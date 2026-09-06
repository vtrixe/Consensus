package com.example.consensus.web;

import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.reconciliation.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/run")
    public List<TradeBreak> reconcile() {
        return reconciliationService.reconcile();
    }
}
