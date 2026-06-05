package com.satya.urlshortener.Repository;

import com.satya.urlshortener.Entity.LinkClick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkClickRepository extends JpaRepository<LinkClick, Long> {
    List<LinkClick> findByLinkId(Long linkId);
}
