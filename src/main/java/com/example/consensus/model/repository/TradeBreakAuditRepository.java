package com.example.consensus.model.repository;

import com.example.consensus.model.entity.TradeBreakAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeBreakAuditRepository extends JpaRepository<TradeBreakAudit, Long> {

    List<TradeBreakAudit> findAllByTradeBreak_Id(Long breakId);

    Optional<TradeBreakAudit> findFirstByTradeBreak_IdOrderByIdDesc(Long breakId);

}
