# Project Context & Sources of Truth
@repomix-business-logic.md
@repomix-frontend.md

# Scope & Product Requirements (STRICT)
We are building ONLY the **Field Staff Mobile Client (Мобильное приложение выездного сотрудника)** in Kotlin and Jetpack Compose.
- **Cut all bloat:** DO NOT build dispatcher dashboards, supervisor analytics, PGP group management tables, or unassigned order queues.
- **Core features ONLY:**
  1. **Маршрут на день (Daily Route):** Personal timeline of stops (`DEPOT_START`, `VISIT`, `BREAK`, `RESTOCK`, `DEPOT_END`) with statuses (`PLANNED`, `IN_PROGRESS`, `DONE`), time windows (`arrival`, `start`, `end`), required/remaining equipment (`ONT`, `ROUTER`, `STB`), and order details.
  2. **Навигация к следующему адресу (Navigation to Next Address):** Prominent card for the current/next stop with address, coordinates (`lat`, `lon`), travel time/distance, and one-tap deep-link launch into external navigation apps (Yandex Maps / 2GIS / Google Maps via `geo:` and `yandexnavi:` intents) plus an in-app route preview.
  3. **Отметки о звонке абоненту, прибытии и завершении (Customer Call, Arrival & Completion Check-ins):** Step-by-step interactive workflow on the active `VISIT` card:
     - **"Позвонить абоненту / Звонок выполнен"** (Call customer check-in + optional dialer intent).
     - **"Прибыл на место"** (Arrival check-in, transitions local/active stop state to `IN_PROGRESS`).
     - **"Завершить визит"** (Completion check-in: normal completion, or reporting `VISIT_DELAYED` / `ORDER_TRANSFERRED` / `STAFF_UNAVAILABLE` to `/api/v1/days/{date}/events` according to backend contract).

# Visual Style & Localization
- **100% Russian UI:** All labels, statuses, buttons, dialogs, and error messages must be in natural Russian, matching the terminology in `@repomix-business-logic.md` and `@repomix-frontend.md`.
- **Design System Parity:** Extract exact color tokens, Rostelecom / Atomaro (`@atomaro/ui-kit`) palette, typography scale, border radii, badge colors, and card elevation styles from `@repomix-frontend.md` and replicate them in Jetpack Compose `MaterialTheme`.

# Technical Stack & Domain Rules
- **Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), Coroutines + StateFlow, Hilt (or clean manual DI), Retrofit2 + OkHttp3 + Kotlinx Serialization (`ignoreUnknownKeys = true`).
- **Dual Data Source:** Provide both `RemoteDispatchRepository` (connecting to `/api/v1` with configurable Base URL) and `LocalMockDispatchRepository` (offline in-memory simulator with realistic Yekaterinburg UTC+5 data for instant testing without backend access).
- **Timezone Invariant:** All timestamps (`AwareDatetime`) operate strictly in Yekaterinburg time (`UTC+5`). Never use device system clock for business logic.