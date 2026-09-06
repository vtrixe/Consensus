package com.example.consensus.model.repository;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.entity.TradeBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeBreakRepository extends JpaRepository<TradeBreak, Long> {

    List<TradeBreak> findAllByStatus(BreakStatus  status);
    List<TradeBreak> findAllByTradeId(String tradeId);

}
