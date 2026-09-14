package com.example.consensus.worker.events;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CandidateRejectedEvent  implements KafkaEvent {
    Long candidateId;
    Long breakId;

    public String getEventKey() {
        return breakId != null ? breakId.toString() : null;
    }
}
