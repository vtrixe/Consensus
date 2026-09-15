package com.example.consensus.worker.consumer;

import com.example.consensus.fuzzymatch.FuzzyMatchingService;
import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.worker.events.BreakDetectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FuzzyMatchConsumer {

    private final FuzzyMatchingService fuzzyMatchingService;

    @KafkaListener(topics = "breaks.detected", groupId = "consensus-group")
    public void onBreakDetected(BreakDetectedEvent event) {
        if (event.getBreakType() != BreakType.MISSING_BLOTTER
                && event.getBreakType() != BreakType.MISSING_CUSTODIAN) {
            return;
        }
        try {
            fuzzyMatchingService.matchBreak(event.getBreakId());
        } catch (Exception e) {
            log.error("FuzzyMatchConsumer failed for breakId={}", event.getBreakId(), e);
        }
    }
}
