import { useState } from 'react';
import type { FormEvent } from 'react';
import type { MonitorInput } from '../types';
import { ApiError } from '../api';
import { ErrorNotice } from './Common';
const defaults: MonitorInput = { name: '', url: '', intervalSeconds: 60, timeoutMs: 5000, degradationThresholdMs: 1000, enabled: true };
export function MonitorForm({ initial, onSave, onCancel }: {
  initial?: MonitorInput; onSave: (input: MonitorInput) => Promise<void>; onCancel: () => void;
}) {
  const [values, setValues] = useState<MonitorInput>(initial || defaults);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string>();
  const [saving, setSaving] = useState(false);
  function change(key: keyof MonitorInput, value: string | number | boolean) { setValues(prev => ({ ...prev, [key]: value })); }
  async function submit(event: FormEvent) {
    event.preventDefault();
    const next: Record<string, string> = {};
    if (!values.name.trim() || values.name.trim().length > 120) next.name = 'Enter a name between 1 and 120 characters.';
    try {
      const url = new URL(values.url);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.hash || values.url.length > 2048) throw new Error();
    } catch { next.url = 'Enter an HTTP/HTTPS URL without credentials or fragments.'; }
    for (const [key, min, max] of [['intervalSeconds', 5, 86400], ['timeoutMs', 100, 30000], ['degradationThresholdMs', 1, 30000]] as const) {
      if (!Number.isInteger(values[key]) || values[key] < min || values[key] > max) next[key] = `Enter a whole number from ${min} to ${max}.`;
    }
    if (values.degradationThresholdMs > values.timeoutMs) next.degradationThresholdMs = 'Threshold must not exceed timeout.';
    setErrors(next); setError(undefined);
    if (Object.keys(next).length) return;
    setSaving(true);
    try { await onSave({ ...values, name: values.name.trim() }); }
    catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not save monitor');
      if (cause instanceof ApiError) setErrors(cause.fields);
    } finally { setSaving(false); }
  }
  function field(key: keyof MonitorInput, label: string, type = 'text', min?: number, max?: number) {
    return <label className={key === 'url' || key === 'name' ? 'wide' : ''} htmlFor={key}>{label}
      <input id={key} name={key} type={type} value={String(values[key])} min={min} max={max}
        autoComplete="off" aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `${key}-error` : undefined}
        onChange={e => change(key, type === 'number' ? Number(e.target.value) : e.target.value)} />
      {errors[key] && <small className="field-error" id={`${key}-error`}>{errors[key]}</small>}
    </label>;
  }
  return <form onSubmit={submit} noValidate className="monitor-form">
    <ErrorNotice message={error} />
    <fieldset disabled={saving}><legend className="sr-only">Monitor configuration</legend><div className="form-grid">
      {field('name', 'Monitor name')}{field('url', 'Endpoint URL', 'url')}
      {field('intervalSeconds', 'Interval (seconds)', 'number', 5, 86400)}
      {field('timeoutMs', 'Timeout (ms)', 'number', 100, 30000)}
      {field('degradationThresholdMs', 'Degraded above (ms)', 'number', 1, 30000)}
      <label className="checkbox wide"><input type="checkbox" checked={values.enabled} onChange={e => change('enabled', e.target.checked)} /> Enable monitoring</label>
    </div><p className="muted">GET requests only. Public destinations are required by default. Latency measures time to response headers.</p>
    <div className="actions"><button type="button" onClick={onCancel}>Cancel</button><button className="primary" type="submit">{saving ? 'Saving…' : initial ? 'Save changes' : 'Create monitor'}</button></div>
    </fieldset>
  </form>;
}
