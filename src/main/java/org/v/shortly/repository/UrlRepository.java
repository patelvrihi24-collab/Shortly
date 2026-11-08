package org.v.shortly.repository;

import org.springframework.data.jpa.repository.JpaRepository;  // gives ready-made save/find/delete methods
import org.v.shortly.entity.Url;
import java.util.Optional;                                      // a box that may or may not hold a value

public interface UrlRepository extends JpaRepository<Url, Long> {
    // <Url, Long> = "manage Url entities whose primary key type is Long"

    Optional<Url> findByShortCode(String shortCode);
    // Spring writes the SQL from the method name:
    // SELECT * FROM urls WHERE short_code = ?
    // Optional forces callers to handle "not found" instead of a surprise null.
}