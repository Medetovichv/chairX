package kg.chairx.finance;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

/**
 * Only legacy company-closing integration fixtures use this deliberately
 * narrow additional grant. Production EMPLOYEE never receives FINANCE_READ.
 * No shared system role or migration is changed by the test.
 */
final class CorporateClosingTestGrant {
    private CorporateClosingTestGrant() {}

    static UUID grant(JdbcTemplate jdbc, UUID employeeId) {
        UUID role=UUID.randomUUID();
        String code="P21_FINANCE_VIEW_"+role.toString().replace("-","");
        jdbc.update("INSERT INTO security_roles(id,code,name,system_role) VALUES (?,?,?,FALSE)",
                role,code,"Test-only company read");
        jdbc.update("INSERT INTO security_role_permissions(role_id,permission_code) VALUES (?,'FINANCE_READ')",role);
        jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES (?,?)",employeeId,role);
        return role;
    }

    static void revoke(JdbcTemplate jdbc, UUID role) {
        jdbc.update("DELETE FROM security_user_roles WHERE role_id=?",role);
        jdbc.update("DELETE FROM security_role_permissions WHERE role_id=?",role);
        jdbc.update("DELETE FROM security_roles WHERE id=?",role);
    }
}
