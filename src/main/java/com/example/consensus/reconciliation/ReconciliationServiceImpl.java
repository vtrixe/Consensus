package com.example.consensus.reconciliation;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.BreakType;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.repository.TradeBreakRepository;
import com.example.consensus.model.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final TradeRepository tradeRepository;
    private final TradeBreakRepository tradeBreakRepository;

    @Override
    public List<TradeBreak> reconcile() {
        List<TradeBreak> breaks = new ArrayList<>();

        List<Trade> blotterTrades = tradeRepository.findBySource(DataSource.BLOTTER);
        Set<String> blotterTradeIds = blotterTrades.stream()
                .map(Trade::getTradeId)
                .collect(Collectors.toSet());

        for (Trade blotter : blotterTrades) {
            Optional<Trade> custodianOpt = tradeRepository
                    .findByTradeIdAndSource(blotter.getTradeId(), DataSource.CUSTODIAN);

            if (custodianOpt.isEmpty()) {
                breaks.add(newBreak(blotter.getTradeId(), BreakType.MISSING_CUSTODIAN, null, null));
            } else {
                Trade cust = custodianOpt.get();
                if (blotter.getPrice().compareTo(cust.getPrice()) != 0)
                    breaks.add(newBreak(blotter.getTradeId(), BreakType.PRICE_MISMATCH,
                            blotter.getPrice().toString(), cust.getPrice().toString()));

                if (blotter.getQuantity().compareTo(cust.getQuantity()) != 0)
                    breaks.add(newBreak(blotter.getTradeId(), BreakType.QUANTITY_MISMATCH,
                            blotter.getQuantity().toString(), cust.getQuantity().toString()));

                if (!blotter.getSettlementDate().equals(cust.getSettlementDate()))
                    breaks.add(newBreak(blotter.getTradeId(), BreakType.SETTLEMENT_DATE_MISMATCH,
                            blotter.getSettlementDate().toString(), cust.getSettlementDate().toString()));

                if (!blotter.getCounterparty().equals(cust.getCounterparty()))
                    breaks.add(newBreak(blotter.getTradeId(), BreakType.COUNTERPARTY_MISMATCH,
                            blotter.getCounterparty(), cust.getCounterparty()));
            }
        }

        tradeRepository.findBySource(DataSource.CUSTODIAN).stream()
                .filter(c -> !blotterTradeIds.contains(c.getTradeId()))
                .map(c -> newBreak(c.getTradeId(), BreakType.MISSING_BLOTTER, null, c.getTradeId()))
                .forEach(breaks::add);

        tradeBreakRepository.saveAll(breaks);
        log.info("Reconciliation complete — {} break(s) found", breaks.size());
        return breaks;
    }

    private TradeBreak newBreak(String tradeId, BreakType type,
                                String blotterValue, String custodianValue) {
        return new TradeBreak()
                .setTradeId(tradeId)
                .setBreakType(type)
                .setBlotterValue(blotterValue)
                .setCustodianValue(custodianValue)
                .setDetectedAt(LocalDateTime.now())
                .setStatus(BreakStatus.OPEN);
    }
}
