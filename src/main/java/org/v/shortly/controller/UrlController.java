package org.v.shortly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.v.shortly.dto.PagedResponse;
import org.v.shortly.dto.ShortenRequest;
import org.v.shortly.dto.ShortenResponse;
import org.v.shortly.dto.StatsResponse;
import org.v.shortly.dto.UrlSummaryResponse;
import org.v.shortly.security.AuthenticatedUser;
import org.v.shortly.service.StatsService;
import org.v.shortly.service.UrlService;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Links")                                          // groups these endpoints in Swagger UI
public class UrlController {

    private final UrlService urlService;
    private final StatsService statsService;

    public UrlController(UrlService urlService, StatsService statsService) {
        this.urlService = urlService;
        this.statsService = statsService;
    }

    @PostMapping
    @Operation(summary = "Create a short link",
            description = "Works anonymously (stricter rate limit) or with a token (link is owned by you and listed in 'My links').")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request,
                                                   @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        // hidden = true: the logged-in user is injected by Spring, so it must NOT appear as a field callers fill in
        Long userId = (user != null) ? user.id() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(urlService.shorten(request, userId));
    }

    @GetMapping
    @Operation(summary = "List my links (paginated, newest first)")
    @SecurityRequirement(name = "bearerAuth")                 // shows the padlock and sends the token from "Authorize"
    public PagedResponse<UrlSummaryResponse> myLinks(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return urlService.listForUser(user.id(), page, size);
    }

    @GetMapping("/{code}/stats")
    @Operation(summary = "Click statistics for one of my links", description = "days is clamped to 1-90.")
    @SecurityRequirement(name = "bearerAuth")
    public StatsResponse stats(@PathVariable String code,
                               @RequestParam(defaultValue = "7") int days,
                               @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return statsService.getStats(code, user.id(), days);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Delete one of my links", description = "Also evicts it from the Redis cache. Another user's link returns 404.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> delete(@PathVariable String code,
                                       @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        urlService.delete(code, user.id());
        return ResponseEntity.noContent().build();
    }
}