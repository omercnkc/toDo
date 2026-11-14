package com.kafein.todo.project.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /** Önce OWNER, sonra katılma sırası. */
    List<ProjectMember> findByProjectIdOrderByRoleDescJoinedAtAsc(Long projectId);

    /** Liste sayfasındaki projelerde kullanıcının rolü (tek sorgu; N+1 yok). */
    List<ProjectMember> findByUserIdAndProjectIdIn(Long userId, Collection<Long> projectIds);

    long countByProjectId(Long projectId);

    /** Kullanıcının üyesi olduğu proje id'leri (dahili uç; user_id indeksi). */
    @Query("select m.project.id from ProjectMember m where m.userId = :userId order by m.project.id")
    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

    /** Liste sayfasındaki projelerin üye sayıları (tek sorgu). */
    @Query("""
            select m.project.id as projectId, count(m) as memberCount
              from ProjectMember m
             where m.project.id in :projectIds
             group by m.project.id""")
    List<MemberCount> countByProjectIds(@Param("projectIds") Collection<Long> projectIds);

    interface MemberCount {
        Long getProjectId();

        long getMemberCount();
    }
}
