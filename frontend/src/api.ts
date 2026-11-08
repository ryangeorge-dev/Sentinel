import type { Check, Dashboard, Incident, Monitor, MonitorInput, Page } from './types';

export class ApiError extends Error {
  constructor(message: string, public fields: Record<string, string> = {}) { super(message); }
}
const base = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${base}${path}`, {
      ...options, headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError('Cannot reach Sentinel. Check that the backend is running.');
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(body?.message || `Request failed (${response.status})`, body?.errors || {});
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export const api = {
  dashboard: (signal?: AbortSignal) => request<Dashboard>('/dashboard', { signal }),
  monitors: (page = 0, signal?: AbortSignal) => request<Page<Monitor>>(`/monitors?page=${page}&size=20`, { signal }),
  monitor: (id: string, signal?: AbortSignal) => request<Monitor>(`/monitors/${id}`, { signal }),
  checks: (id: string, page = 0, signal?: AbortSignal) => request<Page<Check>>(`/monitors/${id}/checks?page=${page}&size=50`, { signal }),
  incidents: (page = 0, signal?: AbortSignal, id?: string) => request<Page<Incident>>(`${id ? `/monitors/${id}` : ''}/incidents?page=${page}&size=20`, { signal }),
  create: (input: MonitorInput) => request<Monitor>('/monitors', { method: 'POST', body: JSON.stringify(input) }),
  update: (id: string, input: MonitorInput) => request<Monitor>(`/monitors/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  enable: (id: string, enabled: boolean) => request<Monitor>(`/monitors/${id}/enabled`, { method: 'PATCH', body: JSON.stringify({ enabled }) }),
  remove: (id: string) => request<void>(`/monitors/${id}`, { method: 'DELETE' }),
};
