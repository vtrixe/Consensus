package com.example.consensus.model.repository;


import com.example.consensus.model.entity.TradeMatchCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeMatchCandidateRepository  extends JpaRepository<TradeMatchCandidate, Long> {

    List<TradeMatchCandidate> findByTradeBreak_IdOrderByConfidenceScoreDesc(Long tradeBreakId);

    List<TradeMatchCandidate> findByTradeBreak_IdAndRejectedFalseOrderByRankAsc(Long tradeBreakId);
}
