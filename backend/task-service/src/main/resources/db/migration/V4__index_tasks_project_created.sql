-- Faz 5-B: görev listesinin varsayılan sıralaması (en yeniler önce) için indeks.
-- Liste sorgusu: WHERE project_id = ? [filtreler] ORDER BY created_at DESC, id DESC LIMIT n.
-- InnoDB'de ikincil indeks birincil anahtarı (id) da içerdiği için (project_id, created_at) indeksi bu sırayı
-- olduğu gibi verir: tersten okunur, sıralama (filesort) gerekmez, ilk sayfa ilk 20 satırda biter.
-- Ölçüm (100 bin görevli proje, docs/05 "Liste sorgusu ölçümleri"): ilk sayfa 156 ms → ~1 ms.
-- Diğer filtreler mevcut indeksleri kullanır: (project_id, status, position), (assignee_id, status), (due_date),
-- (project_id, priority).

CREATE INDEX idx_tasks_project_created ON tasks (project_id, created_at);
