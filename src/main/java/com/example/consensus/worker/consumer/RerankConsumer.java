package com.example.consensus.worker.consumer;

import com.example.consensus.fuzzymatch.FuzzyMatchingService;
import com.example.consensus.tenant.TenantContext;
import com.example.consensus.worker.events.CandidateRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RerankConsumer {

    private final FuzzyMatchingService fuzzyMatchingService;

    @KafkaListener(topics = "candidate.rejected", groupId = "rerank-group")
    public void onCandidateRejected(CandidateRejectedEvent event) {
        TenantContext.set(event.getTenantSchema());
        try {
            fuzzyMatchingService.rerankCandidates(event.getBreakId());
        } catch (Exception e) {
            log.error("RerankConsumer failed for breakId={}", event.getBreakId(), e);
        } finally {
            TenantContext.clear();
        }
    }
}
