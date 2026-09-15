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
public class TradeBreakController {

    private final ReconciliationService reconciliationService;
    private final BreakAgingService breakAgingService;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;
    private final FuzzyMatchingService  fuzzyMatchingService;
    private final TradeMatchCandidateRepository  tradeMatchCandidateRepository;

    @GetMapping
    public List<TradeBreak> getBreaks(@RequestParam(required = false) BreakStatus status) {
        return reconciliationService.findBreaks(status);
    }

    @GetMapping("/{tradeId}")
    public List<TradeBreak> getBreaksByTradeId(@PathVariable String tradeId) {
        return reconciliationService.findBreaksByTradeId(tradeId);
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateBreakStatus(@PathVariable Long id, @RequestBody UpdateBreakRequestBody requestBody) {
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
    }
    @GetMapping("/{id}/audit")
    public List<TradeBreakAudit> getAuditTrail(@PathVariable Long id) {
        return tradeBreakAuditRepository.findAllByTradeBreak_Id(id);
    }
    @PostMapping("/{id}/fuzzy-match")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fuzzyMatch(@PathVariable Long id) {
         fuzzyMatchingService.matchBreak(id);
    }
    @GetMapping("/{id}/candidates")
    public List<TradeMatchCandidate> getMatchCandidates(@PathVariable Long id) {
        return tradeMatchCandidateRepository.findByTradeBreak_IdAndRejectedFalseOrderByRankAsc(id);
    }

}
