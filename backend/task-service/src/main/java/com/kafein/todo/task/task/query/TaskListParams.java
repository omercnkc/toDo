package com.kafein.todo.task.task.query;

import io.swagger.v3.oas.annotations.Parameter;

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
public record TaskListParams(
        @Parameter(description = "TODO, IN_PROGRESS, DONE (virgülle ya da tekrarla birden fazla)") List<String> status,
        @Parameter(description = "LOW, MEDIUM, HIGH (virgülle ya da tekrarla birden fazla)") List<String> priority,
        @Parameter(description = "Atanan kullanıcı id'si ya da `none` (atanmamışlar)") String assigneeId,
        @Parameter(description = "Başlık veya açıklamada geçen metin (en fazla 200 karakter)") String q,
        @Parameter(description = "Bitiş tarihi alt sınırı, dahil (YYYY-AA-GG)") String dueFrom,
        @Parameter(description = "Bitiş tarihi üst sınırı, dahil (YYYY-AA-GG)") String dueTo,
        @Parameter(description = "`true`: bitiş tarihi geçmiş ve DONE olmayanlar") String overdue,
        @Parameter(description = "`alan[,asc|desc]`, tekrarlanabilir; alanlar: dueDate, priority, createdAt, title, "
                + "status. Varsayılan createdAt,desc") List<String> sort) {
}
