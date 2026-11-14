package com.kafein.todo.project.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /** Önce OWNER, sonra katılma sırası. */
    List<ProjectMember> findByProjectIdOrderByRoleDescJoinedAtAsc(Long projectId);
}
