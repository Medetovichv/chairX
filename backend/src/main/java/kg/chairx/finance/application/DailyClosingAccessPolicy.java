package kg.chairx.finance.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Deterministic business-time policy for daily-closing edits and unlocks.
 *
 * Pure decision logic: callers still have to check normal RBAC permissions,
 * the matching closing row, revoked grants and transaction serialization.
 */
public final class DailyClosingAccessPolicy {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bishkek");
    public static final LocalTime NEXT_DAY_CUTOFF = LocalTime.of(13, 0);
    public static final int MANAGER_MAX_DAYS_AFTER = 5;
    public static final long UNLOCK_SECONDS = 3_600;

    private final Clock clock;

    public DailyClosingAccessPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return today(now());
    }

    public LocalDate today(Instant at) {
        return Objects.requireNonNull(at, "at")
                .atZone(BUSINESS_ZONE).toLocalDate();
    }

    /** Today, or yesterday until (but not including) 13:00 Bishkek. */
    public boolean normalWriteWindow(LocalDate businessDate, Instant at) {
        Objects.requireNonNull(businessDate, "businessDate");
        LocalDate today = today(at);
        return businessDate.equals(today)
                || (businessDate.equals(today.minusDays(1))
                    && at.atZone(BUSINESS_ZONE).toLocalTime().isBefore(NEXT_DAY_CUTOFF));
    }

    /** Manager may authorize a single date until day six starts in Bishkek. */
    public boolean managerMayUnlock(LocalDate businessDate, Instant at) {
        Objects.requireNonNull(businessDate, "businessDate");
        LocalDate today = today(at);
        return !businessDate.isAfter(today)
                && !businessDate.isBefore(today.minusDays(MANAGER_MAX_DAYS_AFTER));
    }

    /** Admin has no age cutoff but cannot unlock a future report. */
    public boolean adminMayUnlock(LocalDate businessDate, Instant at) {
        return !Objects.requireNonNull(businessDate, "businessDate").isAfter(today(at));
    }

    /**
     * Expiry is computed on the server. A manager grant is capped at the
     * beginning of day six, even when fewer than 60 minutes have elapsed.
     */
    public Instant expiry(LocalDate businessDate, Instant grantedAt, boolean byAdmin) {
        Objects.requireNonNull(businessDate, "businessDate");
        Objects.requireNonNull(grantedAt, "grantedAt");
        if (!(byAdmin ? adminMayUnlock(businessDate, grantedAt)
                     : managerMayUnlock(businessDate, grantedAt))) {
            throw new IllegalArgumentException("Unlock is outside the permitted date range");
        }
        Instant expires = grantedAt.plusSeconds(UNLOCK_SECONDS);
        if (!byAdmin) {
            Instant managerCutoff = businessDate.plusDays(MANAGER_MAX_DAYS_AFTER + 1L)
                    .atStartOfDay(BUSINESS_ZONE).toInstant();
            if (expires.isAfter(managerCutoff)) {
                expires = managerCutoff;
            }
        }
        return expires;
    }

    /** Checks a persisted grant; equality with expiresAt is already expired. */
    public boolean grantActive(Instant at, Instant expiresAt, Instant revokedAt) {
        return revokedAt == null && Objects.requireNonNull(at, "at")
                .isBefore(Objects.requireNonNull(expiresAt, "expiresAt"));
    }
}
