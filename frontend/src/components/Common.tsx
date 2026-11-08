import type { HealthStatus } from '../types';
export function Status({ value }: { value: HealthStatus | null }) {
  return <span className={`status ${value?.toLowerCase() || 'pending'}`}><span aria-hidden="true">●</span> {value || 'PENDING'}</span>;
}
export function ErrorNotice({ message, retry }: { message?: string; retry?: () => void }) {
  if (!message) return null;
  return <div className="error" role="alert">{message} {retry && <button onClick={retry}>Retry</button>}</div>;
}
export function Empty({ title, children }: { title: string; children?: React.ReactNode }) {
  return <div className="empty"><strong>{title}</strong><p>{children}</p></div>;
}
export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1 && page === 0) return null;
  return <nav className="pagination" aria-label="Pagination">
    <button disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
    <span>Page {page + 1} of {Math.max(1, totalPages)}</span>
    <button disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</button>
  </nav>;
}
export const dateTime = (value: string | null) => value ? new Date(value).toLocaleString() : '—';
export function duration(seconds: number) {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor(seconds % 3600 / 60)}m`;
}
