package com.example.consensus.model.entity;

import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.Enums.TransactionSide;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Trade {
    @EmbeddedId
    private TradeId id;
    @Column(nullable = false)
    private String symbol;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(nullable = false)
    private LocalDate tradeDate;
    @Column(nullable = false)
    private LocalDate settlementDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionSide side;
    @Column(nullable = false)
    private String counterparty;
    @Column(nullable = false, length = 3)
    private String currency;

}
