package com.example.consensus.reconciliation;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.entity.TradeBreak;

import java.util.List;

public interface ReconciliationService {

    List<TradeBreak> reconcile();

    List<TradeBreak> findBreaks(BreakStatus status);

    List<TradeBreak> findBreaksByTradeId(String tradeId);
}
