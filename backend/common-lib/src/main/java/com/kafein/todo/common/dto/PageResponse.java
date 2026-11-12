package com.kafein.todo.common.dto;

import java.util.List;

/**
 * Sayfalı liste yanıtı. Spring Data {@code Page} tipine bağımlı değildir;
 * servisler kendi tarafında dönüştürür.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
