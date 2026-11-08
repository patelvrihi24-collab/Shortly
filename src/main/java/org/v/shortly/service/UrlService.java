package org.v.shortly.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.v.shortly.dto.ShortenRequest;
import org.v.shortly.dto.ShortenResponse;
import org.v.shortly.entity.Url;
import org.v.shortly.exception.ExpiredLinkException;
import org.v.shortly.exception.NotFoundException;
import org.v.shortly.repository.UrlRepository;
import org.v.shortly.Base62;
import java.time.LocalDateTime;
import org.v.shortly.dto.ShortenResponse;
import org.v.shortly.dto.ShortenRequest;


@Service //tells Spring: create ONE instance of this class and manage it(a bean)
public class UrlService{

    private final UrlRepository urlRepository;
    private final String baseUrl;
    public UrlService(UrlRepository urlRepository, @Value("${app.base-url}") String baseUrl){
        // Constructor injection: Spring sees what the constructor needs and supplies it.
        // @Value reads app.base-url from application.yml.
        this.urlRepository = urlRepository;
        this.baseUrl = baseUrl;
    }
    @Transactional
    // Everything inside runs as ONE database transaction: all of it succeeds or all is rolled back.
    public ShortenResponse shorten(ShortenRequest request){
        Url url = urlRepository.save(new Url(request.url()));
        // INSERT a row. The database generates the id; with IDENTITY that INSERT runs
        // immediately, so url.getId() is already filled in after this line.

        url.setShortCode(Base62.encode(url.getId()));
        // Turn the id (e.g. 125) into a short code (e.g. "21"). No second save() call is needed:
        // the entity is "managed" inside this transaction, so Hibernate notices the change
        // (dirty checking) and runs the UPDATE automatically when the transaction commits.
        return toResponse(url);
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode){
        Url url = urlRepository.findByShortCode(shortCode).orElseThrow(() -> new NotFoundException("Short link not found: "+shortCode));
        // Optional: if empty, throw our exception (the handler turns it into a 404)
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredLinkException("This link has expired");   // 410 Gone
        }
        return url.getOriginalUrl();              // the controller will redirect here
    }
    private ShortenResponse toResponse(Url url){
        return new ShortenResponse(
                url.getShortCode(),
                baseUrl + "/" + url.getShortCode(),
                url.getOriginalUrl(),
                url.getCreatedAt()
        );
    }
}

