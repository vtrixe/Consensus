package com.example.consensus.model.entity;

import com.example.consensus.model.Enums.DataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeId implements Serializable {

    @Column(name = "trade_id", nullable = false)
    private String tradeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private DataSource source;

}
