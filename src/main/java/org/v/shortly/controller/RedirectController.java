package org.v.shortly.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.v.shortly.service.UrlService;
import java.net.URI;

@RestController
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    @GetMapping("/{code:[0-9A-Za-z]{1,10}}")
    // {code:...} = a path variable restricted by a regex: only 1-10 letters/digits.
    // So /favicon.ico (has a dot) or /api/... never land here by accident.
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = urlService.resolve(code);          // look up the original URL
        return ResponseEntity.status(HttpStatus.FOUND)     // 302 = "temporary redirect"
                .location(URI.create(target))              // sets the Location header: where the browser should go
                .build();                                  // no response body needed
    }
}