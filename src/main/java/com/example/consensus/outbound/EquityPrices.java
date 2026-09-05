package com.example.consensus.outbound;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record EquityPrices(
        @JsonProperty("Time Series (Daily)") Map<String, DailyData> timeSeriesDaily
) {

    public record DailyData(
            @JsonProperty("1. open") String open,
            @JsonProperty("2. high") String high,
            @JsonProperty("3. low") String low,
            @JsonProperty("4. close") String close,
            @JsonProperty("5. volume") String volume
    ) {}
}
