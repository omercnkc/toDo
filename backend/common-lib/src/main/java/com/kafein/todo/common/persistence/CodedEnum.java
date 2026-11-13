package com.kafein.todo.common.persistence;

/**
 * Veritabanında {@code TINYINT UNSIGNED} kod olarak saklanan enum'lar (bkz. docs/05-database.md).
 * <p>
 * Kodlar bir kez yazıldıktan sonra değiştirilmez; yeni değerler sona eklenir.
 * {@code @Enumerated(ORDINAL)} kullanılmaz: enum sırası değişirse veri bozulur.
 */
public interface CodedEnum {

    byte getCode();
}
