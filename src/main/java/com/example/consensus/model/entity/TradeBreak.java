package com.example.consensus.model.entity;


import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.BreakStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
    @Column(name = "custordian_value", nullable = false)
    private String custodianValue;
    @Column(name = "blotter_value", nullable = false)
    private String blotterValue;
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime  detectedAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BreakStatus status;


}
