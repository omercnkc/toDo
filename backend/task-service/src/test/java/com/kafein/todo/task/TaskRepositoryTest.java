package com.kafein.todo.task;

import com.kafein.todo.task.task.Task;
import com.kafein.todo.task.task.TaskPriority;
import com.kafein.todo.task.task.TaskRepository;
import com.kafein.todo.task.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway şeması + entity eşlemesi gerçek MySQL üzerinde (CHECK kısıtları, kod olarak saklanan enum'lar,
 * optimistic lock). Her test kendi transaction'ında çalışır ve sonunda geri alınır.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TaskRepositoryTest {

    private static final long REPORTER = 10L;
    private static final long ALICE = 20L;

    @Autowired
    private TaskRepository tasks;
    @Autowired
    private TestEntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    /** Testler aynı veritabanını paylaşır; her test kendi proje id'siyle çalışır. */
    private static long nextProjectId = System.nanoTime() % 1_000_000_000L;

    private static long newProjectId() {
        return ++nextProjectId;
    }

    private Task newTask(long projectId, String title, int position) {
        return tasks.saveAndFlush(new Task(projectId, title, null, TaskPriority.MEDIUM, null, REPORTER, null, position));
    }

    @Test
    void gorevKaydedilirEnumlarKodOlarakSaklanir() {
        long projectId = newProjectId();
        Task saved = tasks.saveAndFlush(new Task(projectId, "  Giriş ekranı ", "   ", TaskPriority.HIGH, ALICE,
                REPORTER, LocalDate.of(2026, 11, 20), 0));
        em.clear();

        Task found = tasks.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Giriş ekranı");
        assertThat(found.getDescription()).as("boş açıklama NULL saklanır").isNull();
        assertThat(found.getStatus()).as("yeni görev TODO'da başlar").isEqualTo(TaskStatus.TODO);
        assertThat(found.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(found.getAssigneeId()).isEqualTo(ALICE);
        assertThat(found.getReporterId()).isEqualTo(REPORTER);
        assertThat(found.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();

        Map<String, Object> row = jdbc.queryForMap("SELECT status, priority FROM tasks WHERE id = ?", saved.getId());
        assertThat(((Number) row.get("status")).intValue()).isZero();
        assertThat(((Number) row.get("priority")).intValue()).isEqualTo(2);

        found.placeAt(TaskStatus.DONE, 3);
        tasks.saveAndFlush(found);
        assertThat(jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", Integer.class, saved.getId()))
                .isEqualTo(2);
    }

    @Test
    void uzunAciklamaTextKolonundaSaklanir() {
        String longText = "ğ".repeat(10_000);
        Task saved = tasks.saveAndFlush(new Task(newProjectId(), "Büyük", longText, TaskPriority.LOW, null,
                REPORTER, null, 0));
        em.clear();

        assertThat(tasks.findById(saved.getId()).orElseThrow().getDescription()).isEqualTo(longText);
    }

    @Test
    void gecersizKodlarVeNegatifPozisyonVeritabanindaReddedilir() {
        Task task = newTask(newProjectId(), "Görev", 0);

        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET status = 3 WHERE id = ?", task.getId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET priority = 3 WHERE id = ?", task.getId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tasks SET position = -1 WHERE id = ?", task.getId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> task.placeAt(TaskStatus.TODO, -1))
                .as("entity de negatif pozisyonu kabul etmez")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boardSirasiKolonVeKolonIciPozisyonaGoredir() {
        long projectId = newProjectId();
        newTask(projectId, "todo-1", 1);
        newTask(projectId, "todo-0", 0);
        Task done0 = newTask(projectId, "done-0", 0);
        done0.placeAt(TaskStatus.DONE, 0);
        Task progress0 = newTask(projectId, "progress-0", 2);
        progress0.placeAt(TaskStatus.IN_PROGRESS, 0);
        tasks.saveAllAndFlush(List.of(done0, progress0));
        newTask(newProjectId(), "başka proje", 0);
        em.clear();

        assertThat(tasks.findByProjectIdOrderByStatusAscPositionAsc(projectId))
                .extracting(Task::getTitle)
                .containsExactly("todo-0", "todo-1", "progress-0", "done-0");
    }

    @Test
    void eskiSurumUzerindenYapilanDegisiklikReddedilir() {
        Task task = newTask(newProjectId(), "Görev", 0);
        em.clear();
        Task stale = tasks.findById(task.getId()).orElseThrow();
        long version = stale.getVersion();

        // Bu arada başka bir istek görevi değiştirmiş olsun
        jdbc.update("UPDATE tasks SET title = 'Başkası değiştirdi', version = version + 1 WHERE id = ?", task.getId());
        stale.update("Benim değişikliğim", null, TaskPriority.LOW, null, null);

        assertThatThrownBy(() -> tasks.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(version).isZero();
    }

    @Test
    void kolondakiEnBuyukPozisyonBossaEksiBir() {
        long projectId = newProjectId();
        assertThat(tasks.findMaxPosition(projectId, TaskStatus.TODO)).isEqualTo(-1);

        newTask(projectId, "a", 0);
        newTask(projectId, "b", 1);

        assertThat(tasks.findMaxPosition(projectId, TaskStatus.TODO)).isEqualTo(1);
        assertThat(tasks.findMaxPosition(projectId, TaskStatus.DONE)).isEqualTo(-1);
    }

    @Test
    void gecikmeDurumuTamamlanmamisVeTarihiGecmisGorevlerdir() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        Task task = new Task(1L, "Görev", null, TaskPriority.MEDIUM, null, REPORTER, today.minusDays(1), 0);

        assertThat(task.isOverdue(today)).isTrue();
        task.placeAt(TaskStatus.DONE, 0);
        assertThat(task.isOverdue(today)).as("tamamlanan görev gecikmiş sayılmaz").isFalse();
        task.update("Görev", null, TaskPriority.MEDIUM, null, today);
        task.placeAt(TaskStatus.TODO, 0);
        assertThat(task.isOverdue(today)).as("bugün biten görev henüz gecikmiş değil").isFalse();
        task.update("Görev", null, TaskPriority.MEDIUM, null, null);
        assertThat(task.isOverdue(today)).isFalse();
    }
}
