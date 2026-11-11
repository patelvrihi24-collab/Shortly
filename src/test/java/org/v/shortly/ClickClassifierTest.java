package org.v.shortly;

import org.junit.jupiter.api.Test;
import org.v.shortly.util.ClickClassifier;
import static org.junit.jupiter.api.Assertions.*;

class ClickClassifierTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String EDGE_WINDOWS = CHROME_WINDOWS + " Edg/120.0.0.0";
    // Edge's string ALSO contains "Chrome" and "Safari". This is the trap the check order must survive.
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_TABLET =
            "Mozilla/5.0 (Linux; Android 13; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Test
    void detectsChromeOnDesktop() {
        assertEquals("Chrome", ClickClassifier.browser(CHROME_WINDOWS));
        assertEquals("Desktop", ClickClassifier.device(CHROME_WINDOWS));
    }

    @Test
    void edgeIsNotMistakenForChrome() {
        assertEquals("Edge", ClickClassifier.browser(EDGE_WINDOWS));
    }

    @Test
    void detectsSafariOnPhone() {
        assertEquals("Safari", ClickClassifier.browser(IPHONE_SAFARI));
        assertEquals("Mobile", ClickClassifier.device(IPHONE_SAFARI));
    }

    @Test
    void androidWithoutMobileKeywordIsATablet() {
        assertEquals("Tablet", ClickClassifier.device(ANDROID_TABLET));   // Android phones say "Mobile", tablets don't
    }

    @Test
    void toolsAndMissingHeadersAreHandled() {
        assertEquals("Bot/Tool", ClickClassifier.browser("curl/8.4.0"));
        assertEquals("Unknown", ClickClassifier.browser(null));
        assertEquals("Unknown", ClickClassifier.device(null));
    }

    @Test
    void referrerKeepsOnlyTheLowercaseHost() {
        assertEquals("news.ycombinator.com", ClickClassifier.referrerHost("https://News.YCombinator.com/item?id=1"));
        assertEquals("direct", ClickClassifier.referrerHost(null));
        assertEquals("direct", ClickClassifier.referrerHost("  "));
    }

    @Test
    void malformedReferrerDoesNotCrash() {
        assertEquals("unknown", ClickClassifier.referrerHost("not a url"));   // a space makes URI.create throw
        assertEquals("unknown", ClickClassifier.referrerHost("garbage"));     // parses, but has no host
    }
}