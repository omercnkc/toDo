package com.kafein.todo.common.persistence;

/**
 * Kullanıcı arama metnini JPQL/SQL {@code LIKE} desenine çevirir. Sorguda kaçış karakteri
 * {@code escape '!'} olarak verilmelidir ('\' MySQL string'inde de kaçış karakteri olduğu için kullanılmaz).
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * "içerir" deseni: {@code %metin%}. Metindeki {@code % _ !} joker sayılmaz ("%" araması her şeyi
     * döndürmesin). Metin boşsa null (sorguda {@code :pattern is null} → filtre yok).
     */
    public static String contains(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
