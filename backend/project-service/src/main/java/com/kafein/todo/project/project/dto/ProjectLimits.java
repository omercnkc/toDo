package com.kafein.todo.project.project.dto;

/** Alan sınırları (frontend ile aynı tutulur). */
public final class ProjectLimits {

    /** TEXT kolonu 65 535 bayt; 5000 karakter en kötü durumda (4 bayt/karakter) 20 000 bayt. */
    public static final int DESCRIPTION_MAX = 5000;

    private ProjectLimits() {
    }
}
