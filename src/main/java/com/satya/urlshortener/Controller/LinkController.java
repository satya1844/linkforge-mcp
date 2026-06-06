package com.satya.urlshortener.Controller;

import com.satya.urlshortener.Dto.CreateLinkRequest;
import com.satya.urlshortener.Dto.LinkAnalyticsResponse;
import com.satya.urlshortener.Dto.LinkResponse;
import com.satya.urlshortener.Service.LinkService;
import com.satya.urlshortener.Util.Encoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
POST /urls
  → validate input
  → generate Base62 short code (or use custom alias)
  → check uniqueness
  → persist to Postgres
  → return shortCode + full short URL

GET /{shortCode}
  → query Postgres by short_code
  → if not found → 404
  → if expired → 410
  → insert row into link_clicks (synchronous for now)
  → return 302 with Location header

GET /urls/{shortCode}/analytics
  → query Postgres
  → COUNT link_clicks WHERE link_id = ?
  → return JSON with totalClicks, createdAt, lastAccessed
 */

@RestController
@RequestMapping("/urls")
public class LinkController {

    LinkService linkService;
    Encoder encoder;
     public LinkController(LinkService linkService, Encoder encoder) {
         this.encoder = encoder;
        this.linkService = linkService;

    }

    @PostMapping
    public ResponseEntity<?> createShortUrl(@Valid @RequestBody CreateLinkRequest request) {
      LinkResponse response = linkService.createShortUrl(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<LinkAnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        return ResponseEntity.ok(linkService.getAnalytics(shortCode));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToOriginalUrl(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        String originalUrl = linkService.redirectToOriginalUrl(shortCode, request);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    }








