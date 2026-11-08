package org.v.shortly.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.v.shortly.dto.ShortenRequest;
import org.v.shortly.dto.ShortenResponse;
import org.v.shortly.service.UrlService;

@RestController                                   // this class handles web requests and returns JSON (not HTML pages)
@RequestMapping("/api/v1/urls")                   // every method's path starts with this; v1 = API version
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) { // Spring injects the service
        this.urlService = urlService;
    }

    @PostMapping                                  // handles POST /api/v1/urls
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        // @RequestBody: convert the JSON body into a ShortenRequest object
        // @Valid: run the validation annotations; if they fail, Spring throws before our code runs
        ShortenResponse response = urlService.shorten(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
        // 201 Created is the correct status for "a new resource was made" (not 200)
    }
}