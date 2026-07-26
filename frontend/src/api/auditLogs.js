import { useQuery } from '@tanstack/react-query';
import client from './client';

export function useAuditLogs(filters = {}, page = 0, size = 20, options = {}) {
  return useQuery({
    queryKey: ['audit-logs', 'list', filters, page],
    queryFn: () => client.get('/api/audit-logs', { params: { page, size, ...filters } }),
    placeholderData: (prev) => prev,
    enabled: options.enabled ?? true,
  });
}

export function useAuditLog(id) {
  return useQuery({
    queryKey: ['audit-logs', 'detail', id],
    queryFn: () => client.get(`/api/audit-logs/${id}`),
    enabled: !!id,
  });
}
