package com.example.consensus.model.entity;

import com.example.consensus.model.Enums.MatchTier;
import com.example.consensus.model.dtos.ScoreBreakdown;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_match_candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class TradeMatchCandidate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_break_id", nullable = false)
    private TradeBreak tradeBreak;

    @Column(name = "candidate_trade_id", nullable = false)
    private String candidateTradeId;

    @Column(name = "confidence_score", nullable = false)
    private Long confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_tier", nullable = false)
    private MatchTier matchTier;

    @Column(nullable = false)
    private Integer rank;

    @Column(nullable = false)
    private Boolean rejected = false;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Lob
    @Column(name = "score_breakdown")
    private String scoreBreakdown;
}
