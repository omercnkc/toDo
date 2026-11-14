package com.kafein.todo.project.client;

import com.kafein.todo.common.error.ApiException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * auth-service'teki kullanıcı bilgisine tek giriş noktası. Feign hatalarını anlamlı sonuçlara çevirir:
 * <ul>
 *   <li>kullanıcı yok (404) → {@link Optional#empty()}</li>
 *   <li>auth-service'e ulaşılamıyor / 5xx / zaman aşımı → 503 {@code USER_SERVICE_UNAVAILABLE}</li>
 * </ul>
 */
@Service
public class UserDirectory {

    /** auth-service {@code LookupRequest} sınırı. */
    static final int LOOKUP_BATCH_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(UserDirectory.class);

    private final AuthClient authClient;

    public UserDirectory(AuthClient authClient) {
        this.authClient = authClient;
    }

    public Optional<UserSummary> find(long userId) {
        try {
            return Optional.of(authClient.get(userId));
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        } catch (FeignException e) {
            throw unavailable(e);
        }
    }

    /** id → özet. Bulunamayan id'ler haritada yer almaz. 200'den fazla id parçalara bölünür. */
    public Map<Long, UserSummary> lookup(Collection<Long> userIds) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(userIds));
        Map<Long, UserSummary> result = new HashMap<>();
        try {
            for (int from = 0; from < ids.size(); from += LOOKUP_BATCH_SIZE) {
                List<Long> batch = ids.subList(from, Math.min(from + LOOKUP_BATCH_SIZE, ids.size()));
                for (UserSummary user : authClient.lookup(new AuthClient.LookupRequest(batch))) {
                    result.put(user.id(), user);
                }
            }
        } catch (FeignException e) {
            throw unavailable(e);
        }
        return result;
    }

    private static ApiException unavailable(FeignException e) {
        log.warn("auth-service çağrısı başarısız (HTTP {}): {}", e.status(), e.getMessage());
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                "Kullanıcı bilgilerine şu an ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.");
    }
}
