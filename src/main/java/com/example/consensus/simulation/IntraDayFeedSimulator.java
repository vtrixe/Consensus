package com.example.consensus.simulation;

import com.example.consensus.loader.TradeGenerator;
import com.example.consensus.model.dtos.SymbolState;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.outbound.EquityPrices;
import com.example.consensus.outbound.MarketDataOutbound;
import com.example.consensus.worker.events.TradeIngestedEvent;
import com.example.consensus.worker.producer.ProducerService;
import com.example.consensus.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Slf4j
@RequiredArgsConstructor
public class IntraDayFeedSimulator {

    private final TradeGenerator tradeGenerator;
    private final MarketDataOutbound marketDataOutbound;
    private final ProducerService producerService;

    private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "JPM");
    private final Random random = new Random();
    private final Map<String, SymbolState> symbolState = new HashMap<>();
    private final Set<String> activeTenants = ConcurrentHashMap.newKeySet();
    private int counter = 0;

    public void start(String schema) {
        if (symbolState.isEmpty()) seedPrices();
        activeTenants.add(schema);
        log.info("Simulation started for schema={}", schema);
    }

    public void stop(String schema) {
        activeTenants.remove(schema);
        log.info("Simulation stopped for schema={}", schema);
    }

    @Scheduled(fixedRate = 30_000)
    public void simulateIntraDayFeed() {
        if (activeTenants.isEmpty() || symbolState.isEmpty()) return;
        for (String schema : activeTenants) {
            TenantContext.set(schema);
            try {
                List<String> symbols = new ArrayList<>(symbolState.keySet());
                String symbol = symbols.get(random.nextInt(symbols.size()));
                SymbolState current = symbolState.get(symbol);
                double tickDt = 30.0 / 23400.0;
                double Z = random.nextGaussian();
                double mu = current.getRsMean();
                double sigma = current.getDailySigma();
                double SnextTick = current.getLastClose() * Math.exp(
                        (mu - 0.5 * sigma * sigma) * tickDt + sigma * Math.sqrt(tickDt) * Z
                );
                current.setLastClose(SnextTick);
                List<Trade> trades = tradeGenerator.generateTick(symbol, BigDecimal.valueOf(current.getLastClose()), LocalDate.now(), ++counter);
                trades.forEach(t -> {
                    TradeIngestedEvent event = new TradeIngestedEvent();
                    event.setTradeId(t.getTradeId());
                    event.setSource(t.getSource());
                    event.setSymbol(t.getSymbol());
                    event.setSettlementDate(t.getSettlementDate());
                    event.setTenantSchema(schema);
                    producerService.publishTradeIngested(event);
                });
            } finally {
                TenantContext.clear();
            }
        }
    }

    private void seedPrices() {
        for (String symbol : SYMBOLS) {
            Map<String, EquityPrices.DailyData> series;
            try {
                series = marketDataOutbound.getDailySeries(symbol).getTimeSeriesDaily();
                Thread.sleep(15_000);
            } catch (Exception e) {
                log.warn("seedPrices: skipping {} — {}", symbol, e.getMessage());
                continue;
            }
            List<String> keys = new ArrayList<>(series.keySet());
            Collections.sort(keys);
            List<Double> rS = new ArrayList<>();
            for (int i = 1; i < keys.size(); i++) {
                String previousDate = keys.get(i - 1);
                String currentDate = keys.get(i);

                Double previousClose = Double.parseDouble(series.get(previousDate).getClose());
                Double currentClose = Double.parseDouble(series.get(currentDate).getClose());
                Double rI = Math.log(currentClose / previousClose);
                rS.add(rI);
            }

            Double rs_mean = (rS.stream().mapToDouble(Double::doubleValue).sum()) / rS.size();
            double sumSquaredDeviations = rS.stream()
                    .mapToDouble(x -> Math.pow(x - rs_mean, 2))
                    .sum();
            Double variance = sumSquaredDeviations / (rS.size() - 1);
            Double dailySigma = Math.sqrt(variance);
            String lastDate = keys.get(keys.size() - 1);
            Double lastClose = Double.parseDouble(series.get(lastDate).getClose());
            symbolState.put(symbol, new SymbolState(lastClose, dailySigma,rs_mean));
        }
    }
}