package com.kafein.todo.project.project.dto;

import com.kafein.todo.project.project.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Proje düzenleme (PUT: ad ve açıklama tam gönderilir).
 *
 * @param status isteğe bağlı; null → değişmez. Arşivden geri almak için {@code ACTIVE}.
 */
public record UpdateProjectRequest(
        @NotBlank(message = "boş olamaz")
        @Size(max = 150, message = "en fazla 150 karakter olabilir")
        String name,
        @Size(max = ProjectLimits.DESCRIPTION_MAX, message = "en fazla " + ProjectLimits.DESCRIPTION_MAX + " karakter olabilir")
        String description,
        ProjectStatus status) {
}
