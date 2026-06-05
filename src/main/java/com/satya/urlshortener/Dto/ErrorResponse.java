package com.satya.urlshortener.Dto;

import lombok.Data;

@Data
public class ErrorResponse {
    private String timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    public ErrorResponse(String error, String message, int status, String path) {
        this.timestamp = java.time.Instant.now().toString();
        this.error = error;
        this.message = message;
        this.status = status;
        this.path = path;
    }

}
