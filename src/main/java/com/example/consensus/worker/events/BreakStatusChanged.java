package com.example.consensus.worker.events;

import com.example.consensus.model.Enums.BreakStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BreakStatusChanged implements KafkaEvent {
    Long breakId;
    BreakStatus fromStatus;
    BreakStatus toStatus;
    String changedBy;

    public String getEventKey() {
        return breakId != null ? breakId.toString() : null;
    }
}
