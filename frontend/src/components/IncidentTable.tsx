import { Link } from 'react-router-dom';
import type { Incident } from '../types';
import { dateTime, duration, Empty } from './Common';
export function IncidentTable({ incidents }: { incidents: Incident[] }) {
  if (!incidents.length) return <Empty title="No incidents recorded">Incidents open after three consecutive DOWN checks.</Empty>;
  return <div className="table-scroll"><table><thead><tr><th>Monitor</th><th>Status</th><th>Started</th><th>Recovered</th><th>Duration</th></tr></thead>
    <tbody>{incidents.map(i => <tr key={i.id} className={i.active ? 'active-incident' : ''}>
      <td><Link to={`/monitors/${i.monitorId}`}>{i.monitorName}</Link></td><td><span className={`status ${i.active ? 'down' : 'up'}`}>{i.active ? '● ACTIVE' : '✓ RESOLVED'}</span></td>
      <td>{dateTime(i.startedAt)}</td><td>{dateTime(i.recoveredAt)}</td><td>{duration(i.durationSeconds)}{i.active ? ' ongoing' : ''}</td>
    </tr>)}</tbody></table></div>;
}
