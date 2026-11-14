package com.kafein.todo.project.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Proje listesi. Parametreler null ise o filtre uygulanmaz.
     *
     * @param memberId null değilse yalnızca bu kullanıcının üyesi olduğu projeler (USER); null → tümü (ADMIN)
     * @param pattern  {@code LikePatterns.contains} ile üretilmiş {@code %metin%} (proje adında aranır)
     */
    @Query("""
            select p from Project p
             where (:memberId is null
                    or exists (select 1 from ProjectMember m where m.project = p and m.userId = :memberId))
               and (:pattern is null or p.name like :pattern escape '!')
               and (:status is null or p.status = :status)""")
    Page<Project> search(@Param("memberId") Long memberId,
                         @Param("pattern") String pattern,
                         @Param("status") ProjectStatus status,
                         Pageable pageable);
}
