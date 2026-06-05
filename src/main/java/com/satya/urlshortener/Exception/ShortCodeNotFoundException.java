package com.satya.urlshortener.Exception;

public class ShortCodeNotFoundException extends RuntimeException {
    public ShortCodeNotFoundException(String shortCode) {
        super("Invalid URL code mate :(" + shortCode);
    }
}
