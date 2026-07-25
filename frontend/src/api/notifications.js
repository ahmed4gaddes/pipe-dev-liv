import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client from './client';

export function useNotifications(unreadOnly = false, page = 0, size = 20) {
  return useQuery({
    queryKey: ['notifications', 'list', unreadOnly, page],
    queryFn: () => client.get('/api/notifications', { params: { unreadOnly, page, size } }),
    placeholderData: (prev) => prev,
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => client.get('/api/notifications/unread-count'),
    refetchInterval: 30_000,
  });
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => client.patch(`/api/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => client.patch('/api/notifications/read-all'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}
