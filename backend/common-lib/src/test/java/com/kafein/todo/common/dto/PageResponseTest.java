package com.kafein.todo.common.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void totalPagesYukariYuvarlanir() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 0, 20, 41);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void sifirBoyuttaTotalPagesSifirdir() {
        assertThat(PageResponse.of(List.of(), 0, 0, 5).totalPages()).isZero();
    }
}
