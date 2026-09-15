package com.example.consensus.worker.events;


import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.MaterialityTier;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class BreakDetectedEvent implements KafkaEvent {

    Long breakId;
    BreakType breakType;
    MaterialityTier materialityTier;
    Boolean settlementFailRisk;
    String tenantSchema;

    public String getEventKey() {
        return breakId != null ? breakId.toString() : null;
    }

}
