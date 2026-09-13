package com.example.consensus.model.repository;

import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findBySource(DataSource source);

    Optional<Trade> findByTradeIdAndSource(String tradeId, DataSource source);

    @Query("""
    SELECT t
    FROM Trade t
    WHERE t.source = :source
      AND t.symbol = :symbol
      AND t.settlementDate BETWEEN :fromDate AND :toDate
""")
    List<Trade> findPotentialMatchingTrades(
            @Param("source") DataSource source,
            @Param("symbol") String symbol,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

}
