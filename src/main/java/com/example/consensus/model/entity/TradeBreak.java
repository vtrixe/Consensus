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

    @Column(name = "blotter_value")
    private String blotterValue;

    @Column(name = "custodian_value")
    private String custodianValue;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakStatus status;

    // ── Notional & tier ──────────────────────────────────────────────────────
    @Column(name = "notional_impact")
    private BigDecimal notionalImpact;

    @Enumerated(EnumType.STRING)
    @Column(name = "materiality_tier")
    private MaterialityTier materialityTier;

    // ── Composite scoring ────────────────────────────────────────────────────
    @Column(name = "bps_deviation")
    private BigDecimal bpsDeviation;                  // price breaks only

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;              // midnight of trade's settlement date in ET

    @Column(name = "minutes_to_settlement")
    private Long minutesToSettlement;                  // minutes to DTC 3PM ET cutoff at detection time

    @Column(name = "estimated_resolution_minutes")
    private Long estimatedResolutionMinutes;           // feasibility gate input

    @Column(name = "composite_score")
    private Long compositeScore;                       // 0–100 weighted score

    @Column(name = "settlement_fail_risk", nullable = false)
    private Boolean settlementFailRisk = false;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name= "last_change_at" )

    private LocalDateTime lastChangeAt;

    @Column(name="sla_breached", nullable = false)

    private Boolean slaBreached = false;

    @Column(name = "investigation_started_at")

    private LocalDateTime investigationStartedAt;

    @Column(name = "resolved_at")

    private LocalDateTime resolvedAt;



}
