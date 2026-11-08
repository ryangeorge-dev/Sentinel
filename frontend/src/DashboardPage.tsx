import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from './api';
import { useResource } from './hooks/useResource';
import { dateTime, Empty, ErrorNotice, Pagination, Status } from './components/Common';
import { MonitorForm } from './components/MonitorForm';
export function DashboardPage() {
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const navigate = useNavigate();
  const load = useCallback(async (signal: AbortSignal) => {
    const [summary, monitors] = await Promise.all([api.dashboard(signal), api.monitors(page, signal)]);
    return { summary, monitors };
  }, [page]);
  const { data, loading, error, refresh } = useResource(load);
  return <>
    <header className="page-heading"><div><p className="eyebrow">WORKSPACE OVERVIEW</p><h1>API monitoring</h1><p className="muted">Availability, performance, and incidents in one place.</p></div>
      <button className="primary" onClick={() => setAdding(true)}>+ Add monitor</button></header>
    {adding && <section className="panel"><div className="panel-heading"><h2>Add a monitor</h2></div><MonitorForm onCancel={() => setAdding(false)} onSave={async input => { const result = await api.create(input); navigate(`/monitors/${result.id}`); }} /></section>}
    <ErrorNotice message={error} retry={refresh} />
    {loading && <p role="status">Loading monitors…</p>}
    {data && <>
      <div className="metrics">
        {([['Endpoints', data.summary.total, ''], ['Up', data.summary.up, 'up'], ['Degraded', data.summary.degraded, 'degraded'], ['Down', data.summary.down, 'down'], ['Active incidents', data.summary.activeIncidents, 'down']] as const).map(([label, value, tone]) =>
          <article className="metric" key={label}><span>{label}</span><strong className={tone}>{value}</strong></article>)}
      </div>
      <p className="muted summary-note">Last observed status, including paused monitors · {data.summary.pending} awaiting first check · Refreshes every 10 seconds</p>
      <section className="panel"><div className="panel-heading"><h2>Monitors <span className="count">{data.monitors.totalElements}</span></h2><span className="muted">GET endpoints</span></div>
        {!data.monitors.content.length ? <Empty title="No monitors on this page">Add your first HTTP endpoint to start collecting real health checks.</Empty> :
          <div className="table-scroll"><table><thead><tr><th>Endpoint</th><th>Status</th><th>Latency</th><th>Interval</th><th>Last checked</th></tr></thead><tbody>
            {data.monitors.content.map(m => <tr key={m.id}><td><Link className="monitor-link" to={`/monitors/${m.id}`}>{m.name}</Link><span className="endpoint-url">{m.url}</span></td>
              <td><Status value={m.currentStatus}/>{!m.enabled && <span className="paused">Paused</span>}</td><td className="mono">{m.latestDurationMs === null ? '—' : `${m.latestDurationMs} ms`}</td><td>{m.intervalSeconds}s</td><td>{dateTime(m.lastCheckedAt)}</td></tr>)}
          </tbody></table></div>}
        <Pagination page={page} totalPages={data.monitors.totalPages} onPage={setPage} />
      </section>
      <section className="panel"><div className="panel-heading"><h2>Recent activity</h2><span className="muted">Latest 12 checks</span></div>
        {!data.summary.recentActivity.length ? <Empty title="No checks yet">Enabled monitors will be checked automatically.</Empty> : <ul className="activity">
          {data.summary.recentActivity.map(({ check, monitorName }) => <li key={check.id}><Status value={check.status} /><Link to={`/monitors/${check.monitorId}`}>{monitorName}</Link>
            <span className="muted">{check.failureReason?.replaceAll('_', ' ') || `${check.durationMs ?? '—'} ms`}</span><time dateTime={check.checkedAt}>{dateTime(check.checkedAt)}</time></li>)}
        </ul>}
      </section>
    </>}
  </>;
}
