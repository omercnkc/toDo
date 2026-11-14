package com.kafein.todo.project.project;

import org.springframework.data.jpa.repository.JpaRepository;

/** Liste/arama sorguları proje uçlarıyla birlikte (3-B) eklenir. */
public interface ProjectRepository extends JpaRepository<Project, Long> {
}
