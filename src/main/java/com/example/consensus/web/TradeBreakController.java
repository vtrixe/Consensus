package com.example.consensus.web;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.reconciliation.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/breaks")
@RequiredArgsConstructor
public class TradeBreakController {

    private final ReconciliationService reconciliationService;

    @GetMapping
    public List<TradeBreak> getBreaks(@RequestParam(required = false) BreakStatus status) {
        return reconciliationService.findBreaks(status);
    }

    @GetMapping("/{tradeId}")
    public List<TradeBreak> getBreaksByTradeId(@PathVariable String tradeId) {
        return reconciliationService.findBreaksByTradeId(tradeId);
    }
}
