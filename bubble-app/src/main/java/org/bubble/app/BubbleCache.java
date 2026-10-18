package org.bubble.app;

import java.util.List;

/**
 * What Bubble last read from the bubble, shared by all screens in this process: entering Apps or
 * Bubble shows it at once instead of connecting again. Fresh for 10 minutes; any change made
 * through Bubble clears it, so the next read is real.
 */
final class BubbleCache {

    static final long FRESH_MS = 10 * 60 * 1000;

    private static List<String[]> apps;
    private static long appsAt;
    private static Controller.BubbleReport report;
    private static long reportAt;

    private BubbleCache() {}

    static synchronized List<String[]> apps() {
        return apps != null && System.currentTimeMillis() - appsAt < FRESH_MS ? apps : null;
    }

    static synchronized void setApps(List<String[]> v) {
        apps = v;
        appsAt = System.currentTimeMillis();
    }

    static synchronized Controller.BubbleReport report() {
        return report != null && System.currentTimeMillis() - reportAt < FRESH_MS ? report : null;
    }

    static synchronized void setReport(Controller.BubbleReport v) {
        if (!v.connected) return;
        report = v;
        reportAt = System.currentTimeMillis();
    }

    static synchronized void clear() {
        apps = null;
        report = null;
    }
}
