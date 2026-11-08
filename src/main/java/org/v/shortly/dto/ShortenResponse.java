package org.v.shortly.dto;
import java.time.LocalDateTime;
public record ShortenResponse(
        String shortCode,  //eg. 1B
        String shortUrl,   //full link e.g "http://localhost:8080/1B"
        String originalUrl,// what user provided
        LocalDateTime createdAt // when it was created
){}