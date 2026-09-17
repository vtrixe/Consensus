package com.example.consensus.web;

import com.example.consensus.outbound.EquityPrices;
import com.example.consensus.outbound.MarketDataOutbound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/market-data")
@RequiredArgsConstructor
@Tag(name = "Market Data", description = "External equity price data")
@SecurityRequirement(name = "BearerAuth")
public class MarketDataController {

    private final MarketDataOutbound marketDataOutbound;

    @GetMapping("/{symbol}")
    @Operation(summary = "Get daily price series", description = "Fetches OHLCV daily series for an equity symbol from the external market data provider.")
    @ApiResponse(responseCode = "200", description = "Price series returned")
    public EquityPrices getDailySeries(
            @Parameter(description = "Ticker symbol, e.g. AAPL") @PathVariable String symbol) {
        return marketDataOutbound.getDailySeries(symbol);
    }
}