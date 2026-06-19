package com.satya.urlshortener.Service;

import org.springframework.stereotype.Service;
import com.satya.urlshortener.Exception.InvalidAliasException;
import com.satya.urlshortener.Exception.InvalidUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

@Service
public class UrlValidationService {

    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api", "admin", "health", "swagger", "actuator",
            "login", "logout", "register", "static", "assets"
    );

    private static final List<String> BLOCKED_HOSTS = List.of(
            "localhost", "127.0.0.1", "0.0.0.0",
            "169.254.169.254" // AWS metadata
    );

    public void validateOriginalUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host == null) {
                throw new InvalidUrlException("URL has no valid host");
            }

            BLOCKED_HOSTS.forEach(blocked -> {
                if (host.equalsIgnoreCase(blocked) || host.startsWith("192.168.")
                        || host.startsWith("10.") || host.matches("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
                    throw new InvalidUrlException("URL points to a private or reserved address");
                }
            });

        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Malformed URL: " + e.getMessage());
        }
    }

    public void validateCustomAlias(String alias) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new InvalidAliasException("'" + alias + "' is a reserved word");
        }
    }
}