# 01 – Gereksinimler

## Fonksiyonel Gereksinimler
1. **Kimlik ve kullanıcı**: kayıt, giriş, çıkış, token yenileme; kullanıcı listeleme/düzenleme/pasifleştirme (ADMIN); profil görüntüleme/düzenleme, şifre değiştirme.
2. **Proje**: oluşturma, düzenleme, silme (veya arşivleme); üye davet etme (davet edilen kabul edince üye olur) ve üye çıkarma.
3. **Görev**: proje içinde oluşturma, düzenleme, silme; kullanıcıya atama; öncelik ve bitiş tarihi.
4. **Durum yönetimi**: `TODO` (Yapılacak), `IN_PROGRESS` (Devam Ediyor), `DONE` (Tamamlandı).
5. **Kanban**: görevler 3 kolonda listelenir; sürükle-bırak ile durum (ve kolon içi sıra) güncellenir.
6. **Arama / filtre / sıralama**: başlık-açıklamada arama; durum, öncelik, atanan kişi, bitiş tarihi filtreleri; öncelik/bitiş tarihi/oluşturma tarihine göre sıralama; sayfalama.
7. **Dashboard**: proje sayısı, duruma göre görev dağılımı, geciken görevler, bana atanan görevler, proje bazlı ilerleme yüzdesi.
8. **Responsive arayüz**: mobil, tablet, masaüstü.

## Kullanıcı Hikayeleri (özet)
- Kullanıcı olarak e-posta/şifre ile giriş yapabilmeliyim ki verilerime erişeyim.
- Kullanıcı olarak proje oluşturup başkalarını davet edebilmeliyim; davet edilen kişi olarak daveti kabul veya reddedebilmeliyim ki istemediğim projeye eklenmeyeyim.
- Üye olarak projedeki görevleri Kanban'da görüp sürükleyerek durumunu değiştirebilmeliyim.
- Üye olarak görevleri arayıp filtreleyebilmeliyim.
- Kullanıcı olarak dashboard'da genel durumu görebilmeliyim.
- Admin olarak tüm kullanıcıları ve projeleri yönetebilmeliyim.

## Fonksiyonel Olmayan Gereksinimler
- Güvenlik: BCrypt, JWT, yetkisiz erişim engeli, girdi doğrulama (Bean Validation).
- Performans: liste uçlarında sayfalama; filtrelenen kolonlarda indeks.
- Tutarlı hata formatı (bkz. 06). Loglama ve correlation id (isteğe bağlı faz 7).
- Tek komutla ayağa kalkan ortam (`docker compose up`).
- Test: servis katmanı birim testleri, kritik uçlar için entegrasyon testleri (Testcontainers MySQL).

## Kapsam Dışı (ilk sürüm)
Dosya ekleri, yorumlar, bildirimler/e-posta, gerçek zamanlı (WebSocket) güncelleme, çok kiracılık, asenkron mesajlaşma, OAuth/SSO.
Bu maddeler sonraki sürüm adayıdır.
