package com.kafein.todo.dashboard.summary;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /dashboard/summary} yanıtı (docs/06 "dashboard-service").
 * <p>
 * Toplamlar kapsamdaki <b>tüm</b> projeleri (arşivdekiler dahil) kapsar: görev listesindeki sayılarla birebir
 * aynı olsun diye. Arşivdeki projeler {@code projects} içinde {@code status=ARCHIVED} ile ayırt edilir.
 *
 * @param scope         {@code mine} veya {@code all}
 * @param projectCount  kapsamdaki proje sayısı ({@code activeProjectCount + archivedProjectCount})
 * @param taskTotals    durum başına görev sayısı
 * @param totalTasks    tüm görevler
 * @param openTasks     {@code DONE} olmayanlar
 * @param overdueCount  bitiş tarihi bugünden önce ve {@code DONE} olmayanlar
 * @param assignedToMe  istekte bulunana atanmış açık görevler (kapsam içinde)
 * @param projects      aktifler önce, sonra ada göre (Türkçe sıralama)
 * @param generatedAt   özetin hesaplandığı an (önbellekten geldiyse eski olabilir; arayüz "x sn önce" gösterebilir)
 */
public record DashboardSummary(String scope, int projectCount, int activeProjectCount, int archivedProjectCount,
                               TaskTotals taskTotals, long totalTasks, long openTasks, long overdueCount,
                               AssignedToMe assignedToMe, List<ProjectProgress> projects, Instant generatedAt) {

    public record TaskTotals(long todo, long inProgress, long done) {
    }

    public record AssignedToMe(long open, long overdue) {
    }

    /**
     * @param myRole          {@code OWNER} / {@code MEMBER}; {@code all} kapsamında (üye olunmayabilir) null
     * @param progressPercent tamamlanan / toplam, aşağı yuvarlanmış (yalnızca hepsi bittiyse 100); görevsiz projede 0
     */
    public record ProjectProgress(long id, String name, String status, String myRole, long total, long todo,
                                  long inProgress, long done, long overdue, int progressPercent) {
    }
}
