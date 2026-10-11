import { apiClient } from '../../shared/api/client';
import type { AdminUser, AdminRole, Permission, AuditPage, NewUser, NewRole, RoleUpdate } from './types';

const enc = encodeURIComponent;
export const adminKeys = {
  users: ['admin', 'users'] as const,
  user: (id: string) => ['admin', 'users', id] as const,
  roles: ['admin', 'roles'] as const,
  role: (id: string) => ['admin', 'roles', id] as const,
  permissions: ['admin', 'permissions'] as const,
  audit: (target: string, page: number) => ['admin', 'audit', target, page] as const,
  auditPrefix: ['admin', 'audit'] as const,
};
export const adminApi = {
  users: () => apiClient.get<AdminUser[]>('/api/admin/users'),
  user: (id: string) => apiClient.get<AdminUser>('/api/admin/users/' + enc(id)),
  createUser: (data: NewUser) => apiClient.post<AdminUser>('/api/admin/users', data),
  deactivate: (id: string) => apiClient.patch<void>('/api/admin/users/' + enc(id) + '/deactivate'),
  activate: (id: string) => apiClient.patch<void>('/api/admin/users/' + enc(id) + '/activate'),
  resetPassword: (id: string, newPassword: string) => apiClient.post<void>(
    '/api/admin/users/' + enc(id) + '/reset-password', { newPassword }),
  assignRole: (id: string, roleId: string) =>
    apiClient.post<void>('/api/admin/users/' + enc(id) + '/roles', { roleId }),
  removeRole: (id: string, roleId: string) =>
    apiClient.delete<void>('/api/admin/users/' + enc(id) + '/roles/' + enc(roleId)),
  roles: () => apiClient.get<AdminRole[]>('/api/admin/roles'),
  role: (id: string) => apiClient.get<AdminRole>('/api/admin/roles/' + enc(id)),
  createRole: (data: NewRole) => apiClient.post<AdminRole>('/api/admin/roles', data),
  updateRole: (id: string, data: RoleUpdate) => apiClient.put<AdminRole>('/api/admin/roles/' + enc(id), data),
  permissions: () => apiClient.get<Permission[]>('/api/admin/permissions'),
  audit: (target: string, page: number) => {
    const q = new URLSearchParams({page: String(page), size: '20'});
    if (target) q.set('targetId', target);
    return apiClient.get<AuditPage>('/api/admin/audit?' + q.toString());
  },
};
