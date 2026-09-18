package com.example.consensus.web;

import com.example.consensus.breakaging.BreakAgingService;
import com.example.consensus.fuzzymatch.FuzzyMatchingService;
import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeBreakAudit;
import com.example.consensus.model.entity.TradeMatchCandidate;
import com.example.consensus.model.repository.TradeBreakAuditRepository;
import com.example.consensus.model.repository.TradeMatchCandidateRepository;
import com.example.consensus.reconciliation.ReconciliationService;
import com.example.consensus.web.DTOs.Requests.UpdateBreakRequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/breaks")
@RequiredArgsConstructor
@Tag(name = "Trade Breaks", description = "Query, manage and investigate trade breaks within the caller's tenant")
@SecurityRequirement(name = "BearerAuth")
@SecurityRequirement(name = "ApiKeyAuth")
public class TradeBreakController {

    private final ReconciliationService reconciliationService;
    private final BreakAgingService breakAgingService;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;
    private final FuzzyMatchingService  fuzzyMatchingService;
    private final TradeMatchCandidateRepository  tradeMatchCandidateRepository;

    @GetMapping
    @Operation(summary = "List trade breaks", description = "Returns all breaks in the tenant, optionally filtered by status.")
    @ApiResponse(responseCode = "200", description = "Break list returned")
    public List<TradeBreak> getBreaks(
            @Parameter(description = "Filter by status: OPEN | RESOLVED | INVESTIGATING | PENDING_CONFIRM | WRITTEN_OFF")
            @RequestParam(required = false) BreakStatus status) {
        return reconciliationService.findBreaks(status);
    }

    @GetMapping("/{tradeId}")
    @Operation(summary = "Get breaks by trade ID")
    @ApiResponse(responseCode = "200", description = "Breaks for the given trade")
    public List<TradeBreak> getBreaksByTradeId(
            @Parameter(description = "Trade ID to look up") @PathVariable String tradeId) {
        return reconciliationService.findBreaksByTradeId(tradeId);
    }

    @PatchMapping("/{id}/status")
    @Operation(
        summary = "Update break status",
        description = "Transitions a break to a new status. `WRITTEN_OFF` requires the `OPS_LEAD` or `ADMIN` role. All transitions are logged to the audit trail."
    )
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "403", description = "WRITTEN_OFF attempted without OPS_LEAD / ADMIN role")
    @ApiResponse(responseCode = "404", description = "Break not found")
    public SuccessResponse updateBreakStatus(
            @Parameter(description = "Break ID") @PathVariable Long id,
            @RequestBody UpdateBreakRequestBody requestBody) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String changedBy = auth.getName();

        if (com.example.consensus.model.Enums.BreakStatus.WRITTEN_OFF.equals(requestBody.getNewStatus())) {
            boolean authorized = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_OPS_LEAD") || a.getAuthority().equals("ROLE_ADMIN"));
            if (!authorized) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WRITTEN_OFF requires OPS_LEAD or ADMIN role");
            }
        }

        breakAgingService.transitionState(id, requestBody.getNewStatus(), changedBy, requestBody.getAssignedTo(), requestBody.getNotes());
        return SuccessResponse.of("Break " + id + " status updated to " + requestBody.getNewStatus());
    }
    @GetMapping("/{id}/audit")
    @Operation(summary = "Get audit trail", description = "Returns the full status-transition history for a break, including who made each change and any notes.")
    @ApiResponse(responseCode = "200", description = "Audit entries returned")
    public List<TradeBreakAudit> getAuditTrail(
            @Parameter(description = "Break ID") @PathVariable Long id) {
        return tradeBreakAuditRepository.findAllByTradeBreak_Id(id);
    }
    @PostMapping("/{id}/fuzzy-match")
    @Operation(summary = "Trigger fuzzy match", description = "Runs the fuzzy matching algorithm against this break to find and rank resolution candidates.")
    @ApiResponse(responseCode = "200", description = "Match candidates populated")
    public SuccessResponse fuzzyMatch(@Parameter(description = "Break ID") @PathVariable Long id) {
        fuzzyMatchingService.matchBreak(id);
        return SuccessResponse.of("Fuzzy match complete for break " + id);
    }
    @GetMapping("/{id}/candidates")
    @Operation(summary = "Get match candidates", description = "Returns non-rejected match candidates for a break, ordered by rank (highest confidence first).")
    @ApiResponse(responseCode = "200", description = "Candidate list returned")
    public List<TradeMatchCandidate> getMatchCandidates(
            @Parameter(description = "Break ID") @PathVariable Long id) {
        return tradeMatchCandidateRepository.findByTradeBreak_IdAndRejectedFalseOrderByRankAsc(id);
    }

}
