package com.kafein.todo.gateway.security;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway'de oluşan (servise ulaşamama, zaman aşımı, eşleşmeyen yol…) hataları da servislerle aynı
 * {@link com.kafein.todo.common.error.ApiError} biçiminde döndürür. Spring'in varsayılan işleyicisi
 * ({@code @Order(-1)}) farklı bir gövde yazıyordu ve servis yeni kapandığında (Eureka kaydı henüz düşmemişken
 * bağlantı reddi) 503 yerine 500 dönüyordu; arayüz bunu "beklenmeyen hata" sanıyordu.
 * <ul>
 *   <li>Servis örneği yok / bağlantı reddedildi / adı çözülemiyor (Docker'da durdurulmuş konteyner) / ağ yolu yok
 *       → 503 {@code SERVICE_UNAVAILABLE}</li>
 *   <li>Zaman aşımı → 504 {@code GATEWAY_TIMEOUT}</li>
 *   <li>Diğer {@link ResponseStatusException}'lar (404, 405…) → kendi durumu, durum adıyla</li>
 *   <li>Beklenmeyen → 500 {@code INTERNAL_ERROR}; ayrıntı yalnızca loga yazılır, gövdeye sızmaz.</li>
 * </ul>
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ErrorResponseWriter writer;

    public GatewayErrorHandler(ErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        if (isUnreachable(ex) || isStatus(ex, HttpStatus.SERVICE_UNAVAILABLE)) {
            log.warn("Servise ulaşılamadı: {} {} ({})", exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(), rootMessage(ex));
            return writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Servis şu an kullanılamıyor");
        }
        if (hasCause(ex, TimeoutException.class) || isStatus(ex, HttpStatus.GATEWAY_TIMEOUT)) {
            log.warn("Servis zaman aşımı: {} {}", exchange.getRequest().getMethod(), exchange.getRequest().getPath());
            return writer.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", "Servis zamanında yanıt vermedi");
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status != null && !status.is5xxServerError()) {
                return writer.write(exchange, status, status.name(), status.getReasonPhrase());
            }
        }
        log.error("Gateway'de beklenmeyen hata: {} {}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(), ex);
        return writer.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Beklenmeyen bir hata oluştu");
    }

    private static boolean isUnreachable(Throwable ex) {
        return hasCause(ex, ConnectException.class) || hasCause(ex, UnknownHostException.class)
                || hasCause(ex, NoRouteToHostException.class);
    }

    private static boolean isStatus(Throwable ex, HttpStatusCode status) {
        return ex instanceof ResponseStatusException rse && rse.getStatusCode().value() == status.value();
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }
}
