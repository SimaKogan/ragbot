export type Source = { doc: string; snippet: string; score: number | null };
export type Msg = { id: number; channel: string; username: string; question: string; answer: string;
  latencyMs: number; createdAt: string; sources: Source[]; feedback: number | null };
export type Doc = { id: number; filename: string; chunks: number; createdAt: string };
export type Stats = { total: number; last24h: number; avgLatencyMs: number; groundedRate: number;
  documents: number; perDay: Record<string, number>; satisfaction: number | null; thumbsUp: number; thumbsDown: number;
  sourceQuality: { doc: string; up: number; down: number }[] };

const key = "ragbot.token";
export const getToken = () => localStorage.getItem(key) ?? "";
export const setToken = (t: string) => localStorage.setItem(key, t);

async function req<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(path, { ...init, headers: { "X-Admin-Token": getToken(),
    ...(init.body && !(init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}) } });
  if (!res.ok) throw new Error(res.status === 401 ? "Неверный токен" : `Ошибка ${res.status}`);
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  stats: () => req<Stats>("/api/stats"),
  messages: (page = 0, feedback?: number) =>
    req<{ content: Msg[]; totalPages: number }>(`/api/messages?page=${page}&size=15${feedback ? `&feedback=${feedback}` : ""}`),
  rate: (id: number, value: 1 | -1) => req<Msg>(`/api/messages/${id}/feedback`, { method: "POST", body: JSON.stringify({ value }) }),
  docs: () => req<Doc[]>("/api/documents"),
  upload: (f: File) => { const fd = new FormData(); fd.append("file", f); return req<Doc>("/api/documents", { method: "POST", body: fd }); },
  remove: (id: number) => req<void>(`/api/documents/${id}`, { method: "DELETE" }),
  chat: (question: string) => req<Msg>("/api/chat", { method: "POST", body: JSON.stringify({ question }) }),
};
