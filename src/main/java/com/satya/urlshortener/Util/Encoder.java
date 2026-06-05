package com.satya.urlshortener.Util;
//implement base62 encoding and decoding logic for the URL shortener service.
// This class will be responsible for converting a numeric ID into a short code and vice versa.

import org.springframework.stereotype.Component;

@Component
public class Encoder {
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62.charAt((int)(num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public long decode(String str) {
        long num = 0;
        for (char c : str.toCharArray()) {
            num = num * 62 + BASE62.indexOf(c);
        }
        return num;
    }
}
