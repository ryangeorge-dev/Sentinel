import { useCallback, useState } from 'react';
import { api } from './api';
import { useResource } from './hooks/useResource';
import { ErrorNotice, Pagination } from './components/Common';
import { IncidentTable } from './components/IncidentTable';
export function IncidentsPage() {
  const [page, setPage] = useState(0);
  const loader = useCallback((signal: AbortSignal) => api.incidents(page, signal), [page]);
  const { data, loading, error, refresh } = useResource(loader);
  return <><header className="page-heading"><div><p className="eyebrow">AVAILABILITY LOG</p><h1>Incidents</h1><p className="muted">Outages open after three consecutive failures and resolve on recovery.</p></div></header>
    <ErrorNotice message={error} retry={refresh}/>{loading && <p role="status">Loading incidents…</p>}
    {data && <section className="panel"><div className="panel-heading"><h2>Incident history</h2><span className="muted">{data.totalElements} total</span></div><IncidentTable incidents={data.content}/><Pagination page={page} totalPages={data.totalPages} onPage={setPage}/></section>}
  </>;
}
