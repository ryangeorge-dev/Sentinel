import { useCallback, useEffect, useState } from 'react';

/** Poll after each completed request; abort stale work on navigation or refresh. */
export function useResource<T>(loader: (signal: AbortSignal) => Promise<T>) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision(value => value + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    setLoading(true);
    setData(undefined);
    setError(undefined);
    async function load() {
      try {
        const value = await loader(controller.signal);
        if (!controller.signal.aborted) { setData(value); setError(undefined); }
      } catch (cause) {
        if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Could not load data');
      } finally {
        if (!controller.signal.aborted) { setLoading(false); timer = setTimeout(load, 10000); }
      }
    }
    void load();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [loader, revision]);
  return { data, error, loading, refresh };
}
