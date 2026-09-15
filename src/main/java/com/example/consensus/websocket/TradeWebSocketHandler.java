package com.example.consensus.websocket;

import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.repository.TradeRepository;
import com.example.consensus.worker.events.TradeIngestedEvent;
import com.example.consensus.worker.producer.ProducerService;
import com.example.consensus.worker.topics.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TradeWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final TradeRepository tradeRepository;
    private final ProducerService producerService;

    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket session opened: sessionId={}, remoteAddress={}",
                session.getId(), session.getRemoteAddress());
    }

    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket session closed: sessionId={}, status={}",
                session.getId(), status);
    }

    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received message on sessionId={}: {}", session.getId(), payload);

        try {
            Trade trade = objectMapper.readValue(payload, Trade.class);

            Trade saved = tradeRepository.save(trade);

            TradeIngestedEvent event = new TradeIngestedEvent();
            event.setTradeId(saved.getTradeId());
            event.setSource(trade.getSource());
            event.setSymbol(trade.getSymbol());
            event.setSettlementDate(saved.getSettlementDate());
            producerService.publishTradeIngested(event);

            Map<String, Object> ack = Map.of(
                    "ack", saved.getTradeId(),
                    "saved", true
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));

            log.info("Trade ingested: tradeId={}, sessionId={}", saved.getTradeId(), session.getId());

        } catch (Exception e) {
            log.error("Failed to process trade message on sessionId={}: {}", session.getId(), e.getMessage(), e);

            Map<String, Object> nack = Map.of(
                    "ack", (Object) null,
                    "saved", false,
                    "error", e.getMessage()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(nack)));
        }
    }


}
