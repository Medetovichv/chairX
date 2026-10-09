package kg.chairx.security.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class SecurityAuditRepository {

    private final JdbcClient jdbc;

    public SecurityAuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String details
    ) {
        UUID auditId = UUID.randomUUID();

        jdbc.sql("""
                INSERT INTO security_audit_log (
                    id,
                    actor_user_id,
                    action,
                    target_type,
                    target_id,
                    details
                )
                VALUES (
                    :id,
                    :actorUserId,
                    :action,
                    :targetType,
                    :targetId,
                    :details
                )
                """)
                .param("id", auditId)
                .param("actorUserId", actorUserId)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("details", details)
                .update();

        return auditId;
    }
}