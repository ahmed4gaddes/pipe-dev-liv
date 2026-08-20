import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client from './client';

export const ticketKeys = {
  list: (filters, page) => ['tickets', 'list', filters, page],
  detail: (id) => ['tickets', 'detail', id],
  history: (id) => ['tickets', 'history', id],
  stats: ['tickets', 'stats'],
};

export function useTickets(filters = {}, page = 0, size = 20) {
  return useQuery({
    queryKey: ticketKeys.list(filters, page),
    queryFn: () => client.get('/api/tickets', { params: { page, size, ...filters } }),
    placeholderData: (prev) => prev,
  });
}

export function useTicket(id) {
  return useQuery({
    queryKey: ticketKeys.detail(id),
    queryFn: () => client.get(`/api/tickets/${id}`),
    enabled: !!id,
  });
}

export function useTicketHistory(id) {
  return useQuery({
    queryKey: ticketKeys.history(id),
    queryFn: () => client.get(`/api/tickets/${id}/history`),
    enabled: !!id,
  });
}

export function useTicketStats() {
  return useQuery({
    queryKey: ticketKeys.stats,
    queryFn: () => client.get('/api/tickets/stats'),
  });
}

function useInvalidateTicket(id) {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['tickets'] });
    if (id) queryClient.invalidateQueries({ queryKey: ticketKeys.history(id) });
  };
}

export function useDeleteTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => client.delete(`/api/tickets/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tickets'] }),
  });
}

export function useCreateTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (dto) => client.post('/api/tickets', dto),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tickets'] }),
  });
}

export function useUpdateTicket(id) {
  const invalidate = useInvalidateTicket(id);
  return useMutation({
    mutationFn: (dto) => client.put(`/api/tickets/${id}`, dto),
    onSuccess: invalidate,
  });
}

export function useChangeStatus(id) {
  const invalidate = useInvalidateTicket(id);
  return useMutation({
    mutationFn: (dto) => client.patch(`/api/tickets/${id}/status`, dto),
    onSuccess: invalidate,
  });
}

export function useApproveTicket(id) {
  const invalidate = useInvalidateTicket(id);
  return useMutation({
    mutationFn: () => client.post(`/api/tickets/${id}/approve`),
    onSuccess: invalidate,
  });
}

export function useRejectTicket(id) {
  const invalidate = useInvalidateTicket(id);
  return useMutation({
    mutationFn: (comment) => client.post(`/api/tickets/${id}/reject`, comment ? { comment } : undefined),
    onSuccess: invalidate,
  });
}

export function useDeployTicket(id) {
  const invalidate = useInvalidateTicket(id);
  return useMutation({
    mutationFn: (env) => client.post(`/api/tickets/${id}/deploy/${env}`),
    onSuccess: invalidate,
  });
}

