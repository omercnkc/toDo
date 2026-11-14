package com.kafein.todo.auth.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code email} parametresi {@link User#normalizeEmail(String)} ile normalize edilmiş olmalı. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Ad veya e-postada geçen metne göre arama. {@code pattern} null ise filtre yok.
     * Karşılaştırma collation sayesinde büyük/küçük harf duyarsızdır.
     *
     * @param pattern     {@code LikePatterns.contains} ile üretilmiş {@code %metin%}
     * @param onlyEnabled true ise pasif kullanıcılar dönmez
     */
    // Kaçış karakteri '!': MySQL'de '\' string içinde de kaçış karakteri olduğu için '\' literal'i bozulur
    @Query("""
            select u from User u
             where (:pattern is null
                    or u.email like :pattern escape '!'
                    or u.fullName like :pattern escape '!')
               and (:onlyEnabled = false or u.enabled = true)""")
    Page<User> search(@Param("pattern") String pattern, @Param("onlyEnabled") boolean onlyEnabled, Pageable pageable);

    List<User> findAllByIdInOrderByIdAsc(Collection<Long> ids);
}
