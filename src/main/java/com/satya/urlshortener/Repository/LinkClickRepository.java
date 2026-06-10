package com.satya.urlshortener.Repository;

import com.satya.urlshortener.Entity.LinkClick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkClickRepository extends JpaRepository<LinkClick, Long> {
    List<LinkClick> findByLinkId(Long linkId);
    long countByLinkId(Long linkId);
    Optional<LinkClick> findTopByLinkIdOrderByClickedAtDesc(Long linkId);

}
