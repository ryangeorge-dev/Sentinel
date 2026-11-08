export type HealthStatus = 'UP' | 'DEGRADED' | 'DOWN';
export interface MonitorInput {
  name: string; url: string; intervalSeconds: number; timeoutMs: number;
  degradationThresholdMs: number; enabled: boolean;
}
export interface Monitor extends MonitorInput {
  id: string; createdAt: string; updatedAt: string; currentStatus: HealthStatus | null;
  lastCheckedAt: string | null; latestDurationMs: number | null; latestHttpStatus: number | null;
}
export interface Check {
  id: string; monitorId: string; checkedAt: string; httpStatus: number | null;
  durationMs: number | null; status: HealthStatus; failureReason: string | null;
}
export interface Incident {
  id: string; monitorId: string; monitorName: string; startedAt: string;
  recoveredAt: string | null; durationSeconds: number; active: boolean;
}
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Dashboard {
  total: number; up: number; degraded: number; down: number; pending: number; activeIncidents: number;
  recentActivity: { monitorName: string; check: Check }[];
}
