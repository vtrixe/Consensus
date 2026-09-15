package com.example.consensus.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SymbolState {

    private double lastClose;
    private double dailySigma;
    private double rsMean;
}