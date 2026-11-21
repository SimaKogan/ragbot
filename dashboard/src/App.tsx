import { useCallback, useEffect, useState } from "react";
import { api, Doc, getToken, Msg, setToken, Stats } from "./api";

type Tab = "overview" | "messages" | "knowledge" | "playground";
const TABS: [Tab, string][] = [["overview", "Обзор"], ["messages", "Диалоги"], ["knowledge", "База знаний"], ["playground", "Песочница"]];
const fmt = (iso: string) => new Date(iso).toLocaleString("ru-RU", { dateStyle: "short", timeStyle: "short" });

function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T>(); const [error, setError] = useState("");
  const reload = useCallback(() => { fn().then(setData).catch(e => setError(e.message)); }, deps); // eslint-disable-line
  useEffect(reload, [reload]);
  return { data, error, reload };
}

function Login({ onDone }: { onDone: () => void }) {
  const [v, setV] = useState("");
  return (
    <div className="login card">
      <h1>RagBot</h1><p className="muted">Введите токен администратора</p>
      <input type="password" value={v} onChange={e => setV(e.target.value)} placeholder="ADMIN_TOKEN"
        onKeyDown={e => e.key === "Enter" && (setToken(v), onDone())} />
      <button onClick={() => { setToken(v); onDone(); }}>Войти</button>
    </div>
  );
}

function Overview() {
  const { data: s, error } = useLoad<Stats>(api.stats);
  if (error) return <p className="error">{error}</p>;
  if (!s) return <p className="muted">Загрузка…</p>;
  const days = Object.entries(s.perDay); const max = Math.max(1, ...days.map(d => d[1]));
  const kpis: [string, string | number][] = [["Всего вопросов", s.total], ["За 24 часа", s.last24h],
    ["Ответов с источниками", `${s.groundedRate}%`],
    ["Удовлетворённость", s.satisfaction == null ? "—" : `${s.satisfaction}%`], ["Оценок 👍/👎", `${s.thumbsUp} / ${s.thumbsDown}`], ["Средняя задержка", `${s.avgLatencyMs} мс`], ["Документов", s.documents]];
  return (<>
    <div className="kpis">{kpis.map(([l, v]) => <div className="card kpi" key={l}><span className="muted">{l}</span><b>{v}</b></div>)}</div>
    <div className="card"><h3>Вопросы за 7 дней</h3>
      <div className="bars">{days.map(([d, n]) => (
        <div className="bar" key={d}><span>{n}</span><i style={{ height: `${(n / max) * 100}%` }} /><em>{d.slice(5)}</em></div>))}</div>
    </div>
    <div className="card"><h3>Качество по документам</h3>
      {s.sourceQuality.length === 0 && <p className="muted">Оценок пока нет — они появятся после 👍/👎 в Telegram, Slack или песочнице.</p>}
      {s.sourceQuality.map(q => { const t = q.up + q.down; return (
        <div className="qrow" key={q.doc}><span className="q">{q.doc}</span>
          <div className="qbar"><i className="g" style={{ width: `${(q.up / t) * 100}%` }} /><i className="r" style={{ width: `${(q.down / t) * 100}%` }} /></div>
          <span className="muted">👍 {q.up} · 👎 {q.down}</span></div>); })}
      <p className="muted">Документы с высокой долей 👎 — кандидаты на доработку.</p>
    </div>
  </>);
}

function Messages() {
  const [page, setPage] = useState(0); const [open, setOpen] = useState<Msg>(); const [fb, setFb] = useState<number>(0);
  const { data, error, reload } = useLoad(() => api.messages(page, fb || undefined), [page, fb]);
  if (error) return <p className="error">{error}</p>;
  const FILTERS: [number, string][] = [[0, "Все"], [-1, "👎 На разбор"], [1, "👍 Хорошие"]];
  return (<>
    <div className="chips">{FILTERS.map(([v, l]) => (
      <button key={v} className={`chip ${fb === v ? "on" : ""}`} onClick={() => { setFb(v); setPage(0); }}>{l}</button>))}</div>
    <div className="card table">
      {data?.content.map(m => (
        <button key={m.id} className="row" onClick={() => setOpen(m)}>
          <span className={`tag ${m.channel}`}>{m.channel}</span>
          <span className="q">{m.question}</span>
          <span>{m.feedback === 1 ? "👍" : m.feedback === -1 ? "👎" : ""}</span>
          <span className="muted">{m.sources.length ? `${m.sources.length} ист.` : "без источников"}</span>
          <span className="muted">{fmt(m.createdAt)}</span>
        </button>))}
      {data?.content.length === 0 && <p className="muted pad">Пока нет сообщений</p>}
    </div>
    <div className="pager"><button disabled={page === 0} onClick={() => setPage(page - 1)}>←</button>
      <span className="muted">{page + 1} / {Math.max(1, data?.totalPages ?? 1)}</span>
      <button disabled={page + 1 >= (data?.totalPages ?? 1)} onClick={() => setPage(page + 1)}>→</button></div>
    {open && <Drawer m={open} onClose={() => { setOpen(undefined); reload(); }} />}
  </>);
}

function Rating({ m }: { m: Msg }) {
  const [v, setV] = useState(m.feedback ?? 0);
  useEffect(() => setV(m.feedback ?? 0), [m.id, m.feedback]);
  const rate = (x: 1 | -1) => api.rate(m.id, x).then(() => setV(x)).catch(() => {});
  return (<div className="rating"><span className="muted">Ответ полезен?</span>
    <button className={v === 1 ? "on up" : ""} onClick={() => rate(1)}>👍</button>
    <button className={v === -1 ? "on down" : ""} onClick={() => rate(-1)}>👎</button></div>);
}

function Answer({ m }: { m: Msg }) {
  return (<>
    <p className="bubble user">{m.question}</p>
    <p className="bubble bot">{m.answer}</p>
    <Rating m={m} />
    <h4>Источники ответа <span className="muted">· {m.latencyMs} мс</span></h4>
    {m.sources.length === 0 && <p className="muted">Контекст не найден — бот отказался отвечать.</p>}
    {m.sources.map((s, i) => (
      <div className="source" key={i}><div className="srchead"><b>{s.doc}</b>
        {s.score != null && <span className="score"><i style={{ width: `${Math.round(s.score * 100)}%` }} />{s.score.toFixed(2)}</span>}</div>
        <p className="muted">{s.snippet}</p></div>))}
  </>);
}

function Drawer({ m, onClose }: { m: Msg; onClose: () => void }) {
  return (<div className="overlay" onClick={onClose}><aside className="drawer" onClick={e => e.stopPropagation()}>
    <header><h3>@{m.username} · {fmt(m.createdAt)}</h3><button onClick={onClose}>✕</button></header><Answer m={m} />
  </aside></div>);
}

function Knowledge() {
  const { data, error, reload } = useLoad<Doc[]>(api.docs); const [busy, setBusy] = useState(false); const [err, setErr] = useState("");
  const onFile = async (f?: File) => { if (!f) return; setBusy(true); setErr("");
    try { await api.upload(f); reload(); } catch (e) { setErr((e as Error).message); } finally { setBusy(false); } };
  return (<>
    <label className="card drop">{busy ? "Индексация…" : "Перетащите или выберите файл (PDF, DOCX, MD, TXT)"}
      <input type="file" hidden disabled={busy} onChange={e => onFile(e.target.files?.[0])} /></label>
    {(error || err) && <p className="error">{error || err}</p>}
    <div className="card table">{data?.map(d => (
      <div className="row static" key={d.id}><b className="q">{d.filename}</b><span className="muted">{d.chunks} чанков</span>
        <span className="muted">{fmt(d.createdAt)}</span>
        <button className="ghost" onClick={() => api.remove(d.id).then(reload)}>Удалить</button></div>))}
      {data?.length === 0 && <p className="muted pad">База знаний пуста</p>}</div>
  </>);
}

function Playground() {
  const [q, setQ] = useState(""); const [m, setM] = useState<Msg>(); const [busy, setBusy] = useState(false); const [err, setErr] = useState("");
  const ask = async () => { if (!q.trim()) return; setBusy(true); setErr("");
    try { setM(await api.chat(q)); } catch (e) { setErr((e as Error).message); } finally { setBusy(false); } };
  return (<>
    <div className="card ask"><input value={q} onChange={e => setQ(e.target.value)} placeholder="Спросите что-нибудь по базе знаний…"
      onKeyDown={e => e.key === "Enter" && ask()} /><button disabled={busy} onClick={ask}>{busy ? "…" : "Спросить"}</button></div>
    {err && <p className="error">{err}</p>}{m && <div className="card"><Answer m={m} /></div>}
  </>);
}

export default function App() {
  const [authed, setAuthed] = useState(!!getToken()); const [tab, setTab] = useState<Tab>("overview");
  const [theme, setTheme] = useState(localStorage.getItem("ragbot.theme") ?? "dark");
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem("ragbot.theme", theme); }, [theme]);
  if (!authed) return <Login onDone={() => setAuthed(true)} />;
  return (<div className="shell">
    <nav><div className="brand">◆ RagBot</div>
      {TABS.map(([k, l]) => <button key={k} className={tab === k ? "active" : ""} onClick={() => setTab(k)}>{l}</button>)}
      <div className="spacer" />
      <button onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>{theme === "dark" ? "☀ Светлая" : "☾ Тёмная"}</button>
      <button onClick={() => { setToken(""); setAuthed(false); }}>Выйти</button></nav>
    <main><h2>{TABS.find(t => t[0] === tab)![1]}</h2>
      {tab === "overview" && <Overview />}{tab === "messages" && <Messages />}
      {tab === "knowledge" && <Knowledge />}{tab === "playground" && <Playground />}</main>
  </div>);
}
