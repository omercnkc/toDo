package com.kafein.todo.project.member;

import com.kafein.todo.project.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/** Proje üyeliği. Aynı kullanıcı bir projede bir kez bulunur (UNIQUE project_id, user_id). */
@Entity
@Table(name = "project_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA için
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** auth_db.users.id (mantıksal referans). */
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Convert(converter = MemberRole.JpaConverter.class)
    @Column(name = "member_role", nullable = false)
    private MemberRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    private ProjectMember(Project project, long userId, MemberRole role) {
        this.project = Objects.requireNonNull(project, "project");
        this.userId = userId;
        this.role = Objects.requireNonNull(role, "role");
    }

    /** Projeyi oluşturan kişi. */
    public static ProjectMember owner(Project project, long userId) {
        return new ProjectMember(project, userId, MemberRole.OWNER);
    }

    /** Daveti kabul eden kişi. */
    public static ProjectMember member(Project project, long userId) {
        return new ProjectMember(project, userId, MemberRole.MEMBER);
    }

    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }
}
