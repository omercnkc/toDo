package com.kafein.todo.dashboard.summary;

import com.kafein.todo.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard uçları (docs/06 "dashboard-service"). */
@Tag(name = "Dashboard", description = "Panel özeti (project-service + task-service toplaması).")
@RestController
@RequestMapping("/dashboard")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * Kullanıcıya özel özet. {@code scope=mine} (varsayılan) üye olunan projeler, {@code scope=all} tüm sistem
     * (yalnızca ADMIN). En fazla {@code dashboard.cache-ttl} kadar eski olabilir ({@code generatedAt}).
     */
    @Operation(summary = "Panel özeti",
            description = "`scope=mine` (varsayılan) üye olunan projeler; `scope=all` tüm sistem, yalnızca ADMIN. Toplamlar arşivdeki projeleri de kapsar. Kullanıcı + kapsam başına 30 sn önbellek (`generatedAt` hesaplama anı). Kısmi yanıt yok: aşağıdaki servislerden biri kapalıysa 503.")
    @ApiResponse(responseCode = "200", description = "Özet")
    @ApiResponse(responseCode = "403", description = "STATS_SCOPE_FORBIDDEN — `scope=all` yalnızca ADMIN")
    @ApiResponse(responseCode = "503", description = "PROJECT_SERVICE_UNAVAILABLE / TASK_SERVICE_UNAVAILABLE")
    @GetMapping("/summary")
    public DashboardSummary summary(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(required = false) String scope) {
        return summaryService.summary(user, scope);
    }
}
