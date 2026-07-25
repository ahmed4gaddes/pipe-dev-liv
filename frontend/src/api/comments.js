import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import client from './client';

export function useComments(ticketId) {
  return useQuery({
    queryKey: ['tickets', 'comments', ticketId],
    queryFn: () => client.get(`/api/tickets/${ticketId}/comments`),
    enabled: !!ticketId,
  });
}

export function useAddComment(ticketId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (dto) => client.post(`/api/tickets/${ticketId}/comments`, dto),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tickets', 'comments', ticketId] }),
  });
}
