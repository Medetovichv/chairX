package kg.chairx.common.db;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Small global transactional fence between operational completion and daily close.
 * All paths acquire this BEFORE sale/delivery locks; P21 BANK -> CASH lock order is preserved.
 */
public final class CompletionGuard {
    private static final long ADVISORY_KEY = 772200921L;

    private CompletionGuard() {}

    public static void acquire(JdbcClient jdbc) {
        // PostgreSQL advisory_xact_lock is released automatically at commit/rollback.
        jdbc.sql("SELECT COUNT(*) FROM pg_advisory_xact_lock(" + ADVISORY_KEY + ")")
                .query(Long.class).single();
    }
}
