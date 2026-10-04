package kg.chairx.audit;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {
    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public AuditService(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // Audit must roll back with the business operation; never commit it independently.
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, UUID entityId, String action, Object before, Object after) {
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        recordAs(actor, entityType, entityId, action, before, after);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(String actor, String entityType, UUID entityId, String action, Object before, Object after) {
        jdbc.sql("""
                INSERT INTO audit_entries
                    (id, entity_type, entity_id, action, actor, occurred_at, before_state, after_state)
                VALUES (:id, :type, :entity, :action, :actor, :time,
                    CAST(:before AS jsonb), CAST(:after AS jsonb))
                """)
                .param("id", UUID.randomUUID()).param("type", entityType).param("entity", entityId)
                .param("action", action).param("actor", actor)
                .param("time", OffsetDateTime.now(ZoneOffset.UTC))
                .param("before", before == null ? null : mapper.writeValueAsString(before), java.sql.Types.VARCHAR)
                .param("after", mapper.writeValueAsString(after)).update();
    }
}
