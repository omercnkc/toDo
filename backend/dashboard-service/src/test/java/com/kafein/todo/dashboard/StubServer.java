package com.kafein.todo.dashboard;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Testte project-service / task-service yerine geçen küçük HTTP sunucusu (JDK'nın yerleşik sunucusu; ek bağımlılık
 * yok). Feign gerçekten çağırır: token iletimi, JSON çözümü, zaman aşımı uçtan uca sınanır. {@link #stop()} /
 * {@link #start()} aynı portta kapatıp açar (bağlantı reddi testi; diğer testler etkilenmesin).
 */
public final class StubServer implements AutoCloseable {

    /** Gelen istek: yol + sorgu ve Authorization header'ı. */
    public record Received(String pathAndQuery, String authorization) {
    }

    private record Response(int status, String body) {
    }

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Map<String, Response> routes = new ConcurrentHashMap<>();
    private final int port;
    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "[]";
    private volatile long delayMillis;

    public StubServer() {
        server = create(0);
        port = server.getAddress().getPort();
    }

    public String uri() {
        return "http://127.0.0.1:" + port;
    }

    public void respond(int status, String body) {
        this.status = status;
        this.body = body;
        this.delayMillis = 0;
    }

    /** Belirli bir yol (sorgu hariç) için yanıt; tanımlanmamış yollar {@link #respond} yanıtını alır. */
    public void route(String path, int status, String body) {
        routes.put(path, new Response(status, body));
    }

    public void delay(long millis) {
        this.delayMillis = millis;
    }

    public List<Received> received() {
        return received;
    }

    /** Her test başında: kayıtlar silinir, varsayılan yanıt, sunucu açık. */
    public synchronized void reset() {
        received.clear();
        routes.clear();
        respond(200, "[]");
        start();
    }

    /** Bu porta bağlanmak "bağlantı reddedildi" verir. */
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public synchronized void start() {
        if (server == null) {
            server = create(port);
        }
    }

    @Override
    public void close() {
        stop();
    }

    private HttpServer create(int bindPort) {
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", bindPort), 0);
            created.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getRawQuery();
                received.add(new Received(exchange.getRequestURI().getPath() + (query == null ? "" : "?" + query),
                        exchange.getRequestHeaders().getFirst("Authorization")));
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Response response = routes.getOrDefault(exchange.getRequestURI().getPath(), new Response(status, body));
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            created.start();
            return created;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
