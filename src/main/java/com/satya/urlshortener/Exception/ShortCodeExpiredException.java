package com.satya.urlshortener.Exception;

public class ShortCodeExpiredException extends RuntimeException{
    private String shortCode;

    public ShortCodeExpiredException(String shortCode) {
        super("Short code '" + shortCode + "' has expired.");
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}
