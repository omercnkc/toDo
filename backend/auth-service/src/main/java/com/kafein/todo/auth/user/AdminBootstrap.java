package com.kafein.todo.auth.user;

import com.kafein.todo.auth.authentication.validation.PasswordPolicyValidator;
import com.kafein.todo.auth.config.AuthProperties;
import com.kafein.todo.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * İlk açılışta varsayılan ADMIN'i oluşturur ({@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD}).
 * <ul>
 *   <li>Bu e-postada kullanıcı zaten varsa hiçbir şey yapılmaz (şifresi/rolü ezilmez).</li>
 *   <li>Değişkenlerden biri boşsa seed atlanır (uyarı loglanır).</li>
 *   <li>Şifre politikaya uymuyorsa servis açılmaz: zayıf ADMIN şifresi sessizce kabul edilmesin.</li>
 * </ul>
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties.Admin admin;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.admin = properties.admin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!admin.isConfigured()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD tanımlı değil; varsayılan ADMIN oluşturulmadı");
            return;
        }
        String email = User.normalizeEmail(admin.email());
        if (users.existsByEmail(email)) {
            log.info("Varsayılan ADMIN zaten var: {}", email);
            return;
        }
        if (!new PasswordPolicyValidator().isValid(admin.password(), null)) {
            throw new IllegalStateException("ADMIN_PASSWORD şifre politikasına uymuyor (en az "
                    + PasswordPolicyValidator.MIN_LENGTH + " karakter, en fazla " + PasswordPolicyValidator.MAX_BYTES
                    + " bayt)");
        }
        try {
            users.saveAndFlush(new User(email, passwordEncoder.encode(admin.password()), "Sistem Yöneticisi",
                    Role.ADMIN));
            log.info("Varsayılan ADMIN oluşturuldu: {}", email);
        } catch (DataIntegrityViolationException e) {
            // Birden fazla örnek aynı anda açıldıysa diğeri oluşturmuştur
            log.info("Varsayılan ADMIN başka bir örnek tarafından oluşturuldu: {}", email);
        }
    }
}
