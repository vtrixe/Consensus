package com.example.consensus.loader;

import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataLoader implements ApplicationListener<ApplicationReadyEvent> {

    private final TradeGenerator tradeGenerator;
    private final TradeRepository tradeRepository;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (tradeRepository.count() > 0) {
            log.info("Trades already seeded ({} rows), skipping generation",
                    tradeRepository.count());
            return;
        }

        log.info("Seeding trade data...");
        tradeGenerator.generate();
        log.info("Seeded {} trades", tradeRepository.count());
    }
}