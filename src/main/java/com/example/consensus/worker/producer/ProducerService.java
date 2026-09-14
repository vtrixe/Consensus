package com.example.consensus.worker.producer;

import com.example.consensus.worker.events.*;
import com.example.consensus.worker.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBreakDetected(BreakDetectedEvent e) {
        publish(Topics.BREAK_DETECTED, e);
    }

    public void publishBreakStatusChanged(BreakStatusChanged e) {
        publish(Topics.BREAK_STATUS_UPDATE, e);
    }

    public void publishCandidateRejected(CandidateRejectedEvent e) {
        publish(Topics.CANDIDATE_REJECTED, e);
    }

    public void publishTradeIngested(TradeIngestedEvent e) {
        publish(Topics.TRADE_INGESTTED, e);
    }

    public <T extends KafkaEvent> void publish(Topics topic, T event) {
        if (Objects.isNull(event)) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        String key = event.getEventKey();

        if (Objects.isNull(key)) {
            throw new IllegalArgumentException(
                    "Event key cannot be null for topic: " + topic.getTopic()
            );
        }

        try {
            kafkaTemplate.send(
                    topic.getTopic(),
                    key,
                    event
            ).get();

            log.debug(
                    "Successfully published Kafka event, topic={}, key={}",
                    topic.getTopic(),
                    key
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            log.error(
                    "Kafka publish interrupted, topic={}, key={}",
                    topic.getTopic(),
                    key,
                    ex
            );

            throw new RuntimeException("Kafka publish interrupted", ex);

        } catch (ExecutionException ex) {
            log.error(
                    "Kafka publish failed, topic={}, key={}",
                    topic.getTopic(),
                    key,
                    ex
            );

            throw new RuntimeException("Failed to publish Kafka event", ex);
        }
    }

}
