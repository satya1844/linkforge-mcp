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

import java.util.List;

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
public class LinkController {

    private final LinkService linkService;
    private final Encoder encoder;

    public LinkController(LinkService linkService, Encoder encoder) {
        this.encoder = encoder;
        this.linkService = linkService;
    }

    @PostMapping("/urls")
    public ResponseEntity<?> createShortUrl(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse response = linkService.createShortUrl(request);
        // return 201 Created with Location header pointing to the new short URL
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, response.getShortUrl())
                .body(response);
    }

    @GetMapping("/urls/{shortCode}/analytics")
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

    @GetMapping("/urls")
    public ResponseEntity<List<LinkResponse>> getAllLinks() {
        return ResponseEntity.ok(linkService.getAllLinks());
    }

    @DeleteMapping("/urls/{shortCode}")
    public ResponseEntity<Void> deleteLink(@PathVariable String shortCode) {
        linkService.deleteLink(shortCode);
        return ResponseEntity.noContent().build();
    }
}








