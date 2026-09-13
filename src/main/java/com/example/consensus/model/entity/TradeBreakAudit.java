package com.example.consensus.model.entity;

import com.example.consensus.model.Enums.BreakStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Entity
@Table(name = "trade_break_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class TradeBreakAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trade_break_id", nullable = false)
    private TradeBreak tradeBreak;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")

    private BreakStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status")

    private BreakStatus toStatus;

    @Column(name = "changed_at")

    private LocalDateTime changedAt;

    @Column(name = "changed_by")

    private String changedBy;

    @Column(name = "duration")

    private Long duration;

    @Column(name = "notes")

    private String notes;

}
