package org.v.shortly.util;

import java.net.URI;
import java.util.Locale;

public final class ClickClassifier {
    private ClickClassifier() {}                                   // utility class: nobody creates an instance

    public static String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase(Locale.ROOT);            // compare in lowercase; ROOT avoids locale quirks
        if (isTool(ua)) return "Bot/Tool";
        // ORDER MATTERS: user-agents lie. Edge's string also says "Chrome" and "Safari", and Chrome's
        // also says "Safari". So we test the most specific browser first.
        if (ua.contains("edg/") || ua.contains("edgios")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("firefox/") || ua.contains("fxios")) return "Firefox";
        if (ua.contains("chrome/") || ua.contains("crios")) return "Chrome";
        if (ua.contains("safari/")) return "Safari";               // only real Safari is left by now
        return "Other";
    }

    public static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown";
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (isTool(ua)) return "Other";
        if (ua.contains("ipad") || ua.contains("tablet")
                || (ua.contains("android") && !ua.contains("mobile"))) return "Tablet";   // Android phones say "Mobile", tablets don't
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) return "Mobile";
        return "Desktop";
    }

    // We keep only the HOST of the referrer ("news.ycombinator.com"), never the full URL.
    // Full URLs can contain private tokens and make grouping impossible (every URL is unique).
    public static String referrerHost(String referer) {
        if (referer == null || referer.isBlank()) return "direct";     // typed in the address bar, or the site hid it
        try {
            String host = URI.create(referer.trim()).getHost();        // parse the URL and take its host
            if (host == null || host.isBlank()) return "unknown";
            host = host.toLowerCase(Locale.ROOT);
            return host.length() > 255 ? host.substring(0, 255) : host;   // never exceed the column size
        } catch (IllegalArgumentException e) {
            return "unknown";                                          // malformed header: don't crash, don't trust
        }
    }

    private static boolean isTool(String ua) {
        return ua.contains("bot") || ua.contains("spider") || ua.contains("crawl") || ua.contains("curl/")
                || ua.contains("wget") || ua.contains("postman") || ua.contains("intellij");
    }
}