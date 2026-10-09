package kg.chairx.security.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SecurityBootstrapRepository {

    private final JdbcTemplate jdbc;

    public SecurityBootstrapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Блокирует строку состояния bootstrap до завершения транзакции.
     * Защищает от одновременной инициализации несколькими экземплярами ChairX.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndCheckInitialized() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("""
                        SELECT initialized
                        FROM security_bootstrap_state
                        WHERE id = 1
                        FOR UPDATE
                        """, Boolean.class)
        );
    }

    /**
     * Отмечает первоначальную инициализацию завершённой.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markInitialized() {
        int updated = jdbc.update("""
                UPDATE security_bootstrap_state
                SET initialized = TRUE,
                    initialized_at = CURRENT_TIMESTAMP
                WHERE id = 1
                  AND initialized = FALSE
                """);

        if (updated != 1) {
            throw new IllegalStateException(
                    "Bootstrap state could not be initialized"
            );
        }
    }
}