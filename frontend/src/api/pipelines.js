import { useQuery } from '@tanstack/react-query';
import client from './client';

export function usePipelineExecutions(page = 0, size = 20) {
  return useQuery({
    queryKey: ['pipelines', 'list', page],
    queryFn: () => client.get('/api/pipelines/executions', { params: { page, size } }),
    placeholderData: (prev) => prev,
  });
}

export function usePipelineExecution(id) {
  return useQuery({
    queryKey: ['pipelines', 'detail', id],
    queryFn: () => client.get(`/api/pipelines/executions/${id}`),
    enabled: !!id,
  });
}

export function usePipelineStages(id) {
  return useQuery({
    queryKey: ['pipelines', 'stages', id],
    queryFn: () => client.get(`/api/pipelines/executions/${id}/stages`),
    enabled: !!id,
  });
}

export function usePipelineLogs(id, options = {}) {
  return useQuery({
    queryKey: ['pipelines', 'logs', id],
    queryFn: () => client.get(`/api/pipelines/executions/${id}/logs`),
    enabled: !!id && (options.enabled ?? true),
    retry: false,
  });
}

export function usePipelinesByTicket(ticketId) {
  return useQuery({
    queryKey: ['pipelines', 'by-ticket', ticketId],
    queryFn: () => client.get(`/api/pipelines/executions/by-ticket/${ticketId}`),
    enabled: !!ticketId,
  });
}
