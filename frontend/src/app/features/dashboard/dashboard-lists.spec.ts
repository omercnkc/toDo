import { addDays, assignedToMeList, upcomingList } from './dashboard-lists';

describe('dashboard-lists', () => {
  it('addDays: ay ve yıl sınırları, artık yıl', () => {
    expect(addDays('2026-11-17', 6)).toBe('2026-11-23');
    expect(addDays('2026-11-28', 6)).toBe('2026-12-04');
    expect(addDays('2026-12-29', 6)).toBe('2027-01-04');
    expect(addDays('2028-02-27', 2)).toBe('2028-02-29');
    expect(addDays('2026-03-28', 3)).withContext('yaz saati geçişi').toBe('2026-03-31');
  });

  it('bana atanan: açık görevler, bitiş tarihine göre; bağlantı "me" ile (paylaşılabilir)', () => {
    const list = assignedToMeList(42);
    expect(list.api).toEqual({
      assigneeId: 42, status: ['TODO', 'IN_PROGRESS'], sort: [{ field: 'dueDate', direction: 'asc' }], size: 5,
    });
    expect(list.link).toEqual({ assignee: 'me', status: 'TODO,IN_PROGRESS', sort: 'dueDate,asc' });
  });

  it('yaklaşan: bugün dahil 7 gün, gecikmişler hariç; bağlantı aynı aralık', () => {
    const list = upcomingList('2026-11-17');
    expect(list.api.dueFrom).toBe('2026-11-17');
    expect(list.api.dueTo).toBe('2026-11-23');
    expect(list.api.status).toEqual(['TODO', 'IN_PROGRESS']);
    expect(list.api.assigneeId).toBeUndefined();
    expect(list.link).toEqual({ status: 'TODO,IN_PROGRESS', dueFrom: '2026-11-17', dueTo: '2026-11-23', sort: 'dueDate,asc' });
  });
});
