package com.example.consensus.worker.topics;

public enum Topics {

    BREAK_DETECTED("breaks.detected"),
    BREAK_STATUS_UPDATE("breaks.status.changed"),
    CANDIDATE_REJECTED("candidate.rejected"),
    TRADE_INGESTED("trade.ingested");

    private final String topic;

    Topics(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}