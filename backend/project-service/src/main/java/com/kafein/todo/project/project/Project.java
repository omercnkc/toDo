package com.kafein.todo.project.project;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Proje. Üyeler ve davetler ayrı entity'lerde ({@code project_id} FK, {@code ON DELETE CASCADE});
 * proje üzerinden koleksiyon olarak yüklenmez — listeler kendi sorgularıyla, sayfalı gelir.
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Oluşturan kullanıcı (auth_db.users.id). OWNER üyelik satırı da ayrıca tutulur. */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Convert(converter = ProjectStatus.JpaConverter.class)
    @Column(nullable = false)
    private ProjectStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Project(String name, String description, long ownerId) {
        this.name = requireName(name);
        this.description = normalizeDescription(description);
        this.ownerId = ownerId;
        this.status = ProjectStatus.ACTIVE;
    }

    public void update(String name, String description) {
        this.name = requireName(name);
        this.description = normalizeDescription(description);
    }

    public void changeStatus(ProjectStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    private static String requireName(String name) {
        return Objects.requireNonNull(name, "name").trim();
    }

    /** Boş/yalnızca boşluk açıklama NULL saklanır. */
    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
