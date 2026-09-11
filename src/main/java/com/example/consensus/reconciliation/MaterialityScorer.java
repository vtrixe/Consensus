package com.example.consensus.reconciliation;

import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.Enums.MaterialityTier;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MaterialityScorer {

    private static final BigDecimal TEN_THOUSAND      = BigDecimal.valueOf(10_000);
    private static final BigDecimal ONE_HUNDRED_THOUSAND = BigDecimal.valueOf(100_000);
    private static final BigDecimal ONE_BPS            = BigDecimal.valueOf(0.0001);
    private static LocalDateTime settlementCutoff() {
        return LocalDateTime.now(ZoneId.of("America/New_York"))
                .withHour(15)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
    }
    private static final Map<String, Integer> COUNTERPARTY_TIERS = new HashMap<>();

    private final TradeBreakRepository tbBreakRepository;

    private static final Map<String, Long> SYMBOL_ADV = Map.of(
            "AAPL", 60_000_000L,
            "MSFT", 25_000_000L,
            "JPM",  10_000_000L
    );

    static {
        // Tier 1 — bulge bracket banks
        COUNTERPARTY_TIERS.put("GS", 1);
        COUNTERPARTY_TIERS.put("JPM", 1);
        COUNTERPARTY_TIERS.put("MS", 1);
        COUNTERPARTY_TIERS.put("UBS", 1);

        // Tier 2 — major market makers / prop trading firms
        COUNTERPARTY_TIERS.put("Citadel", 2);
        COUNTERPARTY_TIERS.put("Virtu", 2);
        COUNTERPARTY_TIERS.put("JaneStreet", 2);

        // Tier 3 — everyone else
        COUNTERPARTY_TIERS.put("TwoSigma", 2); // see note below
    }

    private static final Map<BreakType, Long> BASE_RESOLUTION_MINUTES = Map.of(
            BreakType.MISSING_CUSTODIAN,        180L, // external chase — slowest
            BreakType.MISSING_BLOTTER,          120L,
            BreakType.PRICE_MISMATCH,            45L,
            BreakType.QUANTITY_MISMATCH,         45L,
            BreakType.SETTLEMENT_DATE_MISMATCH,  60L,
            BreakType.COUNTERPARTY_MISMATCH,     90L
    );


    private final TradeRepository tradeRepository;

    public void notion(TradeBreak tradeBreak) {
        BigDecimal notional = switch (tradeBreak.getBreakType()) {

            case MISSING_CUSTODIAN -> {
                Trade t = fetch(tradeBreak.getTradeId(), DataSource.BLOTTER);
                yield t.getPrice().multiply(t.getQuantity());
            }

            case MISSING_BLOTTER -> {
                Trade t = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                yield t.getPrice().multiply(t.getQuantity());
            }

            case PRICE_MISMATCH -> {
                Trade b = fetch(tradeBreak.getTradeId(), DataSource.BLOTTER);
                Trade c = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                yield b.getQuantity().multiply(b.getPrice().subtract(c.getPrice()).abs());
            }

            case QUANTITY_MISMATCH -> {
                Trade b = fetch(tradeBreak.getTradeId(), DataSource.BLOTTER);
                Trade c = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                yield b.getPrice().multiply(b.getQuantity().subtract(c.getQuantity()).abs());
            }

            case SETTLEMENT_DATE_MISMATCH -> {
                Trade b = fetch(tradeBreak.getTradeId(), DataSource.BLOTTER);
                Trade c = fetch(tradeBreak.getTradeId(), DataSource.CUSTODIAN);
                long days = Math.abs(ChronoUnit.DAYS.between(b.getSettlementDate(), c.getSettlementDate()));
                yield b.getPrice().multiply(b.getQuantity()).multiply(ONE_BPS).multiply(BigDecimal.valueOf(days));
            }

            case COUNTERPARTY_MISMATCH -> BigDecimal.ZERO;
        };

        MaterialityTier tier = tradeBreak.getBreakType() == com.example.consensus.model.Enums.BreakType.COUNTERPARTY_MISMATCH
                ? MaterialityTier.MAJOR
                : determineTier(notional);

        tradeBreak.setNotionalImpact(notional);
        tradeBreak.setMaterialityTier(tier);
    }

    private MaterialityTier determineTier(BigDecimal notional) {
        if (notional.compareTo(TEN_THOUSAND) < 0)        return MaterialityTier.MINOR;
        if (notional.compareTo(ONE_HUNDRED_THOUSAND) <= 0) return MaterialityTier.MAJOR;
        return MaterialityTier.CRITICAL;
    }

    private Trade fetch(String tradeId, DataSource source) {
        return tradeRepository.findByTradeIdAndSource(tradeId, source)
                .orElseThrow(() -> new IllegalStateException(
                        "Trade not found: " + tradeId + " / " + source));
    }



    public Long scoreTradeBreak(TradeBreak tradeBreak) {
        Trade t = fetch(tradeBreak.getTradeId(), resolveSource(tradeBreak.getBreakType()));
        if (t == null) {
            return 0L;
        }


        return getSettlementUrgencyScore(tradeBreak)
                + getAdvConcerntrationScore(t.getSymbol(), t.getQuantity())
                + getNotionalImpactScore(tradeBreak.getNotionalImpact())
                + getCounterpartyTierScore(t.getCounterparty());
    }

    private DataSource resolveSource(BreakType breakType) {
        return switch (breakType) {
            case MISSING_CUSTODIAN -> DataSource.CUSTODIAN;
            case MISSING_BLOTTER  -> DataSource.BLOTTER;
            default -> DataSource.BLOTTER;
        };
    }

    private Long getSettlementUrgencyScore(TradeBreak tradeBreak) {
        LocalDateTime cutoff = settlementCutoff();

        long minutes = ChronoUnit.MINUTES.between(
                ZonedDateTime.now(ZoneId.of("America/New_York")),
                cutoff
        );

        tradeBreak.setMinutesToSettlement(minutes);
        tbBreakRepository.save(tradeBreak);


        if (minutes < 0)   return 40L;
        if (minutes < 60)  return 30L;
        if (minutes < 240) return 20L;
        if (minutes < 480) return 10L;
        return 0L;
    }

    private Long getAdvConcerntrationScore(String symbol, BigDecimal quantity) {
        Long adv = SYMBOL_ADV.get(symbol);
        if (adv == null || adv == 0L) {
            return 0L;
        }

        BigDecimal advBd = BigDecimal.valueOf(adv);
        BigDecimal concentrationPct = quantity
                .divide(advBd, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (concentrationPct.compareTo(BigDecimal.valueOf(50)) >= 0)  return 30L; // ≥50% of ADV
        if (concentrationPct.compareTo(BigDecimal.valueOf(20)) >= 0)  return 22L; // 20–50%
        if (concentrationPct.compareTo(BigDecimal.valueOf(5))  >= 0)  return 15L; // 5–20%
        if (concentrationPct.compareTo(BigDecimal.valueOf(1))  >= 0)  return 8L;  // 1–5%
        return 0L; // <1%
    }

    private Long getCounterpartyTierScore(String counterparty) {
        Integer tier = COUNTERPARTY_TIERS.getOrDefault(counterparty, 3);

        return switch (tier) {
            case 1 -> 0L;   // top-tier bank
            case 2 -> 5L;   // major prop/market-maker
            default -> 10L; // tier 3 / unmapped
        };
    }

    private Long getNotionalImpactScore(BigDecimal notional) {
        if (notional == null) {
            return 0L;
        }

        if (notional.compareTo(BigDecimal.valueOf(50_000_000)) >= 0) return 20L; // ≥$50M
        if (notional.compareTo(BigDecimal.valueOf(10_000_000)) >= 0) return 15L; // $10M–$50M
        if (notional.compareTo(BigDecimal.valueOf(1_000_000))  >= 0) return 8L;  // $1M–$10M
        return 0L; // <$1M
    }


    public Long estimateResolutionMinutes(BreakType breakType, String counterparty, String symbol, BigDecimal quantity) {
        long base = BASE_RESOLUTION_MINUTES.getOrDefault(breakType, 60L);

        // counterparty tier adjustment — lower tier (less established) takes longer
        int tier = COUNTERPARTY_TIERS.getOrDefault(counterparty, 3);
        double tierMultiplier = switch (tier) {
            case 1 -> 0.8;  // faster — established relationship, likely automated
            case 2 -> 1.0;  // baseline
            default -> 1.3; // tier 3 / unmapped — slower
        };

        // ADV concentration adjustment — larger relative position takes longer to unwind/confirm
        Long adv = SYMBOL_ADV.get(symbol);
        double advMultiplier = 1.0;
        if (adv != null && adv > 0L && quantity != null) {
            BigDecimal concentrationPct = quantity
                    .divide(BigDecimal.valueOf(adv), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (concentrationPct.compareTo(BigDecimal.valueOf(20)) >= 0)      advMultiplier = 1.4;
            else if (concentrationPct.compareTo(BigDecimal.valueOf(5)) >= 0)  advMultiplier = 1.15;
            // else stays 1.0
        }

        return Math.round(base * tierMultiplier * advMultiplier);
    }


}
