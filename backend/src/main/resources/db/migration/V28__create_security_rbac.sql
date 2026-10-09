-- Employees and authentication
CREATE TABLE app_users (
                           id UUID PRIMARY KEY,
                           username VARCHAR(100) NOT NULL UNIQUE,
                           password_hash VARCHAR(255) NOT NULL,
                           display_name VARCHAR(200) NOT NULL,
                           active BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Roles
CREATE TABLE security_roles (
                                id UUID PRIMARY KEY,
                                code VARCHAR(100) NOT NULL UNIQUE,
                                name VARCHAR(200) NOT NULL,
                                system_role BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Permissions
CREATE TABLE security_permissions (
                                      code VARCHAR(100) PRIMARY KEY,
                                      description VARCHAR(255) NOT NULL
);

-- User-to-role assignments
CREATE TABLE security_user_roles (
                                     user_id UUID NOT NULL REFERENCES app_users(id),
                                     role_id UUID NOT NULL REFERENCES security_roles(id),
                                     PRIMARY KEY (user_id, role_id)
);

-- Role-to-permission assignments
CREATE TABLE security_role_permissions (
                                           role_id UUID NOT NULL REFERENCES security_roles(id),
                                           permission_code VARCHAR(100) NOT NULL
                                               REFERENCES security_permissions(code),
                                           PRIMARY KEY (role_id, permission_code)
);

-- Security audit history
CREATE TABLE security_audit_log (
                                    id UUID PRIMARY KEY,
                                    actor_user_id UUID REFERENCES app_users(id),
                                    action VARCHAR(100) NOT NULL,
                                    target_type VARCHAR(100) NOT NULL,
                                    target_id UUID,
                                    details TEXT,
                                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_user_roles_role
    ON security_user_roles(role_id);

CREATE INDEX idx_security_role_permissions_permission
    ON security_role_permissions(permission_code);

CREATE INDEX idx_security_audit_created_at
    ON security_audit_log(created_at);