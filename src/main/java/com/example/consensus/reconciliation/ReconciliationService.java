package com.example.consensus.reconciliation;

import com.example.consensus.model.entity.TradeBreak;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface ReconciliationService {

    public List<TradeBreak> reconcile();
}
