package com.example.consensus.worker.events;

import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.Enums.MaterialityTier;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class TradeIngestedEvent implements KafkaEvent {

    String tradeId;
    DataSource  source;
    String symbol;
    LocalDate settlementDate;
    String tenantSchema;

    public String getEventKey() {
        return tradeId != null ? tradeId.toString() : null;
    }
}
