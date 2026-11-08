import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { App } from './App';
import { MonitorForm } from './components/MonitorForm';
import { IncidentTable } from './components/IncidentTable';
import { LatencyChart } from './components/LatencyChart';
import type { Monitor, MonitorInput, Check } from './types';
const monitor: Monitor = { id: 'abc', name: 'Payments API', url: 'https://example.com/health', intervalSeconds: 60, timeoutMs: 5000, degradationThresholdMs: 1000, enabled: true, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', currentStatus: 'UP', lastCheckedAt: '2026-01-01T00:00:00Z', latestDurationMs: 45, latestHttpStatus: 200 };
const page = <T,>(content: T[]) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const summary = { total: 1, up: 1, degraded: 0, down: 0, pending: 0, activeIncidents: 0, recentActivity: [] };
const server = setupServer(
  http.get('/api/dashboard', () => HttpResponse.json(summary)),
  http.get('/api/monitors', () => HttpResponse.json(page([monitor]))),
  http.get('/api/monitors/abc', () => HttpResponse.json(monitor)),
  http.get('/api/monitors/abc/checks', () => HttpResponse.json(page([]))),
  http.get('/api/monitors/abc/incidents', () => HttpResponse.json(page([]))),
);
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
function app(path = '/') { return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>); }

describe('Dashboard', () => {
  it('renders real API data and status', async () => {
    app();
    expect(await screen.findByRole('link', { name: 'Payments API' })).toHaveAttribute('href', '/monitors/abc');
    expect(screen.getByText('UP')).toBeVisible();
    expect(screen.getByText('45 ms')).toBeVisible();
    expect(screen.getByText('No checks yet')).toBeVisible();
  });
  it('shows loading while a request is pending', async () => {
    server.use(http.get('/api/dashboard', async () => { await delay(100); return HttpResponse.json(summary); }));
    app(); expect(screen.getByRole('status')).toHaveTextContent('Loading monitors');
    await screen.findByRole('link', { name: 'Payments API' });
  });
  it('shows useful empty states', async () => {
    server.use(http.get('/api/monitors', () => HttpResponse.json(page([]))), http.get('/api/dashboard', () => HttpResponse.json({ ...summary, total: 0, up: 0 })));
    app(); expect(await screen.findByText('No monitors on this page')).toBeVisible();
  });
  it('shows API errors and supports retry', async () => {
    server.use(http.get('/api/dashboard', () => HttpResponse.json({ message: 'Database unavailable' }, { status: 500 })));
    app(); expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable');
    server.use(http.get('/api/dashboard', () => HttpResponse.json(summary)));
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('link', { name: 'Payments API' })).toBeVisible();
  });
  it('creates a monitor through the network API and navigates to its details', async () => {
    let captured: MonitorInput | undefined;
    server.use(http.post('/api/monitors', async ({ request }) => {
      captured = await request.json() as MonitorInput;
      return HttpResponse.json(monitor, { status: 201 });
    }));
    const user = userEvent.setup(); app();
    await user.click(screen.getByRole('button', { name: '+ Add monitor' }));
    await user.type(screen.getByLabelText('Monitor name'), 'Payments API');
    await user.type(screen.getByLabelText('Endpoint URL'), 'https://example.com/health');
    await user.click(screen.getByRole('button', { name: 'Create monitor' }));
    expect(await screen.findByRole('heading', { name: 'Payments API' })).toBeVisible();
    expect(captured).toMatchObject({ name: 'Payments API', url: 'https://example.com/health', enabled: true, timeoutMs: 5000 });
    expect(screen.getByRole('heading', { name: 'Check history' })).toBeVisible();
  });
});

describe('Monitor form', () => {
  it('validates names, URLs, bounds, and threshold before submission', async () => {
    const save = vi.fn(); const user = userEvent.setup();
    render(<MonitorForm onSave={save} onCancel={() => {}} />);
    await user.type(screen.getByLabelText('Endpoint URL'), 'file:///etc/passwd');
    await user.clear(screen.getByLabelText('Interval (seconds)'));
    await user.type(screen.getByLabelText('Interval (seconds)'), '1');
    await user.clear(screen.getByLabelText('Degraded above (ms)'));
    await user.type(screen.getByLabelText('Degraded above (ms)'), '6000');
    await user.click(screen.getByRole('button', { name: 'Create monitor' }));
    expect(screen.getByText('Enter a name between 1 and 120 characters.')).toBeVisible();
    expect(screen.getByText('Enter an HTTP/HTTPS URL without credentials or fragments.')).toBeVisible();
    expect(screen.getByText('Enter a whole number from 5 to 86400.')).toBeVisible();
    expect(screen.getByText('Threshold must not exceed timeout.')).toBeVisible();
    expect(save).not.toHaveBeenCalled();
  });
  it('preserves inputs and displays backend validation failures', async () => {
    server.use(http.post('/api/monitors', () => HttpResponse.json({ message: 'Destination must resolve only to public addresses', errors: { url: 'Blocked destination' } }, { status: 400 })));
    const user = userEvent.setup(); app();
    await user.click(screen.getByRole('button', { name: '+ Add monitor' }));
    await user.type(screen.getByLabelText('Monitor name'), 'Local');
    await user.type(screen.getByLabelText('Endpoint URL'), 'http://127.0.0.1');
    await user.click(screen.getByRole('button', { name: 'Create monitor' }));
    expect(await screen.findByText('Blocked destination')).toBeVisible();
    expect(screen.getByLabelText('Monitor name')).toHaveValue('Local');
  });
});

it('distinguishes active and resolved incidents with text', () => {
  render(<MemoryRouter><IncidentTable incidents={[
    { id: '1', monitorId: 'abc', monitorName: 'Payments API', startedAt: '2026-01-01T00:00:00Z', recoveredAt: null, durationSeconds: 65, active: true },
    { id: '2', monitorId: 'abc', monitorName: 'Payments API', startedAt: '2026-01-01T00:00:00Z', recoveredAt: '2026-01-01T00:01:00Z', durationSeconds: 60, active: false },
  ]}/></MemoryRouter>);
  expect(screen.getByText('● ACTIVE')).toBeVisible(); expect(screen.getByText('✓ RESOLVED')).toBeVisible();
  expect(screen.getByText('1m 5s ongoing')).toBeVisible();
});

it('requires confirmation before deleting a monitor', async () => {
  let deleted = false;
  server.use(http.delete('/api/monitors/abc', () => { deleted = true; return new HttpResponse(null, { status: 204 }); }));
  const user = userEvent.setup(); app('/monitors/abc');
  await user.click(await screen.findByRole('button', { name: 'Delete' }));
  expect(deleted).toBe(false);
  const dialog = screen.getByRole('alertdialog');
  await user.click(within(dialog).getByRole('button', { name: 'Confirm deletion' }));
  expect(await screen.findByRole('heading', { name: 'API monitoring' })).toBeVisible();
  expect(deleted).toBe(true);
});

it('pauses monitoring through the API', async () => {
  let enabled: boolean | undefined;
  server.use(http.patch('/api/monitors/abc/enabled', async ({ request }) => {
    enabled = (await request.json() as { enabled: boolean }).enabled;
    server.use(http.get('/api/monitors/abc', () => HttpResponse.json({ ...monitor, enabled })));
    return HttpResponse.json({ ...monitor, enabled });
  }));
  const user = userEvent.setup(); app('/monitors/abc');
  await user.click(await screen.findByRole('button', { name: 'Pause monitoring' }));
  expect(await screen.findByRole('button', { name: 'Enable monitoring' })).toBeVisible();
  expect(enabled).toBe(false);
});

it('does not treat missing latency as zero', () => {
  const checks: Check[] = [1, 2].map(i => ({ id: `${i}`, monitorId: 'abc', checkedAt: '2026-01-01T00:00:00Z', httpStatus: null, durationMs: null, status: 'DOWN', failureReason: 'TIMEOUT' }));
  render(<LatencyChart checks={checks} threshold={1000} />);
  expect(screen.getByText('Waiting for latency history')).toBeVisible();
});
