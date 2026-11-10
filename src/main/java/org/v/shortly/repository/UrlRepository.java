package org.v.shortly.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;  // gives ready-made save/find/delete methods
import org.v.shortly.entity.Url;
import java.util.Optional; // a box that may or may not hold a value
import org.springframework.data.domain.Page;


public interface UrlRepository extends JpaRepository<Url, Long> {
    // <Url, Long> = "manage Url entities whose primary key type is Long"

    Optional<Url> findByShortCode(String shortCode);
    // Spring writes the SQL from the method name:
    // SELECT * FROM urls WHERE short_code = ?
    // Optional forces callers to handle "not found" instead of a surprise null.
    boolean existsByShortCode(String shortCode);
    // Spring builds: SELECT count(*) > 0 FROM urls WHERE short_code = ?
    // Cheaper than loading the entire row just to learn that it exists.
    Page<Url> findByUserId(Long userId, Pageable pageable);
    // WHERE user_id = ? plus LIMIT/OFFSET and a COUNT query, all generated from the method name + Pageable

    Optional<Url> findByShortCodeAndUserId(String shortCode, Long userId);
    // WHERE short_code = ? AND user_id = ?  -> the ownership check lives in the query itself
}