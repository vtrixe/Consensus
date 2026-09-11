package com.example.consensus.model.entity;


import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.MaterialityTier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_breaks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class TradeBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "trade_id", nullable = false)
    private String tradeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakType breakType;
    @Column(name = "custordian_value")
    private String custodianValue;
    @Column(name = "blotter_value")
    private String blotterValue;
    @Column(name = "detected_at")
    private LocalDateTime  detectedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakStatus status;
    @Column(name="notional_impact", nullable = false)
    private BigDecimal notionalImpact;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialityTier materialityTier;
    @Column(name="bps_deviation")
    private BigDecimal bpsDeviation;
    @Column(name="minutes_to_settlement")
    private Long minutesToSettlement;
    @Column(name="composite_score")
    private Long compositeScore;
    @Column(name="settlement_date", nullable = false)
    private LocalDateTime settlementDate;
    @Column(name="estimated_resolution_minutes")
    private Long estimatedResolutionMinutes;
    @Column(name="settlement_fail_risk")
    private Boolean settlementFailRisk;


}
