# RagBot — Telegram-бот с RAG на Vertex AI + панель управления

Spring Boot 3.4 · Java 17 · Spring AI 1.0 · Gemini (Vertex AI) · pgvector · React 18 + Vite

```
Telegram ─webhook─▶ TelegramController ─┐
Slack ────events──▶ SlackController ────┴▶ RagService ─▶ pgvector (поиск top-K)
                                               │             ▲
                                               ▼             │ эмбеддинги text-embedding-005
                                        Gemini 2.5 Flash   KnowledgeService ◀── загрузка PDF/DOCX/MD
                                               │
                                               ▼
                                  MessageLog (вопрос, ответ, источники, latency) ─▶ React-панель
```

## Запуск
1. В Google Cloud включите Vertex AI API, создайте service account с ролью *Vertex AI User*, скачайте ключ в `gcp.json`.
2. `cp .env.example .env` и заполните значения (токен бота — у @BotFather).
3. `docker compose up --build` → панель на http://localhost:8080 (вход по `ADMIN_TOKEN`).
4. Загрузите документы во вкладке «База знаний», проверьте ответы в «Песочнице».
5. Подключите Telegram (нужен публичный HTTPS, локально — ngrok / cloudflared):
```
curl "https://api.telegram.org/bot$TELEGRAM_TOKEN/setWebhook" \
  -d url=https://<host>/api/telegram/webhook -d secret_token=$TELEGRAM_WEBHOOK_SECRET
```

## Slack
1. https://api.slack.com/apps → *Create New App* → From scratch.
2. **OAuth & Permissions** → Bot Token Scopes: `chat:write`, `app_mentions:read`, `im:history` → Install to Workspace → `xoxb-…` в `SLACK_BOT_TOKEN`.
3. **Basic Information** → Signing Secret → `SLACK_SIGNING_SECRET`.
4. **Event Subscriptions** → Request URL `https://<host>/api/slack/events` → события бота: `app_mention`, `message.im`.
5. **Interactivity & Shortcuts** → Request URL `https://<host>/api/slack/interactions` (кнопки 👍/👎).
6. **App Home** → включите *Messages Tab* и разрешите писать боту. Бот отвечает в треде на упоминание и в личке.

Запросы Slack проверяются по подписи `X-Slack-Signature` (HMAC-SHA256, окно 5 минут), ретраи Slack игнорируются.

## Оценка качества ответов
Под каждым ответом стоят 👍/👎 (Telegram — inline-кнопки, Slack — Block Kit, панель и песочница — кнопки).
Оценка пишется в `MessageLog.feedback`, после нажатия кнопки убираются. В панели:
- **Удовлетворённость** = 👍 / (👍 + 👎) за 7 дней и счётчик оценок;
- **Качество по документам** — доля 👍/👎 среди ответов, где документ был источником: видно, какие файлы базы знаний исправлять;
- вкладка **Диалоги** → фильтр «👎 На разбор» — очередь плохих ответов с источниками и score.

## Разработка
- Бэкенд: `cd backend && mvn spring-boot:run` (нужна запущенная БД: `docker compose up db`)
- Панель: `cd dashboard && npm install && npm run dev` (прокси на :8080)

## Что делает проект сильнее исходника
| watsonwork-spring-boot (2017) | RagBot |
|---|---|
| Java 8, Gradle, Retrofit | Java 17, Spring AI |
| Эхо-логика, платформа закрыта | RAG с источниками, Gemini на Vertex AI, Telegram + Slack |
| Секреты в `application.yml` | Только переменные окружения, токен панели, проверка secret вебхука |
| Нет UI (Thymeleaf-заглушка) | React-панель: метрики, история, источники, база знаний, метрика качества 👍/👎, тёмная тема |

## Дальше (roadmap)
Стриминг ответов, автооценка качества LLM-as-judge (faithfulness / relevance) поверх ручных 👍/👎, Flyway вместо `ddl-auto: update`, Testcontainers-тесты.
