package com.satya.urlshortener.Dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@Data
public class CreateLinkRequest {

    @NotBlank(message = "originalUrl is required")
    @Size(min = 10, max = 2048, message = "URL must be between 10 and 2048 characters")
    @Pattern(
            regexp = "^https?://.*",
            message = "URL must start with http:// or https://"
    )
    private String originalUrl;

    @Size(min = 3, max = 50, message = "Alias must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]$",
            message = "Alias can only contain letters, numbers, and hyphens"
    )
    private String customAlias; // nullable

    @Min(value = 0, message = "Expiry must be at least 0 days")
    @Max(value = 365, message = "Expiry cannot exceed 365 days")
    private Integer expiresInDays; // nullable
}