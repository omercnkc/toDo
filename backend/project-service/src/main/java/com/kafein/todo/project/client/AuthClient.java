package com.kafein.todo.project.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * auth-service'in servisler arası uçları (docs/04 "AuthClient"). Adres Eureka'dan ({@code lb://auth-service});
 * kullanıcının token'ı {@link FeignAuthForwardingInterceptor} ile iletilir.
 * <p>
 * Doğrudan kullanılmaz: hata dönüşümü ve 200'lük parçalama {@link UserDirectory}'de.
 */
@FeignClient(name = "auth-service", path = "/internal/users")
public interface AuthClient {

    /** Yoksa 404 ({@code FeignException.NotFound}). */
    @GetMapping("/{id}")
    UserSummary get(@PathVariable("id") long id);

    /** En fazla 200 id; bulunamayanlar sonuçta yer almaz. */
    @PostMapping("/lookup")
    List<UserSummary> lookup(@RequestBody LookupRequest request);

    record LookupRequest(List<Long> ids) {
    }
}
