package com.example.consensus.model.repository;

import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findBySource(DataSource source);

    Optional<Trade> findByTradeIdAndSource(String tradeId, DataSource source);

}
