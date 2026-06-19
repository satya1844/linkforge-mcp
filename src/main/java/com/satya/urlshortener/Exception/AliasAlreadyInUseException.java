package com.satya.urlshortener.Exception;

public class AliasAlreadyInUseException extends RuntimeException {
    public AliasAlreadyInUseException(String shortCode) {
        super(shortCode);
    }
}
