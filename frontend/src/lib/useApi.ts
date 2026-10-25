"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";

/** Loads `path` through the API helper; `reload` fetches it again (e.g. after a write). */
export function useApi<T>(path: string) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<unknown>();

  const reload = useCallback(async () => {
    try {
      setData(await api<T>(path));
      setError(undefined);
    } catch (e) {
      setError(e);
    }
  }, [path]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- data fetching on mount
    void reload();
  }, [reload]);

  return { data, error, reload };
}
