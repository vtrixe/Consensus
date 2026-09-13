package com.example.consensus.loader;

import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.Enums.TransactionSide;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.repository.TradeRepository;
import com.example.consensus.outbound.EquityPrices;
import com.example.consensus.outbound.MarketDataOutbound;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeGenerator {

    private final MarketDataOutbound marketDataOutbound;
    private final TradeRepository tradeRepository;

    private static final List<String> SYMBOLS = List.of("AAPL", "MSFT", "JPM");

    private static final List<String> COUNTERPARTIES = List.of(
            "Goldman Sachs", "Morgan Stanley", "JPMorgan",
            "Citadel Securities", "Virtu Financial", "Jane Street",
            "Two Sigma", "UBS"
    );

    private static final int[] QUANTITY_LOTS = {
            100, 500, 1_000, 2_000, 5_000, 10_000, 25_000, 50_000
    };

    private final Random random = new Random();

    private enum BreakScenario {
        CLEAN, MISSING_TRADE, PRICE_BREAK,
        QUANTITY_BREAK, SETTLEMENT_BREAK,
        COUNTERPARTY_BREAK, DUPLICATE;

        static BreakScenario pick(Random random) {
            int roll = random.nextInt(100);
            if (roll < 70) return CLEAN;
            if (roll < 78) return MISSING_TRADE;
            if (roll < 86) return PRICE_BREAK;
            if (roll < 92) return QUANTITY_BREAK;
            if (roll < 96) return SETTLEMENT_BREAK;
            if (roll < 99) return COUNTERPARTY_BREAK;
            return DUPLICATE;
        }
    }

    private LocalDate settlementDate(LocalDate tradeDate) {
        LocalDate settlement = tradeDate;
        int businessDaysAdded = 0;
        while (businessDaysAdded < 2) {
            settlement = settlement.plusDays(1);
            if (settlement.getDayOfWeek() != DayOfWeek.SATURDAY
                    && settlement.getDayOfWeek() != DayOfWeek.SUNDAY) {
                businessDaysAdded++;
            }
        }
        return settlement;
    }

    private Trade buildBlotterTrade(String symbol, BigDecimal price,
                                    LocalDate tradeDate, int sequence) {
        String tradeId = String.format("TRD-%s-%s-%03d",
                symbol, tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE), sequence);

        return new Trade()
                .setTradeId(tradeId)
                .setSource(DataSource.BLOTTER)
                .setSymbol(symbol)
                .setPrice(price)
                .setQuantity(pickQuantity())
                .setTradeDate(tradeDate)
                .setSettlementDate(settlementDate(tradeDate))
                .setSide(random.nextBoolean() ? TransactionSide.BUY : TransactionSide.SELL)
                .setCounterparty(COUNTERPARTIES.get(random.nextInt(COUNTERPARTIES.size())))
                .setCurrency("USD");
    }

    private BigDecimal pickQuantity() {
        int lots = QUANTITY_LOTS[random.nextInt(QUANTITY_LOTS.length)];
        return BigDecimal.valueOf(lots);
    }

    private Trade buildCustodianTrade(Trade blotter, BreakScenario scenario) {
        return switch (scenario) {

            case CLEAN -> new Trade()
                    .setTradeId(blotter.getTradeId())
                    .setSource(DataSource.CUSTODIAN)
                    .setSymbol(blotter.getSymbol())
                    .setPrice(blotter.getPrice())
                    .setQuantity(blotter.getQuantity())
                    .setTradeDate(blotter.getTradeDate())
                    .setSettlementDate(blotter.getSettlementDate())
                    .setSide(blotter.getSide())
                    .setCounterparty(blotter.getCounterparty())
                    .setCurrency(blotter.getCurrency());

            case PRICE_BREAK -> {
                int bps = random.nextInt(2, 16);
                BigDecimal delta = blotter.getPrice()
                        .multiply(BigDecimal.valueOf(bps))
                        .divide(BigDecimal.valueOf(10_000), 6, RoundingMode.HALF_UP);
                BigDecimal brokenPrice = random.nextBoolean()
                        ? blotter.getPrice().add(delta)
                        : blotter.getPrice().subtract(delta);
                yield new Trade()
                        .setTradeId(blotter.getTradeId())
                        .setSource(DataSource.CUSTODIAN)
                        .setSymbol(blotter.getSymbol())
                        .setPrice(brokenPrice)
                        .setQuantity(blotter.getQuantity())
                        .setTradeDate(blotter.getTradeDate())
                        .setSettlementDate(blotter.getSettlementDate())
                        .setSide(blotter.getSide())
                        .setCounterparty(blotter.getCounterparty())
                        .setCurrency(blotter.getCurrency());
            }

            case QUANTITY_BREAK -> {
                int lotsOff = (random.nextInt(3) + 1) * 100;
                BigDecimal brokenQty = random.nextBoolean()
                        ? blotter.getQuantity().add(BigDecimal.valueOf(lotsOff))
                        : blotter.getQuantity().subtract(BigDecimal.valueOf(lotsOff));
                yield new Trade()
                        .setTradeId(blotter.getTradeId())
                        .setSource(DataSource.CUSTODIAN)
                        .setSymbol(blotter.getSymbol())
                        .setPrice(blotter.getPrice())
                        .setQuantity(brokenQty)
                        .setTradeDate(blotter.getTradeDate())
                        .setSettlementDate(blotter.getSettlementDate())
                        .setSide(blotter.getSide())
                        .setCounterparty(blotter.getCounterparty())
                        .setCurrency(blotter.getCurrency());
            }

            case SETTLEMENT_BREAK -> new Trade()
                    .setTradeId(blotter.getTradeId())
                    .setSource(DataSource.CUSTODIAN)
                    .setSymbol(blotter.getSymbol())
                    .setPrice(blotter.getPrice())
                    .setQuantity(blotter.getQuantity())
                    .setTradeDate(blotter.getTradeDate())
                    .setSettlementDate(blotter.getSettlementDate().plusDays(1))
                    .setSide(blotter.getSide())
                    .setCounterparty(blotter.getCounterparty())
                    .setCurrency(blotter.getCurrency());

            case COUNTERPARTY_BREAK -> {
                String different = COUNTERPARTIES.stream()
                        .filter(c -> !c.equals(blotter.getCounterparty()))
                        .findAny().orElse("Unknown");
                yield new Trade()
                        .setTradeId(blotter.getTradeId())
                        .setSource(DataSource.CUSTODIAN)
                        .setSymbol(blotter.getSymbol())
                        .setPrice(blotter.getPrice())
                        .setQuantity(blotter.getQuantity())
                        .setTradeDate(blotter.getTradeDate())
                        .setSettlementDate(blotter.getSettlementDate())
                        .setSide(blotter.getSide())
                        .setCounterparty(different)
                        .setCurrency(blotter.getCurrency());
            }

            // MISSING_TRADE and DUPLICATE are handled in generate(), not here
            default -> throw new IllegalStateException(
                    "Scenario " + scenario + " should not reach buildCustodianTrade");
        };
    }

    public void generate() {
        int sequence = 1;

        for (String symbol : SYMBOLS) {

            // fetch real prices from Alpha Vantage
            Map<String, EquityPrices.DailyData> priceSeries;
            try {
                priceSeries = marketDataOutbound.getDailySeries(symbol).getTimeSeriesDaily();
                Thread.sleep(15_000); // stay under free tier: 5 requests/min
            } catch (Exception e) {
                log.warn("Skipping symbol {} — API call failed: {}", symbol, e.getMessage());
                continue;
            }

            // pick 4-6 random dates from the available price data
            List<String> dates = new ArrayList<>(priceSeries.keySet());
            Collections.shuffle(dates, random);
            List<String> selectedDates = dates.subList(0, Math.min(5, dates.size()));

            for (String dateStr : selectedDates) {
                LocalDate tradeDate = LocalDate.parse(dateStr);
                BigDecimal price = new BigDecimal(
                        priceSeries.get(dateStr).getClose()
                );

                BreakScenario scenario = BreakScenario.pick(random);
                Trade blotter = buildBlotterTrade(symbol, price, tradeDate, sequence++);

                // always save the blotter record
                tradeRepository.save(blotter);

                if (scenario == BreakScenario.MISSING_TRADE) {
                    // intentionally skip custodian — this IS the break
                    continue;
                }

                Trade custodian = buildCustodianTrade(blotter,
                        scenario == BreakScenario.DUPLICATE ? BreakScenario.CLEAN : scenario);
                tradeRepository.save(custodian);

                if (scenario == BreakScenario.DUPLICATE) {
                    // save a second custodian record with a modified tradeId suffix
                    Trade duplicate = buildCustodianTrade(blotter, BreakScenario.CLEAN);
                    duplicate.setTradeId(blotter.getTradeId() + "-DUP");
                    tradeRepository.save(duplicate);
                }
            }
        }
    }


}
