package com.kafein.todo.project.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "boş olamaz")
        @Size(max = 150, message = "en fazla 150 karakter olabilir")
        String name,
        @Size(max = ProjectLimits.DESCRIPTION_MAX, message = "en fazla " + ProjectLimits.DESCRIPTION_MAX + " karakter olabilir")
        String description) {
}
