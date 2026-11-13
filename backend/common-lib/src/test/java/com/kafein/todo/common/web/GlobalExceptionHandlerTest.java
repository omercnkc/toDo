package com.kafein.todo.common.web;

import com.kafein.todo.common.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    record Istek(@NotBlank(message = "boş olamaz") String ad,
                 @Size(min = 3, message = "en az 3 karakter") String kod) {
    }

    @RestController
    static class DenemeController {

        @PostMapping("/dogrula")
        void dogrula(@Valid @RequestBody Istek istek) {
        }

        @GetMapping("/cakisma")
        void cakisma() {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "Bu e-posta zaten kayıtlı");
        }

        @GetMapping("/yetkisiz")
        void yetkisiz() {
            throw new AccessDeniedException("iç detay");
        }

        @GetMapping("/patla")
        void patla() {
            throw new IllegalStateException("SELECT * FROM users -- gizli iç detay");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DenemeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void dogrulamaHatasiAlanBazindaDoner() throws Exception {
        mvc.perform(post("/dogrula").contentType(MediaType.APPLICATION_JSON).content("{\"ad\":\"\",\"kod\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/dogrula"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("ad", "kod")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'ad')].message").value("boş olamaz"));
    }

    @Test
    void apiExceptionKendiDurumKoduylaDoner() throws Exception {
        mvc.perform(get("/cakisma"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("Bu e-posta zaten kayıtlı"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void bozukJson400Doner() throws Exception {
        mvc.perform(post("/dogrula").contentType(MediaType.APPLICATION_JSON).content("{bozuk"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void yetkiReddi500DegilDe403Doner() throws Exception {
        mvc.perform(get("/yetkisiz"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("iç detay"))));
    }

    @Test
    void beklenmeyenHataIcDetaySizdirmaz() throws Exception {
        mvc.perform(get("/patla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Beklenmeyen bir hata oluştu"))
                .andExpect(content().string(not(containsString("SELECT"))));
    }

    @Test
    void desteklenmeyenMetot405Doner() throws Exception {
        mvc.perform(put("/cakisma"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void desteklenmeyenIcerikTuru415Doner() throws Exception {
        mvc.perform(post("/dogrula").contentType(MediaType.TEXT_PLAIN).content("ad=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
