package ch.sthomas.stddivelogger.analytics.job;

import org.jspecify.annotations.Nullable;

public enum JobKind {
    PROFILES("Profile analytics", "Depth segments, gas and profile calculations", "0 * * * * *", 0),
    SUMMARIES("Dive summaries", "Refresh computed dive summaries", "0 0 3 * * *", 10),
    ACTIVITY("Diver activity", "Refresh activity and trend statistics", "30 * * * * *", 15),
    REMINDERS("Reminders", "Recalculate due reminders", "0 */5 * * * *", 20),
    PUSH("Reminder delivery", "Send due push notifications to divers", "0 2/5 * * * *", 45),
    CLEANUP("Reminder cleanup", "Remove expired reminders", "0 30 3 * * *", 0),
    SITES("Dive site statistics", "Refresh shared site aggregates", "0 4/15 * * * *", 30),
    MAPS_IMPORT(
            "OSM boundary import",
            "Launch a country and regional-boundary import in Kubernetes",
            "0 0 2 * * SUN",
            0);

    public final String label;
    public final String description;
    public final @Nullable String cron;
    public final int startupDelaySeconds;

    JobKind(
            final String label,
            final String description,
            final @Nullable String cron,
            final int startupDelaySeconds) {
        this.label = label;
        this.description = description;
        this.cron = cron;
        this.startupDelaySeconds = startupDelaySeconds;
    }
}
