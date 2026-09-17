package com.example.consensus.web;

import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.reconciliation.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "Manually trigger reconciliation runs")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "ApiKeyAuth")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/run")
    @Operation(
        summary = "Run reconciliation",
        description = "Compares blotter vs custodian trades and surfaces discrepancies as trade breaks. Normally runs on a 60-second schedule; use this endpoint for on-demand runs."
    )
    @ApiResponse(responseCode = "200", description = "Breaks detected and returned")
    public List<TradeBreak> reconcile() {
        return reconciliationService.reconcile();
    }
}
