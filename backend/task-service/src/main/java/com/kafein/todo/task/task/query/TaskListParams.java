package com.kafein.todo.task.task.query;

import java.util.List;

/**
 * {@code GET /tasks} filtre ve sıralama parametreleri, istekte geldiği haliyle (docs/06 "Liste filtreleri").
 * <p>
 * Bilerek metin olarak bağlanır: Spring'in tür dönüştürmesi geçersiz bir değerde alan adı ve Türkçe mesaj
 * vermeden genel bir 400 döndürür. Doğrulama ve dönüştürmeyi {@link TaskFilter#parse} yapar ve tüm hataları
 * {@code fieldErrors} olarak birlikte bildirir.
 * <p>
 * Çok değerli parametreler hem tekrarlı ({@code status=TODO&status=DONE}) hem virgüllü ({@code status=TODO,DONE})
 * verilebilir. {@code sort} {@code alan,yön} çiftleridir ({@code sort=dueDate,asc&sort=title}); Spring tek değeri
 * virgülden böldüğü için ayrıştırıcı değerleri parçalara ayırıp sırayla okur (alan, ardından isteğe bağlı yön).
 */
public record TaskListParams(List<String> status, List<String> priority, String assigneeId, String q,
                             String dueFrom, String dueTo, String overdue, List<String> sort) {
}
