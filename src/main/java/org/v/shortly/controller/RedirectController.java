package org.v.shortly.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.v.shortly.service.ClickRecorder;
import org.v.shortly.service.UrlService;
import java.net.URI;

@RestController
public class RedirectController {

    private final UrlService urlService;
    private final ClickRecorder clickRecorder;

    public RedirectController(UrlService urlService, ClickRecorder clickRecorder) {
        this.urlService = urlService;
        this.clickRecorder = clickRecorder;
    }

    @GetMapping("/{code:[0-9A-Za-z_-]{1,10}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        String target = urlService.resolve(code);          // throws 404/410 if missing or expired, so those are NOT counted as clicks
        clickRecorder.record(code,
                request.getRemoteAddr(),                   // client IP (only hashed later, never stored)
                request.getHeader("User-Agent"),           // browser/device info
                request.getHeader("Referer"));             // the page the user came from (spelled like this on purpose)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}