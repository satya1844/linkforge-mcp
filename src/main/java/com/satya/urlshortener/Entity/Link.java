package com.satya.urlshortener.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", unique = true, nullable = false, length = 20)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "custom_alias", nullable = false)
    private Boolean customAlias = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime expiresAt;
    private LocalDateTime deletedAt;

    @Column(name = "grace_until")
    private LocalDateTime graceUntil;

    @Column(name = "grace_period_hours")
    private Integer gracePeriodHours = 24;

    @OneToMany(mappedBy = "link", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LinkClick> clicks;
}