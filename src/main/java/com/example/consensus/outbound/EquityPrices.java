package com.example.consensus.outbound;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class EquityPrices {

    @JsonProperty("Time Series (Daily)")
    private Map<String, DailyData> timeSeriesDaily;

    @Data
    @NoArgsConstructor
    public static class DailyData {
        @JsonProperty("1. open")  private String open;
        @JsonProperty("2. high")  private String high;
        @JsonProperty("3. low")   private String low;
        @JsonProperty("4. close") private String close;
        @JsonProperty("5. volume") private String volume;
    }
}
