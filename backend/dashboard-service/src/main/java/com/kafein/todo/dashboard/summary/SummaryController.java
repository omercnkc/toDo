package com.kafein.todo.dashboard.summary;

import com.kafein.todo.common.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard uçları (docs/06 "dashboard-service"). */
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
    @GetMapping("/summary")
    public DashboardSummary summary(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(required = false) String scope) {
        return summaryService.summary(user, scope);
    }
}
