package kg.chairx.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Issue #32 actual backup rehearsal runner. Never connects to a user's live DB.
 *
 * - Real PGDMP custom-format archive is restored inside a brand-new PostgreSQL 17
 *   Testcontainers instance, never over an existing database or Docker volume.
 * - Flyway migrates ONLY that restored database, with clean disabled.
 * - SHA-256 fingerprints of every row in every pre-existing public application
 *   table, using the ORIGINAL columns, must be unchanged.
 * - Explicitly allow *additive* permission/grant seeds from later migrations,
 *   never deletion or alteration of an existing grant.
 *
 * Full suite runs the same restore pipeline on an automatically generated V40
 * backup. A real backup is only consumed by the explicit operator-only test,
 * with an opt-in environment variable and absolutely no data in CI artifacts.
 */
class BackupUpgradeRehearsalTest {
    private static final String DUMP_ENV = "CHAIRX_REHEARSAL_DUMP";
    private static final Set<String> ADDITIVE_SEED_TABLES =
            Set.of("security_permissions", "security_role_permissions");

    private record Snapshot(List<String> columns, long rows, String fingerprint,
                            Set<String> originalRows) { }

    private record OriginalState(Map<String, Snapshot> tables,
                                 Map<String, String> flywayHistory,
                                 long legacyMovementCount, int startingVersion) { }

    @Test
    void syntheticV40DumpRoundTripVerifiesRealPgRestoreAndFlywayPath() throws Exception {
        Path temp = Files.createTempFile("chairx-issue32-v40-", ".dump");
        try {
            try (PostgreSQLContainer<?> source = freshContainer()) {
                source.start();
                Flyway.configure().dataSource(source.getJdbcUrl(), source.getUsername(),
                        source.getPassword()).target("40").load().migrate();
                seedRepresentativeV40Rows(source);
                var dumpResult = source.execInContainer("pg_dump", "-Fc", "--no-owner",
                        "--no-acl", "-U", source.getUsername(), "-d", source.getDatabaseName(),
                        "-f", "/tmp/chairx-v40.dump");
                assertThat(dumpResult.getExitCode()).as("V40 pg_dump exit status").isZero();
                source.copyFileFromContainer("/tmp/chairx-v40.dump", temp.toString());
            }
            verifyArchive(temp, true);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void operatorProvidedBackup() throws Exception {
        String file = System.getenv(DUMP_ENV);
        assumeTrue(file != null && !file.isBlank(),
                "Operator backup not provided; no real-data rehearsal was claimed");
        Path dump = Path.of(file).toAbsolutePath().normalize();
        verifyArchive(dump, false);
    }

    private static PostgreSQLContainer<?> freshContainer() {
        return new PostgreSQLContainer<>("postgres:17")
                .withDatabaseName("chairx_rehearsal")
                .withUsername("chairx_rehearsal")
                .withPassword("temporary-" + UUID.randomUUID());
    }

    private static void verifyArchive(Path dump, boolean synthetic) throws Exception {
        assertThat(dump).as("Readable PGDMP input").isRegularFile();
        assertThat(Files.size(dump)).as("Non-empty PGDMP input").isGreaterThan(5);
        try (var stream = Files.newInputStream(dump)) {
            assertThat(stream.readNBytes(5)).as("Must be a PostgreSQL -Fc archive")
                    .isEqualTo("PGDMP".getBytes(StandardCharsets.US_ASCII));
        }
        try (PostgreSQLContainer<?> restored = freshContainer()) {
            restored.start();
            try (Connection connection = connection(restored)) {
                assertThat(tableCount(connection)).as("Fresh isolated database").isZero();
            }
            // Testcontainers makes a PRIVATE copy. No credentials for, or network
            // connection to, the original database are used at any point.
            restored.copyFileToContainer(MountableFile.forHostPath(dump),
                    "/tmp/chairx-backup.dump");
            var restore = restored.execInContainer("pg_restore", "--exit-on-error",
                    "--single-transaction", "--no-owner", "--no-acl",
                    "-U", restored.getUsername(), "-d", restored.getDatabaseName(),
                    "/tmp/chairx-backup.dump");
            assertThat(restore.getExitCode())
                    .as("pg_restore into brand-new disposable database failed; "
                            + "check archive format/owner/dependency compatibility")
                    .isZero();

            OriginalState baseline;
            try (Connection db = connection(restored)) {
                // A missing Flyway history is not upgraded by baselineOnMigrate;
                // the operator MUST supply a real ChairX post-migration backup.
                baseline = captureBefore(db);
            }
            Flyway flyway = Flyway.configure()
                    .dataSource(restored.getJdbcUrl(), restored.getUsername(),
                            restored.getPassword())
                    .cleanDisabled(true)
                    .baselineOnMigrate(false)
                    .locations("classpath:db/migration").load();
            flyway.migrate();
            flyway.validate();
            try (Connection db = connection(restored)) {
                compareAfter(db, baseline);
            }
            var migrationCount = flyway.info().applied().length;
            System.out.printf(
                    "CHAIRX_UPGRADE_REHEARSAL=PASS source=%s "
                            + "preVersion=V%d appliedMigrations=%d "
                            + "comparedTables=%d originalLegacyMovements=%d "
                            + "noOriginalRowChanged=true nonProductionDatabase=true%n",
                    synthetic ? "SYNTHETIC_V40" : "OPERATOR_BACKUP",
                    baseline.startingVersion(), migrationCount,
                    baseline.tables().size(), baseline.legacyMovementCount());
        }
    }

    private static Connection connection(PostgreSQLContainer<?> container) throws Exception {
        return DriverManager.getConnection(container.getJdbcUrl(),
                container.getUsername(), container.getPassword());
    }

    private static long tableCount(Connection c) throws Exception {
        try (var ps = c.prepareStatement("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_type='BASE TABLE'
                """); var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static OriginalState captureBefore(Connection db) throws Exception {
        assertThat(tableCount(db)).as("Restored archive must contain ChairX tables")
                .isGreaterThan(20);
        Set<String> otherSchemas = new HashSet<>();
        try (var ps = db.prepareStatement("""
                SELECT schema_name FROM information_schema.schemata
                WHERE schema_name NOT IN ('public','information_schema')
                  AND schema_name NOT LIKE 'pg_%'
                """);var rs = ps.executeQuery()) {
            while (rs.next()) otherSchemas.add(rs.getString(1));
        }
        assertThat(otherSchemas)
                .as("Only standard public ChairX schema can be verified")
                .isEmpty();
        Map<String, String> history = migrationHistory(db);
        assertThat(history).as("ChairX Flyway history is required").isNotEmpty();
        int version = history.keySet().stream().mapToInt(Integer::parseInt)
                .max().orElseThrow();
        assertThat(version).as("Expected an existing V40-or-newer ChairX schema")
                .isGreaterThanOrEqualTo(40);
        assertThat(version).as("Backup cannot be ahead of this checked-out code")
                .isLessThanOrEqualTo(51);
        long movements = scalar(db, "SELECT count(*) FROM public.finance_movements");
        return new OriginalState(captureAllTables(db), history, movements, version);
    }

    private static Map<String, String> migrationHistory(Connection db) throws Exception {
        Map<String, String> history = new HashMap<>();
        try (var stmt = db.prepareStatement("""
                SELECT version,description,checksum,success
                FROM public.flyway_schema_history ORDER BY installed_rank
                """);var rs = stmt.executeQuery()) {
            while (rs.next()) {
                assertThat(rs.getBoolean(4)).as("Failed prior Flyway migration").isTrue();
                String version = rs.getString(1);
                if (version != null) {
                    assertThat(history.put(version, rs.getString(2) + "|" + rs.getString(3)))
                            .as("Duplicate Flyway version " + version).isNull();
                }
            }
        }
        return history;
    }

    private static Map<String, Snapshot> captureAllTables(Connection db) throws Exception {
        List<String> tables = new ArrayList<>();
        try (var ps = db.prepareStatement("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' AND table_type='BASE TABLE'
                  AND table_name<>'flyway_schema_history'
                ORDER BY table_name
                """);var rs=ps.executeQuery()) {
            while (rs.next()) tables.add(rs.getString(1));
        }
        Map<String, Snapshot> results = new HashMap<>();
        for (String table : tables) {
            List<String> columns = new ArrayList<>();
            try (var ps = db.prepareStatement("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema='public' AND table_name=?
                    ORDER BY ordinal_position
                    """)) {
                ps.setString(1, table);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) columns.add(rs.getString(1));
                }
            }
            assertThat(columns).as("No columns for " + table).isNotEmpty();
            results.put(table, capture(db, table, columns));
        }
        return results;
    }

    private static Snapshot capture(Connection db, String table,
                                    List<String> columns) throws Exception {
        String qualified = quote(table);
        String projection = String.join(",", columns.stream()
                .map(BackupUpgradeRehearsalTest::quote).toList());
        // PostgreSQL renders exactly the same original typed columns before
        // and after. New migration columns are deliberately excluded.
        String sql = "SELECT payload FROM (SELECT to_jsonb(row_data)::text AS payload "
                + "FROM (SELECT " + projection + " FROM public." + qualified
                + ") row_data) snapshot ORDER BY payload";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Set<String> additiveOriginals = ADDITIVE_SEED_TABLES.contains(table)
                ? new HashSet<>() : Set.of();
        long count = 0;
        boolean autocommit = db.getAutoCommit();
        db.setAutoCommit(false);
        try (PreparedStatement ps = db.prepareStatement(sql,
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(500);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String payload = rs.getString(1);
                    byte[] encoded = payload.getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(4).putInt(encoded.length).array());
                    digest.update(encoded);
                    if (ADDITIVE_SEED_TABLES.contains(table)) {
                        additiveOriginals.add(payload);
                    }
                    count++;
                }
            }
            db.commit();
        } finally {
            db.setAutoCommit(autocommit);
        }
        return new Snapshot(List.copyOf(columns), count,
                java.util.HexFormat.of().formatHex(digest.digest()), additiveOriginals);
    }

    private static void compareAfter(Connection db, OriginalState old) throws Exception {
        Map<String, String> current = migrationHistory(db);
        for (var e : old.flywayHistory().entrySet()) {
            assertThat(current.get(e.getKey()))
                    .as("Applied migration V" + e.getKey() + " was modified")
                    .isEqualTo(e.getValue());
        }
        int latest = current.keySet().stream().mapToInt(Integer::parseInt)
                .max().orElseThrow();
        assertThat(latest).as("Full pending migration set must apply").isEqualTo(51);

        Map<String, Snapshot> migrated = new HashMap<>();
        for (var e : old.tables().entrySet()) {
            String table = e.getKey();
            Snapshot before = e.getValue();
            Snapshot after = capture(db, table, before.columns());
            migrated.put(table, after);
            if (ADDITIVE_SEED_TABLES.contains(table)) {
                assertThat(after.rows()).as("Cannot delete " + table + " rows")
                        .isGreaterThanOrEqualTo(before.rows());
                assertThat(after.originalRows()).as("Old " + table + " grants mutated")
                        .containsAll(before.originalRows());
            } else {
                assertThat(after.rows()).as("Row count changed: " + table)
                        .isEqualTo(before.rows());
                assertThat(after.fingerprint()).as("Original data changed: " + table)
                        .isEqualTo(before.fingerprint());
            }
        }
        assertThat(migrated).containsKey("finance_accounts")
                .containsKey("finance_movements")
                .containsKey("finance_daily_closings")
                .containsKey("finance_daily_closing_accounts")
                .containsKey("security_user_roles");

        if (old.startingVersion() < 43) {
            assertThat(scalar(db, """
                    SELECT count(*) FROM finance_movements
                    WHERE business_date_source='LEGACY_INFERRED'
                      AND business_date = (created_at AT TIME ZONE 'Asia/Bishkek')::date
                    """)).as("Legacy date inference must preserve registration day")
                    .isEqualTo(old.legacyMovementCount());
        }
        assertThat(scalar(db, """
                SELECT count(*) FROM finance_daily_closings
                  c LEFT JOIN finance_daily_closing_sales_snapshots s
                  ON s.closing_id=c.id
                WHERE c.business_date < CURRENT_DATE AND s.closing_id IS NOT NULL
                """)).as("Historic snapshots must not be fabricated")
                .isZero();
        for (String account : List.of("CASH","BANK")) {
            // Do not silently repair an existing accounting discrepancy. The
            // signed account row AND every original movement remain bytewise
            // unchanged; human reconciliation still gates production signoff.
            BigDecimal accountBalance = amount(db,
                    "SELECT balance FROM finance_accounts WHERE code=?", account);
            BigDecimal journal = amount(db, """
                    SELECT COALESCE(sum(amount),0) FROM finance_movements
                    WHERE account_code=?
                    """, account);
            if (accountBalance.compareTo(journal)!=0) {
                System.out.println("CHAIRX_UPGRADE_REHEARSAL=NEEDS_RECONCILIATION"
                        + " account=" + account + " preexistingJournalDrift=true");
            }
        }
    }

    private static BigDecimal amount(Connection db, String sql, String account)
            throws Exception {
        try(var ps=db.prepareStatement(sql)) {
            ps.setString(1,account);
            try(var rs=ps.executeQuery()){rs.next();return rs.getBigDecimal(1);}
        }
    }

    private static long scalar(Connection db, String sql) throws Exception {
        try(var s=db.createStatement();var rs=s.executeQuery(sql)){
            rs.next();return rs.getLong(1);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void seedRepresentativeV40Rows(PostgreSQLContainer<?> source)
            throws Exception {
        UUID movement = UUID.randomUUID();
        UUID closing = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID supplier = UUID.randomUUID();
        UUID purchase = UUID.randomUUID();
        UUID purchaseItem = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        try (Connection c=connection(source)) {
            try(var st=c.createStatement()){
                st.executeUpdate("""
                        UPDATE finance_accounts SET balance=25000,
                          opening_balance_initialized=TRUE WHERE code='CASH'
                        """);
                st.executeUpdate("""
                        UPDATE finance_accounts SET balance=5000,
                          opening_balance_initialized=TRUE WHERE code='BANK'
                        """);
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO finance_movements
                      (id, account_code, amount, movement_type, source_type,
                       source_id, created_by, created_at)
                    VALUES (?, 'CASH',25000,'OPENING_BALANCE','OPENING_BALANCE',
                            ?,'issue32-fixture','2020-10-10 10:00+06')
                    """)){
                ps.setObject(1,movement);ps.setObject(2,UUID.randomUUID());ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO finance_movements
                      (id, account_code, amount, movement_type, source_type,
                       source_id, created_by, created_at)
                    VALUES (?, 'BANK',5000,'OPENING_BALANCE','OPENING_BALANCE',
                            ?,'issue32-fixture','2020-10-10 11:00+06')
                    """)){
                ps.setObject(1,UUID.randomUUID());ps.setObject(2,UUID.randomUUID());
                ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO finance_daily_closings (id,business_date,created_by)
                    VALUES (?,'2020-10-10','issue32-fixture')
                    """)){
                ps.setObject(1,closing);ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO finance_daily_closing_accounts
                      (closing_id, account_code,expected_balance,actual_balance,note)
                    VALUES (?,'CASH',25000,24000,'Investigate reported shortage'),
                           (?,'BANK',5000,5000,NULL)
                    """)){
                ps.setObject(1,closing);ps.setObject(2,closing);ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO products(id,name,description,category,active,
                                         created_at,updated_at)
                    VALUES(?,'ChairX upgrade chair','Only in disposable V40 data',
                           'Chair',TRUE,now(),now())
                    """)){ps.setObject(1,product);ps.executeUpdate();}
            try(var ps=c.prepareStatement("""
                    INSERT INTO product_variants(id,product_id,name,sku,color,
                       recommended_sale_price,active,created_at,updated_at)
                    VALUES (?,?,'Black','CHAIRX-ISSUE32','black',8500,TRUE,now(),now())
                    """)){
                ps.setObject(1,variant);ps.setObject(2,product);ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO suppliers(id,name,active,created_at,updated_at)
                    VALUES(?,'V40 backup supplier',TRUE,now(),now())
                    """)){ps.setObject(1,supplier);ps.executeUpdate();}
            try(var ps=c.prepareStatement("""
                    INSERT INTO purchases(id,supplier_id,status,cargo_cost,
                      cargo_allocation_method,comment,created_by,created_at,updated_at)
                    VALUES (?,?,'DRAFT',0,'BY_QUANTITY',NULL,'upgrade',now(),now())
                    """)){
                ps.setObject(1,purchase);ps.setObject(2,supplier);ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO purchase_items(id,purchase_id,product_variant_id,
                       line_number,ordered_quantity,received_quantity,purchase_unit_cost)
                    VALUES(?,?,?,1,6,0,5000)
                    """)){
                ps.setObject(1,purchaseItem);ps.setObject(2,purchase);
                ps.setObject(3,variant);ps.executeUpdate();
            }
            try(var ps=c.prepareStatement("""
                    INSERT INTO customers(id,full_name,phone,active,created_at,updated_at)
                    VALUES(?,'V40 customer','+996555000000',TRUE,now(),now())
                    """)){ps.setObject(1,customer);ps.executeUpdate();}
        }
    }
}
