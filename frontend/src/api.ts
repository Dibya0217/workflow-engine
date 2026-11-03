import axios from "axios";
import { useCallback, useEffect, useState } from "react";
export const api = axios.create({ baseURL: "/api/v1", timeout: 15000 });
let accessToken: string | null = null;
export function setToken(token: string | null) {
  accessToken = token;
}
api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      error.response?.status === 401 &&
      !error.config?.url?.endsWith("/auth/login")
    )
      window.dispatchEvent(new Event("flowlab:unauthorized"));
    return Promise.reject(error);
  },
);
export function errorMessage(error: unknown): string {
  return axios.isAxiosError(error)
    ? (error.response?.data?.detail ??
        (error.code === "ERR_NETWORK"
          ? "Cannot reach the backend. Check that FlowLab is running."
          : error.message))
    : error instanceof Error
      ? error.message
      : "Something went wrong";
}
export function useApi<T>(path: string, refreshMs = 0) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision((x) => x + 1), []);
  useEffect(() => {
    const abort = new AbortController();
    let pending = false;
    setData(undefined);
    setLoading(true);
    setError("");
    const load = async () => {
      if (pending) return;
      pending = true;
      try {
        const response = await api.get<T>(path, { signal: abort.signal });
        setData(response.data);
        setError("");
      } catch (e) {
        if (!axios.isCancel(e)) setError(errorMessage(e));
      } finally {
        if (!abort.signal.aborted) setLoading(false);
        pending = false;
      }
    };
    void load();
    const timer = refreshMs
      ? setInterval(() => void load(), refreshMs)
      : undefined;
    return () => {
      abort.abort();
      clearInterval(timer);
    };
  }, [path, refreshMs, revision]);
  return { data, error, loading, refresh };
}
export const date = (value: string | null) =>
  value
    ? new Date(value).toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : "—";
export function duration(ms: number) {
  if (ms < 1000) return `${Math.round(ms)} ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} s`;
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`;
}
export const shortId = (id: string) => id.slice(0, 8);
