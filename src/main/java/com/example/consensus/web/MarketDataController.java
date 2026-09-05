package com.example.consensus.web;

import com.example.consensus.outbound.EquityPrices;
import com.example.consensus.outbound.MarketDataOutbound;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market-data")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataOutbound marketDataOutbound;

    @GetMapping("/{symbol}")
    public EquityPrices getDailySeries(@PathVariable String symbol) {
        return marketDataOutbound.getDailySeries(symbol);
    }
}