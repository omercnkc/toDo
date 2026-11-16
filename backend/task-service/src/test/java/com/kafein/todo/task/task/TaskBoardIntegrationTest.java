package com.kafein.todo.task.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.kafein.todo.task.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /tasks/board} ve {@code PATCH /tasks/{id}/move} (docs/05 "Sıralama Stratejisi"). */
class TaskBoardIntegrationTest extends IntegrationTestSupport {

    private static final List<String> STATUSES = List.of("TODO", "IN_PROGRESS", "DONE");

    /** Board'u kolon → başlıklar (sıralı) olarak okur; pozisyonların 0..n-1 olduğunu da doğrular. */
    private Map<String, List<String>> board(long projectId) throws Exception {
        JsonNode body = json(perform(get("/tasks/board").param("projectId", String.valueOf(projectId)), member)
                .andExpect(status().isOk()));
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        for (String status : STATUSES) {
            List<String> titles = new ArrayList<>();
            JsonNode column = body.get(status);
            for (int i = 0; i < column.size(); i++) {
                assertThat(column.get(i).get("position").asInt()).as(status + " pozisyonları boşluksuz").isEqualTo(i);
                assertThat(column.get(i).get("status").asText()).isEqualTo(status);
                titles.add(column.get(i).get("title").asText());
            }
            result.put(status, titles);
        }
        return result;
    }

    private void move(long taskId, String status, int position) throws Exception {
        send(patch("/tasks/{id}/move", taskId), member, Map.of("status", status, "position", position))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    /** TODO: A B C D, IN_PROGRESS: E F. Görev id'leri başlık sırasıyla döner. */
    private long[] seed(long projectId) throws Exception {
        long[] ids = new long[6];
        String[] titles = {"A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < titles.length; i++) {
            ids[i] = createTask(projectId, titles[i], member);
        }
        move(ids[4], "IN_PROGRESS", 0);
        move(ids[5], "IN_PROGRESS", 1);
        return ids;
    }

    @Test
    void bosBoarddaUcKolonDaBosListe() throws Exception {
        long projectId = newProject();

        perform(get("/tasks/board").param("projectId", String.valueOf(projectId)), member)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TODO").isEmpty())
                .andExpect(jsonPath("$.IN_PROGRESS").isEmpty())
                .andExpect(jsonPath("$.DONE").isEmpty());
    }

    @Test
    void boardKolonlaraAyrilirVeSiralidir() throws Exception {
        long projectId = newProject();
        seed(projectId);
        createTask(newProject(), "Başka proje", member);

        assertThat(board(projectId)).isEqualTo(Map.of(
                "TODO", List.of("A", "B", "C", "D"),
                "IN_PROGRESS", List.of("E", "F"),
                "DONE", List.of()));
    }

    @Test
    void boardYetkisi() throws Exception {
        long projectId = newProject();
        createTask(projectId, "A", member);

        perform(get("/tasks/board").param("projectId", String.valueOf(projectId)), stranger)
                .andExpect(status().isForbidden());
        perform(get("/tasks/board").param("projectId", String.valueOf(projectId)), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TODO[0].title").value("A"));
        archive(projectId);
        perform(get("/tasks/board").param("projectId", String.valueOf(projectId)), member)
                .andExpect(status().isOk());
        perform(get("/tasks/board"), member).andExpect(status().isBadRequest());
    }

    @Test
    void kolonIcindeAsagiVeYukariTasima() throws Exception {
        long projectId = newProject();
        long[] ids = seed(projectId);

        move(ids[0], "TODO", 2); // A aşağı: B C A D
        assertThat(board(projectId).get("TODO")).containsExactly("B", "C", "A", "D");

        move(ids[3], "TODO", 0); // D en üste: D B C A
        assertThat(board(projectId).get("TODO")).containsExactly("D", "B", "C", "A");

        move(ids[1], "TODO", 1); // yerinde: değişiklik yok
        assertThat(board(projectId).get("TODO")).containsExactly("D", "B", "C", "A");
    }

    @Test
    void kolonlarArasiTasimaHedefteYerAcarKaynakBoslukKapanir() throws Exception {
        long projectId = newProject();
        long[] ids = seed(projectId);

        move(ids[1], "IN_PROGRESS", 1); // B → E ile F arasına
        assertThat(board(projectId)).isEqualTo(Map.of(
                "TODO", List.of("A", "C", "D"),
                "IN_PROGRESS", List.of("E", "B", "F"),
                "DONE", List.of()));

        move(ids[5], "DONE", 0); // F → boş kolona
        move(ids[4], "TODO", 0); // E → TODO en üste
        assertThat(board(projectId)).isEqualTo(Map.of(
                "TODO", List.of("E", "A", "C", "D"),
                "IN_PROGRESS", List.of("B"),
                "DONE", List.of("F")));
    }

    @Test
    void kolonBoyunuAsanSiraSonaSinirlanir() throws Exception {
        long projectId = newProject();
        long[] ids = seed(projectId);

        send(patch("/tasks/{id}/move", ids[0]), member, Map.of("status", "IN_PROGRESS", "position", 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2));
        send(patch("/tasks/{id}/move", ids[1]), member, Map.of("status", "TODO", "position", 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(2));

        assertThat(board(projectId)).isEqualTo(Map.of(
                "TODO", List.of("C", "D", "B"),
                "IN_PROGRESS", List.of("E", "F", "A"),
                "DONE", List.of()));
    }

    @Test
    void gecersizTasimaIstekleri() throws Exception {
        long projectId = newProject();
        long taskId = createTask(projectId, "A", member);

        send(patch("/tasks/{id}/move", taskId), member, Map.of("status", "TODO", "position", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("position")));
        send(patch("/tasks/{id}/move", taskId), member, Map.of("position", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("status")));
        send(patch("/tasks/{id}/move", taskId), stranger, Map.of("status", "DONE", "position", 0))
                .andExpect(status().isForbidden());
        send(patch("/tasks/{id}/move", 999_999_999L), member, Map.of("status", "DONE", "position", 0))
                .andExpect(status().isNotFound());

        archive(projectId);
        send(patch("/tasks/{id}/move", taskId), member, Map.of("status", "DONE", "position", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROJECT_ARCHIVED"));
    }

    @Test
    void esZamanliRastgeleTasimalardaPozisyonlarBozulmaz() throws Exception {
        long projectId = newProject();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(createTask(projectId, "T" + i, member));
        }

        Random random = new Random(42);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long taskId = ids.get(random.nextInt(ids.size()));
            String status = STATUSES.get(random.nextInt(STATUSES.size()));
            int position = random.nextInt(8);
            calls.add(() -> send(patch("/tasks/{id}/move", taskId), member,
                    Map.of("status", status, "position", position)).andReturn().getResponse().getStatus());
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }

        // board() her kolonda 0..n-1 kontrolünü yapar; hiçbir görev kaybolmaz veya çoğalmaz
        Map<String, List<String>> board = board(projectId);
        List<String> all = board.values().stream().flatMap(List::stream).sorted().toList();
        assertThat(all).containsExactlyElementsOf(IntStream.range(0, 10).mapToObj(i -> "T" + i).sorted().toList());
    }
}
