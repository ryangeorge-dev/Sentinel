import { lazy, Suspense, useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from './api';
import { useResource } from './hooks/useResource';
import { dateTime, Empty, ErrorNotice, Pagination, Status } from './components/Common';
import { IncidentTable } from './components/IncidentTable';
const LatencyChart = lazy(() => import('./components/LatencyChart').then(module => ({ default: module.LatencyChart })));
import { MonitorForm } from './components/MonitorForm';
export function MonitorPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [checkPage, setCheckPage] = useState(0);
  const [incidentPage, setIncidentPage] = useState(0);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string>();
  const load = useCallback(async (signal: AbortSignal) => {
    const [monitor, checks, incidents] = await Promise.all([api.monitor(id, signal), api.checks(id, checkPage, signal), api.incidents(incidentPage, signal, id)]);
    return { monitor, checks, incidents };
  }, [id, checkPage, incidentPage]);
  const { data, loading, error, refresh } = useResource(load);
  async function act(action: () => Promise<unknown>) {
    setBusy(true); setActionError(undefined);
    try { await action(); refresh(); }
    catch (cause) { setActionError(cause instanceof Error ? cause.message : 'Action failed'); }
    finally { setBusy(false); }
  }
  return <>
    <Link className="back-link" to="/">← All monitors</Link>
    <ErrorNotice message={error} retry={refresh} /><ErrorNotice message={actionError} />
    {loading && <p role="status">Loading monitor…</p>}
    {data && <>
      <header className="page-heading"><div><p className="eyebrow">ENDPOINT DETAILS</p><h1>{data.monitor.name}</h1><p className="endpoint-full">{data.monitor.url}</p></div>
        <div className="actions"><button disabled={busy} onClick={() => setEditing(!editing)}>Edit</button>
          <button disabled={busy} onClick={() => void act(() => api.enable(id, !data.monitor.enabled))}>{data.monitor.enabled ? 'Pause monitoring' : 'Enable monitoring'}</button>
          <button className="danger" disabled={busy} onClick={() => setDeleting(true)}>Delete</button></div></header>
      {deleting && <section className="confirm panel" role="alertdialog" aria-labelledby="delete-title" aria-describedby="delete-description"><h2 id="delete-title">Delete {data.monitor.name}?</h2>
        <p id="delete-description">This permanently removes the monitor, its checks, and its incident history.</p><div className="actions"><button disabled={busy} onClick={() => setDeleting(false)}>Cancel</button>
          <button className="danger" disabled={busy} onClick={() => void act(async () => { await api.remove(id); navigate('/'); })}>Confirm deletion</button></div></section>}
      {editing && <section className="panel"><div className="panel-heading"><h2>Edit configuration</h2></div><MonitorForm initial={data.monitor} onCancel={() => setEditing(false)} onSave={async input => { await api.update(id, input); setEditing(false); refresh(); }} /></section>}
      <div className="metrics detail-metrics">
        <article className="metric"><span>Last observed status</span><Status value={data.monitor.currentStatus} /><small>{data.monitor.enabled ? 'Monitoring enabled' : 'Monitoring paused'}</small></article>
        <article className="metric"><span>Latest elapsed time</span><strong>{data.monitor.latestDurationMs ?? '—'}<small> ms</small></strong></article>
        <article className="metric"><span>HTTP response</span><strong>{data.monitor.latestHttpStatus ?? '—'}</strong></article>
        <article className="metric"><span>Last checked</span><p>{dateTime(data.monitor.lastCheckedAt)}</p><small>Interval {data.monitor.intervalSeconds}s · Timeout {data.monitor.timeoutMs}ms</small></article>
      </div>
      <section className="panel"><div className="panel-heading"><h2>Response time</h2><span className="muted">Check history page {checkPage + 1}</span></div><Suspense fallback={<p className="empty" role="status">Loading chart…</p>}><LatencyChart checks={data.checks.content} threshold={data.monitor.degradationThresholdMs} /></Suspense></section>
      <section className="panel"><div className="panel-heading"><h2>Check history</h2><span className="muted">{data.checks.totalElements} checks</span></div>
        {!data.checks.content.length ? <Empty title="No checks recorded">{data.monitor.enabled ? 'The scheduler will check this endpoint shortly.' : 'Enable monitoring to begin collecting checks.'}</Empty> :
          <div className="table-scroll"><table><thead><tr><th>Time</th><th>Health</th><th>Elapsed</th><th>HTTP</th><th>Failure reason</th></tr></thead><tbody>
            {data.checks.content.map(c => <tr key={c.id}><td>{dateTime(c.checkedAt)}</td><td><Status value={c.status}/></td><td>{c.durationMs === null ? '—' : `${c.durationMs} ms`}</td><td>{c.httpStatus ?? '—'}</td><td>{c.failureReason?.replaceAll('_', ' ') || '—'}</td></tr>)}
          </tbody></table></div>}
        <Pagination page={checkPage} totalPages={data.checks.totalPages} onPage={setCheckPage} />
      </section>
      <section className="panel"><div className="panel-heading"><h2>Incident history</h2></div><IncidentTable incidents={data.incidents.content} /><Pagination page={incidentPage} totalPages={data.incidents.totalPages} onPage={setIncidentPage} /></section>
    </>}
  </>;
}
