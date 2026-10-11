export interface AdminUser {
  id: string; username: string; displayName: string; active: boolean; roles: string[];
}
export interface AdminRole {
  id: string; code: string; name: string; systemRole: boolean;
  permissions: string[]; assignedUsersCount: number; version: number;
}
export interface Permission {
  code: string; description: string; group: string;
  sensitive: boolean; adminOnly: boolean;
}
export interface AuditLine {
  id: string; actorUserId: string; action: string;
  targetType: string; targetId: string; details: string | null; createdAt: string;
}
export interface AuditPage { items: AuditLine[]; page: number; size: number; total: number }
export interface NewUser { username: string; password: string; displayName: string }
export interface NewRole { code: string; name: string; permissions: string[] }
export interface RoleUpdate { name: string; permissions: string[]; expectedVersion: number; reason: string }
