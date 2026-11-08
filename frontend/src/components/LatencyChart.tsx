import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, ReferenceLine } from 'recharts';
import type { Check } from '../types';
import { Empty } from './Common';
export function LatencyChart({ checks, threshold }: { checks: Check[]; threshold: number }) {
  if (checks.filter(c => c.durationMs !== null).length < 2) return <Empty title="Waiting for latency history">At least two checks with recorded durations are needed.</Empty>;
  const points = [...checks].sort((a, b) => Date.parse(a.checkedAt) - Date.parse(b.checkedAt)).map(c => ({ ...c, time: Date.parse(c.checkedAt) }));
  return <><div className="chart" role="img" aria-label="Response latency history. Failed checks use red points; missing durations are gaps. Exact values are in the check history table.">
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={points} margin={{ top: 20, right: 24, bottom: 8, left: 8 }}>
        <CartesianGrid stroke="#263242" strokeDasharray="3 3" />
        <XAxis dataKey="time" type="number" domain={['dataMin', 'dataMax']} tickFormatter={time => new Date(time).toLocaleTimeString()} stroke="#98a6ba" minTickGap={50} />
        <YAxis unit=" ms" stroke="#98a6ba" width={75} />
        <Tooltip labelFormatter={time => new Date(Number(time)).toLocaleString()} contentStyle={{ background: '#172131', border: '1px solid #344155', borderRadius: 8 }} />
        <ReferenceLine y={threshold} stroke="#e9b960" strokeDasharray="5 5" label={{ value: 'Degradation threshold', fill: '#e9b960', position: 'insideTopRight' }} />
        <Line type="linear" dataKey="durationMs" name="Elapsed time" unit=" ms" stroke="#6aa9ff" strokeWidth={2} connectNulls={false} isAnimationActive={false}
          dot={({ cx, cy, payload }) => payload.durationMs === null || !Number.isFinite(cx) || !Number.isFinite(cy)
            ? <g key={payload.id} />
            : <circle key={payload.id} cx={cx} cy={cy} r={payload.status === 'DOWN' ? 5 : 3} fill={payload.status === 'DOWN' ? '#ff7a8a' : '#6aa9ff'} stroke="#111a27" />} />
      </LineChart>
    </ResponsiveContainer>
  </div><p className="muted chart-note">Red points: failed checks. Network failures show elapsed time until failure, not server response latency. Missing durations remain gaps.</p></>;
}
