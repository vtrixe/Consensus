package com.example.consensus.worker.events;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CandidateRejectedEvent  implements KafkaEvent {
    Long candidateId;
    Long breakId;
    String tenantSchema;

    public String getEventKey() {
        return breakId != null ? breakId.toString() : null;
    }
}
