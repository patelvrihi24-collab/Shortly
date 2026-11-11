package org.v.shortly.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.v.shortly.dto.StatsResponse;
import org.v.shortly.entity.Url;
import org.v.shortly.exception.NotFoundException;
import org.v.shortly.repository.ClickStatsRepository;
import org.v.shortly.repository.UrlRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private static final int MAX_DAYS = 90;      // cap the window so ?days=100000 can't scan the whole table

    private final UrlRepository urlRepository;
    private final ClickStatsRepository statsRepository;
    private final String baseUrl;

    public StatsService(UrlRepository urlRepository, ClickStatsRepository statsRepository,
                        @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.statsRepository = statsRepository;
        this.baseUrl = baseUrl;
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats(String shortCode, Long userId, int days) {
        int window = Math.min(Math.max(days, 1), MAX_DAYS);        // clamp to 1..90

        Url url = urlRepository.findByShortCodeAndUserId(shortCode, userId)    // ownership check inside the query
                .orElseThrow(() -> new NotFoundException("Short link not found: " + shortCode));
        // Someone else's link and a missing link both give 404, same as delete in Phase 3.

        LocalDate today = LocalDate.now();                          // UTC, because main() sets the JVM time zone
        LocalDate firstDay = today.minusDays(window - 1L);          // 7 days = today and the 6 days before it
        LocalDateTime since = firstDay.atStartOfDay();              // midnight at the start of the first day

        ClickStatsRepository.Totals totals = statsRepository.totals(url.getId(), since);

        // The database only returns days that HAD clicks. Charts need every day, so we fill the gaps with 0.
        Map<LocalDate, Long> found = new HashMap<>();
        for (StatsResponse.DayCount d : statsRepository.clicksPerDay(url.getId(), since)) {
            found.put(d.date(), d.clicks());
        }
        List<StatsResponse.DayCount> series = new ArrayList<>(window);
        for (LocalDate d = firstDay; !d.isAfter(today); d = d.plusDays(1)) {
            series.add(new StatsResponse.DayCount(d, found.getOrDefault(d, 0L)));
        }

        return new StatsResponse(
                url.getShortCode(), baseUrl + "/" + url.getShortCode(), url.getOriginalUrl(),
                window, totals.clicks(), totals.uniqueVisitors(), url.getClickCount(),
                series,
                statsRepository.topBrowsers(url.getId(), since),
                statsRepository.topDevices(url.getId(), since),
                statsRepository.topReferrers(url.getId(), since));
    }
}