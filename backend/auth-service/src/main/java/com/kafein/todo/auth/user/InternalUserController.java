package com.kafein.todo.auth.user;

import com.kafein.todo.auth.user.dto.InternalUserResponse;
import com.kafein.todo.auth.user.dto.LookupRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Servisler arası uçlar (Feign, docs/04 "AuthClient"). Gateway {@code /internal/**} yolunu route etmez;
 * dışarıdan erişilemez. Çağıran servis kullanıcının token'ını iletir, bu yüzden herhangi bir
 * geçerli token yeterlidir (rol kontrolü yok).
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /** Kullanıcı var mı / aktif mi (davet gönderirken). Yoksa 404. */
    @GetMapping("/{id}")
    public InternalUserResponse get(@PathVariable long id) {
        return userService.getInternal(id);
    }

    /** id listesi → özetler (üye/davet listelerinde ad/e-posta). Bulunamayanlar sonuçta yer almaz. */
    @PostMapping("/lookup")
    public List<InternalUserResponse> lookup(@Valid @RequestBody LookupRequest request) {
        return userService.lookup(request.ids());
    }
}
