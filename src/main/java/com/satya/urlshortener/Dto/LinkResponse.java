package com.satya.urlshortener.Dto;


import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Getter
@Setter
public class LinkResponse {
    private String shortCode;     // e.g. "myalias" or "xYz123"
    private String shortUrl;      // e.g. "http://localhost:8080/myalias"
    private String originalUrl;   // the long URL user submitted
    private LocalDateTime createdAt; // when the short link was created
    private LocalDateTime expiresAt; // optional, if expiry is set
    private long totalClicks;     // optional, default 0
}
