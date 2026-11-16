package com.kafein.todo.task.task;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Görev. {@code projectId}, {@code assigneeId}, {@code reporterId} başka veritabanlarındaki kayıtlara mantıksal
 * referanstır (FK yok); proje üyeliği her yazma işleminde project-service'e sorulur.
 * <p>
 * Kanban konumu = ({@link #status}, {@link #position}). Pozisyonlar kolon içinde 0'dan başlar ve boşluksuz
 * tutulur; kaydırma kuralları servis katmanında (docs/05 "Sıralama Stratejisi").
 */
@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Convert(converter = TaskStatus.JpaConverter.class)
    @Column(nullable = false)
    private TaskStatus status;

    @Convert(converter = TaskPriority.JpaConverter.class)
    @Column(nullable = false)
    private TaskPriority priority;

    @Column(name = "assignee_id")
    private Long assigneeId;

    /** Görevi oluşturan; silme yetkisi (reporter / OWNER / ADMIN) buna bakar. */
    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private int position;

    /** Optimistic lock: eşzamanlı iki düzenlemeden ikincisi {@code ObjectOptimisticLockingFailureException} alır. */
    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Yeni görev her zaman {@link TaskStatus#TODO} kolonunda, verilen sırada başlar. */
    public Task(long projectId, String title, String description, TaskPriority priority, Long assigneeId,
                long reporterId, LocalDate dueDate, int position) {
        this.projectId = projectId;
        this.reporterId = reporterId;
        this.status = TaskStatus.TODO;
        this.position = requirePosition(position);
        update(title, description, priority, assigneeId, dueDate);
    }

    /** Düzenlenebilir alanlar (durum ve sıra hariç: onlar {@link #placeAt} ile değişir). */
    public void update(String title, String description, TaskPriority priority, Long assigneeId, LocalDate dueDate) {
        this.title = Objects.requireNonNull(title, "title").trim();
        this.description = normalizeDescription(description);
        this.priority = Objects.requireNonNull(priority, "priority");
        this.assigneeId = assigneeId;
        this.dueDate = dueDate;
    }

    /** Kanban konumunu değiştirir; diğer görevlerin kaydırılması çağıranın sorumluluğu. */
    public void placeAt(TaskStatus status, int position) {
        this.status = Objects.requireNonNull(status, "status");
        this.position = requirePosition(position);
    }

    public void unassign() {
        this.assigneeId = null;
    }

    public boolean isDone() {
        return status == TaskStatus.DONE;
    }

    /** Bitiş tarihi geçmiş ve henüz tamamlanmamış. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && !isDone() && dueDate.isBefore(today);
    }

    private static int requirePosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("position negatif olamaz: " + position);
        }
        return position;
    }

    /** Boş/yalnızca boşluk açıklama NULL saklanır. */
    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
