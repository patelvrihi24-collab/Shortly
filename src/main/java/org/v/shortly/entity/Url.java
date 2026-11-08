package org.v.shortly.entity;                        // must match the folder path exactly

import jakarta.persistence.*;                        // JPA annotations: Entity, Table, Id, Column...
import java.time.LocalDateTime;                      // date+time, maps to SQL TIMESTAMP

@Entity                                              // this class represents a database table
@Table(name = "urls")                                // ...the table named "urls" (made by Flyway)
public class Url {

    @Id                                              // this field is the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // the database auto-generates it (our BIGSERIAL)
    private Long id;                                 // Long (not long) so it can be null before saving

    @Column(name = "short_code", length = 10, unique = true)  // maps to column short_code
    private String shortCode;                        // nullable: filled in after we know the id

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;                      // required, any length

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();  // set automatically at creation

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;                 // null = never expires

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;                     // primitive long can never be null

    protected Url() {}                               // JPA needs an empty constructor to build objects from rows

    public Url(String originalUrl) {                 // the constructor our own code uses
        this.originalUrl = originalUrl;
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public String getOriginalUrl() { return originalUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public long getClickCount() { return clickCount; }
}