import { cloneElement } from 'react';
import type { ReactElement } from 'react';
import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { LatencyChart } from './LatencyChart';
import type { Check } from '../types';
// jsdom has no layout engine. Supply dimensions at the measurement boundary;
// the chart, line generation and point rendering remain real Recharts code.
vi.mock('recharts', async original => ({
  ...await original<typeof import('recharts')>(),
  ResponsiveContainer: ({ children }: { children: ReactElement<{ width: number; height: number }> }) => cloneElement(children, { width: 800, height: 300 }),
}));
it('marks failed checks and leaves missing durations unplotted', async () => {
  const checks: Check[] = [
    { id: '1', monitorId: 'a', checkedAt: '2026-01-01T00:00:00Z', httpStatus: 200, durationMs: 40, status: 'UP', failureReason: null },
    { id: '2', monitorId: 'a', checkedAt: '2026-01-01T00:00:05Z', httpStatus: null, durationMs: null, status: 'DOWN', failureReason: 'DNS_FAILURE' },
    { id: '3', monitorId: 'a', checkedAt: '2026-01-01T00:00:10Z', httpStatus: null, durationMs: 1000, status: 'DOWN', failureReason: 'TIMEOUT' },
  ];
  const { container } = render(<LatencyChart checks={checks} threshold={200} />);
  expect(screen.getByRole('img')).toHaveAccessibleName(/missing durations are gaps/);
  expect(container.querySelectorAll('circle[fill="#ff7a8a"]')).toHaveLength(1);
  expect(container.querySelectorAll('circle[fill="#6aa9ff"]')).toHaveLength(1);
});
