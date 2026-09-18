package com.example.consensus.web;

public record SuccessResponse(boolean success, String message) {
    public static SuccessResponse of(String message) {
        return new SuccessResponse(true, message);
    }
}
