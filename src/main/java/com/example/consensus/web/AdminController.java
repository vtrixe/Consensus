package com.example.consensus.web;

import com.example.consensus.breakaging.BreakAgingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative operations — requires ADMIN role")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "ApiKeyAuth")
public class AdminController {

    private final BreakAgingService breakAgingService;

    @PostMapping("/escalate")
    @Operation(summary = "Escalate stale breaks", description = "Promotes all OPEN breaks past the aging threshold to INVESTIGATING. Normally triggered on the 30-minute schedule.")
    @ApiResponse(responseCode = "200", description = "Escalation complete")
    @ApiResponse(responseCode = "403", description = "ADMIN role required")
    public SuccessResponse escalate() {
        breakAgingService.escalateStaleBreaks();
        return SuccessResponse.of("Stale break escalation complete");
    }

    @PostMapping("/pending-confirm-sweep")
    @Operation(summary = "Pending-confirm sweep", description = "End-of-day sweep that auto-resolves breaks in PENDING_CONFIRM state. Normally runs at US market close (ET).")
    @ApiResponse(responseCode = "200", description = "Sweep complete")
    public SuccessResponse pendingConfirmSweep() {
        breakAgingService.pendingConfirmSweep();
        return SuccessResponse.of("Pending confirm sweep complete");
    }
}
