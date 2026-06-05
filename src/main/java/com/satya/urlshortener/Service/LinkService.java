package com.satya.urlshortener.Service;

import com.satya.urlshortener.Dto.CreateLinkRequest;
import com.satya.urlshortener.Dto.LinkAnalyticsResponse;
import com.satya.urlshortener.Dto.LinkResponse;
import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Entity.LinkClick;
import com.satya.urlshortener.Exception.AliasAlreadyInUseException;

import com.satya.urlshortener.Exception.ShortCodeExpiredException;
import com.satya.urlshortener.Exception.ShortCodeNotFoundException;
import com.satya.urlshortener.Repository.LinkClickRepository;
import com.satya.urlshortener.Repository.LinkRepository;
import com.satya.urlshortener.Util.Encoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class LinkService {

    private LinkRepository linkRepository;
    private UrlValidationService urlValidationService;
    private Encoder encoder;
    private LinkClickRepository linkClickRepository;



    @Value("${app.baseUrl}")
    private String baseUrl;
    public LinkService(LinkRepository linkRepository,LinkClickRepository linkClickRepository, Encoder encoder,UrlValidationService urlValidationService) {
        this.linkRepository = linkRepository;
        this.urlValidationService = urlValidationService;
        this.encoder = encoder;
        this.linkClickRepository = linkClickRepository;
    }

    //methods to implement
    //1create a short code for a given long url
    //2redirect to original url when short code is accessed
    //3get analytics for a short code
    //implementing the methods now

    @Transactional
    public LinkResponse createShortUrl(CreateLinkRequest request) {
        urlValidationService.validateOriginalUrl(request.getOriginalUrl());

        Link link = new Link();
        link.setOriginalUrl(request.getOriginalUrl());

        if (request.getExpiresInDays() != null) {
            link.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        String shortCode;
        link.setShortCode("TEMP");  // temporary placeholder to satisfy DB constraints
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            shortCode = request.getCustomAlias();
            urlValidationService.validateCustomAlias(shortCode);

            if (linkRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyInUseException(shortCode);
            }

            link.setShortCode(shortCode);
            linkRepository.save(link);

        } else {
            link = linkRepository.save(link);            // save to get DB ID
            shortCode = encoder.encode(link.getId());    // encode the ID
            link.setShortCode(shortCode);
            linkRepository.save(link);                   // update with shortCode
        }

        LinkResponse response = new LinkResponse();
        response.setShortCode(shortCode);
        response.setShortUrl(baseUrl + "/" + shortCode);
        response.setOriginalUrl(link.getOriginalUrl());
        response.setCreatedAt(link.getCreatedAt());
        response.setExpiresAt(link.getExpiresAt());

        return response;
    }


    public String redirectToOriginalUrl(String shortCode, HttpServletRequest httpRequest) {
        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ShortCodeExpiredException(shortCode);
        }

        // Log the click
        LinkClick click = new LinkClick();
        click.setLink(link);
        click.setClickedAt(LocalDateTime.now());
        click.setUserAgent(httpRequest.getHeader("User-Agent"));
        click.setReferrer(httpRequest.getHeader("Referer")); // HTTP spec typo, yes it's "Referer"
        click.setIpAddress(extractIp(httpRequest));
        linkClickRepository.save(click);

        return link.getOriginalUrl();
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP in chain is the client
        }
        return request.getRemoteAddr();
    }


    public LinkAnalyticsResponse getAnalytics(String shortCode) {
        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        List<LinkClick> clicks = linkClickRepository.findByLinkId(link.getId());

        LinkAnalyticsResponse response = new LinkAnalyticsResponse();
        response.setShortCode(link.getShortCode());
        response.setOriginalUrl(link.getOriginalUrl());
        response.setCreatedAt(link.getCreatedAt());
        response.setExpiresAt(link.getExpiresAt());
        response.setTotalClicks(clicks.size());
        response.setExpired(link.getExpiresAt() != null &&
                link.getExpiresAt().isBefore(LocalDateTime.now()));

        clicks.stream()
                .map(LinkClick::getClickedAt)
                .max(Comparator.naturalOrder())
                .ifPresent(response::setLastAccessed);

        return response;
    }
}
