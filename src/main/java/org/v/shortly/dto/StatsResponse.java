package org.v.shortly.dto;

import java.time.LocalDate;
import java.util.List;

public record StatsResponse(
        String shortCode, String shortUrl, String originalUrl,
        int days,                       // size of the window, e.g. 7
        long clicksInPeriod,            // clicks inside the window
        long uniqueVisitorsInPeriod,    // distinct visitor hashes inside the window
        long allTimeClicks,             // the lifetime counter from the urls table
        List<DayCount> clicksByDay,     // one entry per day, including days with 0 clicks
        List<NameCount> browsers, List<NameCount> devices, List<NameCount> referrers
) {
    public record DayCount(LocalDate date, long clicks) {}
    public record NameCount(String name, long clicks) {}
}