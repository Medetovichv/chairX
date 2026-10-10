package kg.chairx.finance.application;

import kg.chairx.audit.AuditService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grants are attached to one report date, never to the financial system.
 * Unlock/revoke serialize through a date-specific database lock.
 */
@Service
public class DailyClosingAccessService {
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final DailyClosingAccessPolicy policy;

    public DailyClosingAccessService(JdbcClient jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.policy = new DailyClosingAccessPolicy(clock);
    }

    private static Authentication currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Требуется авторизованный сотрудник");
        }
        return auth;
    }

    private static boolean has(Authentication auth, String permission) {
        return auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }

    /**
     * Lock before reading/modifying a date's grant, even if the report has
     * not yet been created. Call from each report write transaction.
     */
    @Transactional
    public void lockDate(LocalDate date) {
        jdbc.sql("""
                INSERT INTO finance_daily_closing_access_locks(business_date)
                VALUES (:day) ON CONFLICT DO NOTHING
                """).param("day", date).update();
        jdbc.sql("""
                SELECT business_date FROM finance_daily_closing_access_locks
                WHERE business_date = :day FOR UPDATE
                """).param("day", date).query(LocalDate.class).single();
    }

    private UUID auditEntity(LocalDate date) {
        UUID existing = jdbc.sql("SELECT id FROM finance_daily_closings WHERE business_date = :day")
                .param("day", date).query(UUID.class).optional().orElse(null);
        return existing == null ? UUID.nameUUIDFromBytes(
                ("FINANCE_DAILY_CLOSING:" + date).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : existing;
    }

    private Grant activeGrant(LocalDate businessDate, Instant now) {
        List<Grant> grants = jdbc.sql("""
                SELECT id, business_date, unlocked_by, unlocked_at, expires_at,
                       reason, revoked_at, revoked_by
                FROM finance_daily_closing_unlocks
                WHERE business_date = :day
                  AND revoked_at IS NULL AND expires_at > :now
                ORDER BY unlocked_at DESC
                """)
                .param("day", businessDate).param("now", now)
                .query((rs, row) -> new Grant(
                        rs.getObject("id", UUID.class),
                        rs.getDate("business_date").toLocalDate(),
                        rs.getString("unlocked_by"),
                        rs.getTimestamp("unlocked_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("reason"),
                        rs.getTimestamp("revoked_at") == null ? null
                                : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getString("revoked_by")))
                .list();
        return grants.stream().filter(g -> policy.grantActive(now, g.expiresAt(), g.revokedAt()))
                .findFirst().orElse(null);
    }

    /**
     * This is a read-only hint for a UI. Every mutating operation MUST check
     * assertCanEdit inside its write transaction, after acquiring report locks.
     */
    @Transactional(readOnly = true)
    public AccessState get(LocalDate date) {
        Authentication auth = currentUser();
        Instant now = policy.now();
        UUID id = jdbc.sql("SELECT id FROM finance_daily_closings WHERE business_date = :day")
                .param("day", date).query(UUID.class).optional().orElse(null);
        Grant grant = activeGrant(date, now);
        boolean normal = policy.normalWriteWindow(date, now);
        boolean writable = has(auth, "DAILY_CLOSING_WRITE");
        boolean mayUnlock = has(auth, "DAILY_CLOSING_UNLOCK_ADMIN") && policy.adminMayUnlock(date, now)
                || has(auth, "DAILY_CLOSING_UNLOCK_MANAGER") && policy.managerMayUnlock(date, now);
        String status = normal ? "OPEN"
                : grant != null ? "TEMPORARILY_UNLOCKED" : "LOCKED";
        return new AccessState(date, id != null, id == null ? "NOT_CREATED" : "CLOSED",
                status, writable && (normal || grant != null), mayUnlock,
                grant == null ? null : grant.expiresAt(),
                grant == null ? 0 : Math.max(0, grant.expiresAt().getEpochSecond() - now.getEpochSecond()));
    }

    /**
     * A normal permission must still be granted by SecurityConfig/RBAC.
     * Call only after serializing on the target report row in a transaction.
     */
    @Transactional
    public void assertCanEdit(LocalDate date) {
        Authentication auth = currentUser();
        if (!has(auth, "DAILY_CLOSING_WRITE")) {
            throw new AccessDeniedException("Недостаточно прав на изменение отчёта");
        }
        Instant now = policy.now();
        if (policy.normalWriteWindow(date, now)) {
            return;
        }
        if (activeGrant(date, now) == null) {
            throw new AccessDeniedException("REPORT_LOCKED: обратитесь к менеджеру");
        }
    }

    @Transactional
    public Grant unlock(LocalDate date, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new FinanceValidationException("Причина разблокировки обязательна (до 2000 символов)");
        }
        Authentication auth = currentUser();
        lockDate(date); // also permits unlocking not-yet-created reports
        Instant now = policy.now();            // recheck date after waiting on lock
        boolean admin = has(auth, "DAILY_CLOSING_UNLOCK_ADMIN");
        if (!admin && !has(auth, "DAILY_CLOSING_UNLOCK_MANAGER")) {
            throw new AccessDeniedException("Недостаточно прав на разблокировку");
        }
        if (!(admin ? policy.adminMayUnlock(date, now) : policy.managerMayUnlock(date, now))) {
            throw new AccessDeniedException("UNLOCK_NOT_ALLOWED: срок менеджера истёк");
        }
        Grant prior = activeGrant(date, now);
        if (prior != null) {
            return prior; // a retry NEVER extends an active unlock
        }
        Instant until = policy.expiry(date, now, admin);
        Grant created = new Grant(UUID.randomUUID(), date, auth.getName(), now,
                until, reason.trim(), null, null);
        jdbc.sql("""
                INSERT INTO finance_daily_closing_unlocks
                  (id, business_date, unlocked_by, unlocked_at, expires_at, reason)
                VALUES (:id, :day, :actor, :at, :until, :reason)
                """)
                .param("id", created.id()).param("day", date)
                .param("actor", created.unlockedBy()).param("at", created.unlockedAt())
                .param("until", created.expiresAt()).param("reason", created.reason()).update();
        audit.recordAs(auth.getName(), "FINANCE_DAILY_CLOSING", auditEntity(date),
                "REPORT_UNLOCKED", null, Map.of("businessDate", date.toString(),
                        "grantId", created.id().toString(), "expiresAt", until.toString(),
                        "reason", created.reason()));
        return created;
    }

    /** ADMIN may revoke early; expiration itself needs no scheduled task. */
    @Transactional
    public boolean revoke(LocalDate date) {
        Authentication auth = currentUser();
        if (!has(auth, "DAILY_CLOSING_UNLOCK_ADMIN")) {
            throw new AccessDeniedException("Только администратор может отозвать разблокировку");
        }
        lockDate(date);
        Instant now = policy.now();
        Grant existing = activeGrant(date, now);
        if (existing == null) {
            return false;
        }
        jdbc.sql("""
                UPDATE finance_daily_closing_unlocks SET revoked_at = :at, revoked_by = :actor
                WHERE id = :id AND revoked_at IS NULL
                """).param("id", existing.id()).param("at", now)
                .param("actor", auth.getName()).update();
        audit.recordAs(auth.getName(), "FINANCE_DAILY_CLOSING", auditEntity(date),
                "REPORT_RELOCKED", Map.of("grantId", existing.id().toString()),
                Map.of("businessDate", date.toString(), "revokedAt", now.toString()));
        return true;
    }

    public record Grant(UUID id, LocalDate businessDate, String unlockedBy, Instant unlockedAt,
                        Instant expiresAt, String reason, Instant revokedAt, String revokedBy) {}

    public record AccessState(LocalDate businessDate, boolean reportExists,
                              String reportStatus, String accessStatus,
                              boolean canEdit, boolean canUnlock,
                              Instant unlockedUntil, long remainingSeconds) {}
}
