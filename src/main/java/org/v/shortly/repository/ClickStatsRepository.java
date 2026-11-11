package org.v.shortly.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.v.shortly.dto.StatsResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ClickStatsRepository {

    public record Totals(long clicks, long uniqueVisitors) {}

    private final JdbcTemplate jdbc;

    public ClickStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(long urlId, LocalDateTime since) {
        return jdbc.queryForObject(
                "SELECT COUNT(*), COUNT(DISTINCT visitor_hash) FROM click_events WHERE url_id = ? AND clicked_at >= ?",
                (rs, rowNum) -> new Totals(rs.getLong(1), rs.getLong(2)),   // turn the single result row into a Totals
                urlId, since);                                              // values for the two ? placeholders
    }

    public List<StatsResponse.DayCount> clicksPerDay(long urlId, LocalDateTime since) {
        return jdbc.query(
                "SELECT CAST(clicked_at AS DATE) AS day, COUNT(*) FROM click_events "
                        + "WHERE url_id = ? AND clicked_at >= ? GROUP BY 1 ORDER BY 1",   // GROUP BY 1 = group by the first selected column
                (rs, rowNum) -> new StatsResponse.DayCount(rs.getObject(1, LocalDate.class), rs.getLong(2)),
                urlId, since);
    }

    public List<StatsResponse.NameCount> topBrowsers(long urlId, LocalDateTime since)   { return top("browser", urlId, since); }
    public List<StatsResponse.NameCount> topDevices(long urlId, LocalDateTime since)    { return top("device", urlId, since); }
    public List<StatsResponse.NameCount> topReferrers(long urlId, LocalDateTime since)  { return top("referrer_host", urlId, since); }

    // Column NAMES can't be passed as ? parameters (only values can), so we join the name into the SQL text.
    // That is safe ONLY because every caller above passes a hard-coded literal. NEVER do this with user input.
    private List<StatsResponse.NameCount> top(String column, long urlId, LocalDateTime since) {
        return jdbc.query(
                "SELECT " + column + ", COUNT(*) FROM click_events WHERE url_id = ? AND clicked_at >= ? "
                        + "GROUP BY " + column + " ORDER BY COUNT(*) DESC, " + column + " LIMIT 5",   // top 5, ties broken alphabetically
                (rs, rowNum) -> new StatsResponse.NameCount(rs.getString(1), rs.getLong(2)),
                urlId, since);
    }
}