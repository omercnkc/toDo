-- Faz 5-A: görev araması (q) ve başlığa göre sıralama Türkçe kurallarla çalışsın.
-- utf8mb4_0900_ai_ci'de I = i ve ı ayrı bir harf: "IŞIK" araması "ışık"ı bulmaz. Türkçe collation'da
-- I ↔ ı ve İ ↔ i eşleşir (frontend'in toLocaleLowerCase('tr-TR') davranışıyla aynı); büyük/küçük harf ve
-- diğer aksanlar (é = e) yine duyarsız. Sıralama da Türk alfabesine göre olur (c < ç, ı < i, s < ş).
-- LIKE '%q%' zaten indeks kullanmadığı için kolon collation'ını değiştirmek arama performansını etkilemez.

ALTER TABLE tasks
    MODIFY title       VARCHAR(200) NOT NULL COLLATE utf8mb4_tr_0900_ai_ci,
    MODIFY description TEXT         NULL     COLLATE utf8mb4_tr_0900_ai_ci;
