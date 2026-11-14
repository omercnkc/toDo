package com.kafein.todo.project.client;

import com.kafein.todo.common.error.ApiException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDirectoryTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.GET, "http://auth-service/internal/users/1",
            Map.of(), null, StandardCharsets.UTF_8, null);

    private final AuthClient authClient = mock(AuthClient.class);
    private final UserDirectory directory = new UserDirectory(authClient);

    @Test
    void kullaniciVarsaDonerYoksaBos() {
        UserSummary ali = new UserSummary(1L, "ali@ornek.com", "Ali", true);
        when(authClient.get(1L)).thenReturn(ali);
        when(authClient.get(2L)).thenThrow(new FeignException.NotFound("yok", REQUEST, null, Map.of()));

        assertThat(directory.find(1L)).contains(ali);
        assertThat(directory.find(2L)).isEmpty();
    }

    @Test
    void ulasilamayanAuthServis503Olur() {
        when(authClient.get(1L)).thenThrow(new RetryableException(-1, "Connection refused",
                Request.HttpMethod.GET, (Long) null, REQUEST));
        when(authClient.lookup(any())).thenThrow(
                new FeignException.InternalServerError("hata", REQUEST, null, Map.of()));

        assertThatThrownBy(() -> directory.find(1L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(503);
                    assertThat(e.getError()).isEqualTo("USER_SERVICE_UNAVAILABLE");
                });
        assertThatThrownBy(() -> directory.lookup(List.of(1L)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void lookupTekrarlariAtarVe200lukParcalaraBoler() {
        when(authClient.lookup(any())).thenAnswer(inv -> {
            AuthClient.LookupRequest request = inv.getArgument(0);
            return request.ids().stream().map(id -> new UserSummary(id, id + "@x.com", "U" + id, true)).toList();
        });
        List<Long> ids = LongStream.rangeClosed(1, 450).boxed().toList();
        List<Long> withDuplicates = new java.util.ArrayList<>(ids);
        withDuplicates.addAll(List.of(1L, 2L, 3L));

        Map<Long, UserSummary> result = directory.lookup(withDuplicates);

        assertThat(result).hasSize(450);
        ArgumentCaptor<AuthClient.LookupRequest> captor = ArgumentCaptor.forClass(AuthClient.LookupRequest.class);
        verify(authClient, times(3)).lookup(captor.capture());
        assertThat(captor.getAllValues()).extracting(r -> r.ids().size()).containsExactly(200, 200, 50);
    }
}
