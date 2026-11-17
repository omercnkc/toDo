package com.kafein.todo.task.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.kafein.todo.task.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /tasks} filtreleri, sıralama ve doğrulama (Faz 5-A). */
class TaskListFilterIntegrationTest extends IntegrationTestSupport {

    /** Uygulamayla aynı "bugün" (task.time-zone varsayılanı). Testler sınır günlere denk gelmeyen tarihler kullanır. */
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Istanbul"));

    private long projectId;

    /** Doğrudan veritabanına görev yazar (API'den hızlı; liste testinde Kanban pozisyonunun önemi yok). */
    private Task seed(String title, String description, TaskStatus status, TaskPriority priority, Long assigneeId,
                      LocalDate dueDate) {
        Task task = new Task(projectId, title, description, priority, assigneeId, owner.id(), dueDate, 0);
        task.placeAt(status, 0);
        return tasks.saveAndFlush(task);
    }

    private Task seed(String title, TaskPriority priority, LocalDate dueDate) {
        return seed(title, null, TaskStatus.TODO, priority, null, dueDate);
    }

    private MockHttpServletRequestBuilder list(String... params) {
        MockHttpServletRequestBuilder request = get("/tasks").param("projectId", String.valueOf(projectId))
                .param("size", "100");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return request;
    }

    private List<String> titles(String... params) throws Exception {
        JsonNode body = json(perform(list(params), member).andExpect(status().isOk()));
        List<String> result = new ArrayList<>();
        body.get("content").forEach(node -> result.add(node.get("title").asText()));
        assertThat(body.get("totalElements").asLong()).isEqualTo(result.size());
        return result;
    }

    // ---------- tek tek filtreler ----------

    @Test
    void durumVeOncelikCokDegerliVeBirbirineVeIleBaglanir() throws Exception {
        projectId = newProject();
        seed("A", null, TaskStatus.TODO, TaskPriority.HIGH, null, null);
        seed("B", null, TaskStatus.IN_PROGRESS, TaskPriority.HIGH, null, null);
        seed("C", null, TaskStatus.DONE, TaskPriority.HIGH, null, null);
        seed("D", null, TaskStatus.TODO, TaskPriority.LOW, null, null);

        assertThat(titles("status", "TODO")).containsExactlyInAnyOrder("A", "D");
        // virgüllü ve tekrarlı yazım aynı; küçük harf de kabul
        assertThat(titles("status", "todo,IN_PROGRESS")).containsExactlyInAnyOrder("A", "B", "D");
        perform(list().param("status", "TODO").param("status", "DONE"), member)
                .andExpect(jsonPath("$.totalElements").value(3));
        assertThat(titles("priority", "LOW,MEDIUM")).containsExactly("D");
        assertThat(titles("status", "TODO", "priority", "HIGH")).containsExactly("A");
        assertThat(titles("status", "DONE", "priority", "LOW")).isEmpty();
    }

    @Test
    void atananKisiVeAtanmamislar() throws Exception {
        projectId = newProject();
        seed("Üyeye", null, TaskStatus.TODO, TaskPriority.MEDIUM, member.id(), null);
        seed("Diğer üyeye", null, TaskStatus.TODO, TaskPriority.MEDIUM, member2.id(), null);
        seed("Kimseye", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);

        assertThat(titles("assigneeId", String.valueOf(member.id()))).containsExactly("Üyeye");
        assertThat(titles("assigneeId", "none")).containsExactly("Kimseye");
        assertThat(titles("assigneeId", "NONE")).containsExactly("Kimseye");
        assertThat(titles("assigneeId", "")).hasSize(3);
    }

    @Test
    void aramaBaslikVeAciklamadaTurkceKurallarlaVeJokersiz() throws Exception {
        projectId = newProject();
        seed("İstanbul toplantısı", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);
        seed("Rapor", "Işık ayarlarını kontrol et", TaskStatus.TODO, TaskPriority.MEDIUM, null, null);
        seed("İndirim %50", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);
        seed("Kafe_menü", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);
        seed("Résumé güncelle", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);

        assertThat(titles("q", "istanbul")).containsExactly("İstanbul toplantısı");
        assertThat(titles("q", "TOPLANTISI")).containsExactly("İstanbul toplantısı");
        assertThat(titles("q", "  ışık  ")).as("açıklamada; baştaki/sondaki boşluk yok sayılır").containsExactly("Rapor");
        assertThat(titles("q", "IŞIK")).as("Türkçe: I ↔ ı").containsExactly("Rapor");
        assertThat(titles("q", "resume")).as("aksan duyarsız").containsExactly("Résumé güncelle");
        // % ve _ joker değil, düz karakter
        assertThat(titles("q", "%")).containsExactly("İndirim %50");
        assertThat(titles("q", "_")).containsExactly("Kafe_menü");
        assertThat(titles("q", "e_m")).containsExactly("Kafe_menü");
        assertThat(titles("q", "!")).isEmpty();
        assertThat(titles("q", "   ")).as("boş arama filtre değil").hasSize(5);
    }

    @Test
    void bitisTarihiAraligiDahilVeTarihsizlerElenir() throws Exception {
        projectId = newProject();
        LocalDate base = TODAY.plusDays(30);
        seed("Önce", TaskPriority.MEDIUM, base.minusDays(1));
        seed("Başlangıç", TaskPriority.MEDIUM, base);
        seed("Bitiş", TaskPriority.MEDIUM, base.plusDays(2));
        seed("Sonra", TaskPriority.MEDIUM, base.plusDays(3));
        seed("Tarihsiz", TaskPriority.MEDIUM, null);

        assertThat(titles("dueFrom", base.toString(), "dueTo", base.plusDays(2).toString()))
                .containsExactlyInAnyOrder("Başlangıç", "Bitiş");
        assertThat(titles("dueFrom", base.plusDays(2).toString())).containsExactlyInAnyOrder("Bitiş", "Sonra");
        assertThat(titles("dueTo", base.minusDays(1).toString())).containsExactly("Önce");
        assertThat(titles("dueFrom", base.toString(), "dueTo", base.toString())).containsExactly("Başlangıç");
    }

    @Test
    void gecikmislerTamamlanmamisVeBitisTarihiBugundenOnceOlanlar() throws Exception {
        projectId = newProject();
        seed("Geçmiş açık", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, TODAY.minusDays(3));
        seed("Geçmiş sürüyor", null, TaskStatus.IN_PROGRESS, TaskPriority.MEDIUM, null, TODAY.minusDays(1));
        seed("Geçmiş bitti", null, TaskStatus.DONE, TaskPriority.MEDIUM, null, TODAY.minusDays(3));
        seed("Bugün", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, TODAY);
        seed("Gelecek", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, TODAY.plusDays(3));
        seed("Tarihsiz", null, TaskStatus.TODO, TaskPriority.MEDIUM, null, null);

        assertThat(titles("overdue", "true")).containsExactlyInAnyOrder("Geçmiş açık", "Geçmiş sürüyor");
        assertThat(titles("overdue", "false")).as("false = filtre yok").hasSize(6);
        assertThat(titles("overdue", "true", "status", "IN_PROGRESS")).containsExactly("Geçmiş sürüyor");
        assertThat(titles("overdue", "true", "status", "DONE")).isEmpty();
    }

    // ---------- sıralama ----------

    @Test
    void varsayilanSiralamaEnYenilerOnce() throws Exception {
        projectId = newProject();
        seed("Bir", TaskPriority.MEDIUM, null);
        seed("İki", TaskPriority.MEDIUM, null);
        seed("Üç", TaskPriority.MEDIUM, null);
        assertThat(titles()).containsExactly("Üç", "İki", "Bir");
    }

    @Test
    void bitisTarihineGoreSiralamadaTarihsizlerIkiYondeDeSonda() throws Exception {
        projectId = newProject();
        seed("Tarihsiz", TaskPriority.MEDIUM, null);
        seed("Geç", TaskPriority.MEDIUM, TODAY.plusDays(20));
        seed("Erken", TaskPriority.MEDIUM, TODAY.plusDays(10));
        seed("Orta", TaskPriority.MEDIUM, TODAY.plusDays(15));

        assertThat(titles("sort", "dueDate,asc")).containsExactly("Erken", "Orta", "Geç", "Tarihsiz");
        assertThat(titles("sort", "dueDate")).as("yön yoksa artan").containsExactly("Erken", "Orta", "Geç", "Tarihsiz");
        assertThat(titles("sort", "dueDate,desc")).containsExactly("Geç", "Orta", "Erken", "Tarihsiz");
    }

    @Test
    void cokAlanliSiralamaVeTurkceAlfabe() throws Exception {
        projectId = newProject();
        seed("şeftali", TaskPriority.LOW, null);
        seed("çilek", TaskPriority.HIGH, null);
        seed("Armut", TaskPriority.HIGH, null);
        seed("sarımsak", TaskPriority.LOW, null);
        seed("cevİz", TaskPriority.MEDIUM, null);

        assertThat(titles("sort", "title,asc")).as("Türk alfabesi: c < ç, s < ş; büyük/küçük harf duyarsız")
                .containsExactly("Armut", "cevİz", "çilek", "sarımsak", "şeftali");
        // tekrarlı (sort=…&sort=…) ve tek değerde virgüllü yazım aynı sonucu verir
        List<String> byPriorityThenTitleDesc = List.of("çilek", "Armut", "cevİz", "şeftali", "sarımsak");
        JsonNode repeated = json(perform(list().param("sort", "priority,desc").param("sort", "title,desc"), member));
        assertThat(repeated.get("content").findValuesAsText("title")).containsExactlyElementsOf(byPriorityThenTitleDesc);
        assertThat(titles("sort", "priority,desc,title,desc")).containsExactlyElementsOf(byPriorityThenTitleDesc);
        assertThat(titles("sort", "priority,desc,title")).containsExactly("Armut", "çilek", "cevİz", "sarımsak", "şeftali");
        assertThat(titles("sort", "status,asc,priority,asc,title,asc"))
                .containsExactly("sarımsak", "şeftali", "cevİz", "Armut", "çilek");
    }

    @Test
    void esitDegerlerdeSayfalamaKararliTekrarVeAtlamaYok() throws Exception {
        projectId = newProject();
        Set<Long> all = new HashSet<>();
        for (int i = 0; i < 7; i++) {
            all.add(seed("Aynı", TaskPriority.HIGH, TODAY.plusDays(5)).getId());
        }
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            JsonNode body = json(perform(get("/tasks").param("projectId", String.valueOf(projectId))
                    .param("sort", "priority,desc").param("sort", "dueDate,asc").param("sort", "title")
                    .param("size", "2").param("page", String.valueOf(page)), member).andExpect(status().isOk()));
            assertThat(body.get("totalElements").asLong()).isEqualTo(7);
            assertThat(body.get("totalPages").asInt()).isEqualTo(4);
            body.get("content").forEach(node -> seen.add(node.get("id").asLong()));
        }
        assertThat(seen).doesNotHaveDuplicates().hasSize(7);
        assertThat(new HashSet<>(seen)).isEqualTo(all);
    }

    // ---------- doğrulama ve yetki ----------

    @Test
    void gecersizParametrelerTekYanittaAlanHatalariyla400() throws Exception {
        projectId = newProject();
        JsonNode body = json(perform(list("status", "TODO,BITTI", "priority", "ACIL", "assigneeId", "abc",
                "dueFrom", "2026-02-30", "dueTo", "dün", "overdue", "evet", "sort", "position,asc",
                "q", "x".repeat(201)), member).andExpect(status().isBadRequest()));
        assertThat(body.get("error").asText()).isEqualTo("VALIDATION_ERROR");
        Map<String, String> errors = new LinkedHashMap<>();
        body.get("fieldErrors").forEach(e -> errors.put(e.get("field").asText(), e.get("message").asText()));
        assertThat(errors).containsOnlyKeys("status", "priority", "assigneeId", "dueFrom", "dueTo", "overdue",
                "sort", "q");
        assertThat(errors.get("status")).contains("BITTI").contains("TODO, IN_PROGRESS, DONE");
        assertThat(errors.get("sort")).contains("position").contains("dueDate");

        assertThat(fieldErrors(list("dueFrom", TODAY.plusDays(2).toString(), "dueTo", TODAY.toString())))
                .containsOnlyKeys("dueTo");
        assertThat(fieldErrors(list("sort", "title,asc", "sort", "title,desc"))).containsOnlyKeys("sort");
        assertThat(fieldErrors(list("sort", "desc"))).containsOnlyKeys("sort");
        assertThat(fieldErrors(list("assigneeId", "0"))).containsOnlyKeys("assigneeId");
        perform(list("q", "x".repeat(200)), member).andExpect(status().isOk());
    }

    private Map<String, String> fieldErrors(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode body = json(perform(request, member).andExpect(status().isBadRequest()));
        Map<String, String> errors = new LinkedHashMap<>();
        body.get("fieldErrors").forEach(e -> errors.put(e.get("field").asText(), e.get("message").asText()));
        return errors;
    }

    @Test
    void filtreBaskaProjeninGorevleriniGetirmezVeUyeOlmayan403() throws Exception {
        projectId = newProject();
        seed("Benim", TaskPriority.HIGH, null);
        long other = projectId;
        projectId = newProject();
        seed("Başkası", TaskPriority.HIGH, null);

        assertThat(titles("priority", "HIGH")).containsExactly("Başkası");
        projectId = other;
        perform(list("priority", "HIGH", "q", "Ben"), stranger).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PROJECT_ACCESS_DENIED"));
        perform(list("priority", "HIGH"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---------- kombinasyonlar ----------

    /**
     * 3 durum × 3 öncelik × 3 atama × 3 bitiş tarihi = 81 görev (tek sayfaya sığar); 300 rastgele filtre kombinasyonunun her biri
     * için API sonucu, aynı kuralların Java'daki karşılığıyla karşılaştırılır.
     */
    @Test
    void rastgeleFiltreKombinasyonlariBeklenenleAyni() throws Exception {
        projectId = newProject();
        Long[] assignees = {null, member.id(), member2.id()};
        LocalDate[] dueDates = {null, TODAY.minusDays(5), TODAY.plusDays(20)};
        String[] words = {"alfa", "beta", "gama"};
        Map<Long, Task> seeded = new LinkedHashMap<>();
        int n = 0;
        for (TaskStatus status : TaskStatus.values()) {
            for (TaskPriority priority : TaskPriority.values()) {
                for (Long assignee : assignees) {
                    for (LocalDate due : dueDates) {
                        String title = "Görev " + n + " " + words[n % 3];
                        String description = n % 4 == 0 ? "not: " + words[(n + 1) % 3] : null;
                        Task task = seed(title, description, status, priority, assignee, due);
                        seeded.put(task.getId(), task);
                        n++;
                    }
                }
            }
        }

        Random random = new Random(20261117);
        for (int round = 0; round < 300; round++) {
            List<String> params = new ArrayList<>();
            List<Predicate<Task>> rules = new ArrayList<>();

            Set<TaskStatus> statuses = randomSubset(random, TaskStatus.values());
            if (!statuses.isEmpty()) {
                params.addAll(List.of("status", join(statuses)));
                rules.add(t -> statuses.contains(t.getStatus()));
            }
            Set<TaskPriority> priorities = randomSubset(random, TaskPriority.values());
            if (!priorities.isEmpty()) {
                params.addAll(List.of("priority", join(priorities)));
                rules.add(t -> priorities.contains(t.getPriority()));
            }
            switch (random.nextInt(4)) {
                case 1 -> {
                    params.addAll(List.of("assigneeId", "none"));
                    rules.add(t -> t.getAssigneeId() == null);
                }
                case 2 -> {
                    params.addAll(List.of("assigneeId", String.valueOf(member.id())));
                    rules.add(t -> Objects.equals(t.getAssigneeId(), member.id()));
                }
                default -> { }
            }
            if (random.nextInt(3) == 0) {
                String word = words[random.nextInt(3)];
                params.addAll(List.of("q", word.toUpperCase()));
                rules.add(t -> t.getTitle().contains(word)
                        || (t.getDescription() != null && t.getDescription().contains(word)));
            }
            if (random.nextInt(3) == 0) {
                LocalDate from = TODAY.plusDays(random.nextInt(50) - 10);
                params.addAll(List.of("dueFrom", from.toString()));
                rules.add(t -> t.getDueDate() != null && !t.getDueDate().isBefore(from));
            }
            if (random.nextInt(3) == 0) {
                LocalDate to = TODAY.plusDays(random.nextInt(50) - 10);
                params.addAll(List.of("dueTo", to.toString()));
                rules.add(t -> t.getDueDate() != null && !t.getDueDate().isAfter(to));
            }
            if (random.nextInt(4) == 0) {
                params.addAll(List.of("overdue", "true"));
                rules.add(t -> t.isOverdue(TODAY));
            }
            String[] array = params.toArray(String[]::new);
            if (hasInvalidRange(params)) {
                continue;
            }

            Set<Long> expected = seeded.values().stream().filter(t -> rules.stream().allMatch(r -> r.test(t)))
                    .map(Task::getId).collect(Collectors.toSet());
            JsonNode body = json(perform(list(array), member).andExpect(status().isOk()));
            Set<Long> actual = new HashSet<>();
            body.get("content").forEach(node -> actual.add(node.get("id").asLong()));
            assertThat(actual).as("filtre: %s", params).isEqualTo(expected);
            assertThat(body.get("totalElements").asLong()).isEqualTo(expected.size());
        }
    }

    private static boolean hasInvalidRange(List<String> params) {
        int from = params.indexOf("dueFrom");
        int to = params.indexOf("dueTo");
        return from >= 0 && to >= 0 && LocalDate.parse(params.get(from + 1)).isAfter(LocalDate.parse(params.get(to + 1)));
    }

    private static <E extends Enum<E>> Set<E> randomSubset(Random random, E[] values) {
        Set<E> result = new HashSet<>();
        for (E value : values) {
            if (random.nextInt(3) == 0) {
                result.add(value);
            }
        }
        return result;
    }

    private static String join(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
