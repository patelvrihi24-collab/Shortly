package org.v.shortly.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.v.shortly.dto.PagedResponse;
import org.v.shortly.dto.ShortenRequest;
import org.v.shortly.dto.ShortenResponse;
import org.v.shortly.dto.UrlSummaryResponse;
import org.v.shortly.security.AuthenticatedUser;
import org.v.shortly.service.UrlService;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        // @AuthenticationPrincipal injects the logged-in user. For anonymous callers the types don't match,
        // so Spring passes null instead.
        Long userId = (user != null) ? user.id() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(urlService.shorten(request, userId));
    }

    @GetMapping                                                  // GET /api/v1/urls?page=0&size=20
    public PagedResponse<UrlSummaryResponse> myLinks(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return urlService.listForUser(user.id(), page, size);    // user is never null here: security requires a login
    }

    @DeleteMapping("/{code}")                                    // DELETE /api/v1/urls/github-me
    public ResponseEntity<Void> delete(@PathVariable String code,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        urlService.delete(code, user.id());                      // the service enforces ownership
        return ResponseEntity.noContent().build();               // 204: success, nothing to return
    }
}