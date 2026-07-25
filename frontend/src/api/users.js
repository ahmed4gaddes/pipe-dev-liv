import { useQuery } from '@tanstack/react-query';
import client from './client';

export function useUsers(page = 0, size = 50) {
  return useQuery({
    queryKey: ['users', 'list', page],
    queryFn: () => client.get('/api/users', { params: { page, size } }),
  });
}

export function useCurrentUser(enabled = true) {
  return useQuery({
    queryKey: ['users', 'me'],
    queryFn: () => client.get('/api/users/me'),
    enabled,
    staleTime: 5 * 60_000,
  });
}
