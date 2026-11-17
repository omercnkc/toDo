package com.kafein.todo.task.task.query;

import java.util.Arrays;
import java.util.Optional;

/**
 * Listede sıralanabilen alanlar (beyaz liste: istemci başka bir kolon adı veremez).
 * {@code status} ve {@code priority} kodlarına göre sıralanır; kodlar zaten anlam sırasındadır
 * ({@code TODO < IN_PROGRESS < DONE}, {@code LOW < MEDIUM < HIGH}).
 */
public enum TaskSortField {
    DUE_DATE("dueDate"),
    PRIORITY("priority"),
    CREATED_AT("createdAt"),
    TITLE("title"),
    STATUS("status");

    private final String param;

    TaskSortField(String param) {
        this.param = param;
    }

    /** İstekteki ve entity'deki ad (ikisi aynı). */
    public String param() {
        return param;
    }

    public static Optional<TaskSortField> fromParam(String value) {
        return Arrays.stream(values()).filter(f -> f.param.equals(value)).findFirst();
    }

    static String allowed() {
        return String.join(", ", Arrays.stream(values()).map(TaskSortField::param).toList());
    }
}
