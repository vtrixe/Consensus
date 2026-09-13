package com.example.consensus.web;

import com.example.consensus.breakaging.BreakAgingService;
import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeBreakAudit;
import com.example.consensus.model.repository.TradeBreakAuditRepository;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.reconciliation.ReconciliationService;
import com.example.consensus.web.DTOs.Requests.UpdateBreakRequestBody;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/breaks")
@RequiredArgsConstructor
public class TradeBreakController {

    private final ReconciliationService reconciliationService;
    private final BreakAgingService breakAgingService;
    private final TradeBreakAuditRepository tradeBreakAuditRepository;

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
         breakAgingService.transitionState(id,requestBody.getNewStatus(),requestBody.getChangedBy(),requestBody.getAssignedTo(),requestBody.getNotes());
    }
    @GetMapping("/{id}/audit")
    public List<TradeBreakAudit> getAuditTrail(@PathVariable Long id) {
        return tradeBreakAuditRepository.findAllByTradeBreak_Id(id);
    }
}
