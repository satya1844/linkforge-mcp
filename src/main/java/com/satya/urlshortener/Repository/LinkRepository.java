package com.satya.urlshortener.Repository;

import com.satya.urlshortener.Entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LinkRepository extends JpaRepository<Link, Long> {
    Optional<Link> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    @Query("SELECT l.shortCode FROM Link l WHERE l.deletedAt IS NULL")
    List<String> findAllActiveShortCodes();
    List<Link> findByExpiresAtBeforeAndGraceUntilIsNull(LocalDateTime now);
}
