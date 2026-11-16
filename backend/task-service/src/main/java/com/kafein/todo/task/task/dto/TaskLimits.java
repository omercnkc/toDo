package com.kafein.todo.task.task.dto;

/** Alan sınırları (frontend ile aynı tutulur). */
public final class TaskLimits {

    /** {@code tasks.title VARCHAR(200)}. */
    public static final int TITLE_MAX = 200;

    /** TEXT kolonu 65 535 bayt; 5000 karakter en kötü durumda (4 bayt/karakter) 20 000 bayt. */
    public static final int DESCRIPTION_MAX = 5000;

    private TaskLimits() {
    }
}
