package com.example.consensus.model.dtos;

public record ScoreBreakdown(
        Long quantity,
        Long price,
        Long settlementDate,
        Long counterparty
) {}