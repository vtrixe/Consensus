package com.example.consensus.model.repository;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.entity.TradeBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeBreakRepository extends JpaRepository<TradeBreak, Long> {

    List<TradeBreak> findAllByStatus(BreakStatus  status);
    List<TradeBreak> findAllByTradeId(String tradeId);
    List<TradeBreak> findAllByStatusIn(List<BreakStatus> statuses);
    Optional<TradeBreak> findByTradeIdAndBreakTypeAndStatus(String tradeId, BreakType breakType, BreakStatus status);
}
