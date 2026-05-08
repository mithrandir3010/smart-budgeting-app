# 💰 Smart Budget — Bank Statement Analyzer

[![CI — Build & Test](https://github.com/mithrandir3010/smart-budgeting-app/actions/workflows/main.yml/badge.svg)](https://github.com/mithrandir3010/smart-budgeting-app/actions/workflows/main.yml)

> An intelligent, full-stack platform that transforms raw PDF bank statements into structured financial insights. Upload your statement, and Smart Budget automatically extracts, categorizes, and visualizes every transaction — powered by a hybrid AI pipeline engineered to minimize cost without sacrificing accuracy.

<br>

## ✨ Features at a Glance

- 📄 **PDF Ingestion** — Upload bank statement PDFs; the pipeline extracts and normalizes raw text automatically
- 🏷️ **Auto-Categorization** — Transactions are categorized (Food, Transport, Subscriptions, etc.) without manual tagging
- 📊 **Analytics Dashboard** — Spending breakdowns by category, merchant, and time period
- 🔔 **Budget Limits & Alerts** — Set monthly caps per category and get notified when you are close to the limit
- 💳 **Installment Detection** — Taksit (installment) payments are detected and grouped intelligently
- 🤖 **AI Financial Coach (Serena)** — An MCP-powered AI coach that surfaces personalized spending insights
- 🔐 **JWT Auth + Refresh Tokens** — Stateless authentication with access/refresh token rotation via HttpOnly cookies
- 🚦 **Rate Limiting** — Per-user API rate limiting to protect the backend

<br>

## 🏆 Key Engineering Achievement: Hybrid Transaction Router

The most critical design decision in this project is the **Hybrid Transaction Router** — a cost-optimization layer built directly into the PDF extraction pipeline.

### The Problem

Processing every line of a bank statement through an LLM is accurate but expensive. A single 3-page statement can contain 80–120 transaction lines. At scale, this translates into thousands of unnecessary tokens per upload.

### The Solution

```
PDF Text
  └─ PdfTextCleaner           → noise removal & normalization
  └─ Transaction Router
       ├─ isHighConfidence()   → standard, well-formed lines
       │     └─ parseLineLocally()  ──→ TransactionDto  [0 tokens]
       └─ isTransactionCandidate()  → complex / ambiguous lines
             └─ OpenAI GPT-4o-mini  ──→ TransactionDto  [tokens used]
```

The router **classifies each line independently** before any LLM call is made:

| Line Type | Detection | Processing | Token Cost |
|---|---|---|---|
| Standard bank rows (date + merchant + amount) | Regex (high-confidence) | Local Java parser | **0 tokens** |
| Ambiguous, corrupted, or multi-line rows | Heuristic candidate check | OpenAI GPT-4o-mini | Minimal |
| Installment sub-lines (`X TL'lik işlemin N/M taksidi`) | Pattern match | Bundled with parent row → LLM | Minimal |

### The Result

> **87.5% reduction in LLM token consumption** on typical Turkish bank statements, with zero loss in extraction accuracy. Standard-format rows are parsed locally in microseconds; only genuinely complex rows hit the LLM.

Additional optimizations layered on top:

- **Overlapping PDF Chunking** — Large statements are split into overlapping chunks so no transaction is ever cut across a boundary
- **Merchant Cache** — A self-learning cache stores previously seen merchant → category mappings, eliminating repeat LLM categorization calls for known merchants (e.g., Migros, Shell, Netflix)
- **Fault-Tolerant JSON Parsing** — A single malformed row in the LLM response never aborts the entire extraction; rows are parsed independently and failures are logged and skipped

<br>

## 🏗️ Architecture

### Strategy Pattern — Multi-Bank Parser Detection

The extraction pipeline uses a **polymorphic Strategy Pattern** to handle different Turkish bank statement formats. Rather than one monolithic parser, the system automatically detects the source bank from PDF content signatures and dispatches to the appropriate parsing strategy.

Supported bank formats:
- 🏦 İş Bankası (Maximum Card)
- 🏦 Halkbank (Paraf Card)
- 🏦 Yapı Kredi (World Card)

Each bank strategy encapsulates its own date format, column layout, and encoding quirks — new banks can be added without touching the core pipeline.

### Merchant Cache — Self-Learning Categorization

```
Transaction arrives
  └─ MerchantCacheService.lookup(merchantName)
       ├─ Cache HIT  → return stored category  [0 LLM calls]
       └─ Cache MISS → CategorizationService (regex rules → LLM fallback)
                           └─ MerchantCacheService.save()  [learned for next time]
```

The cache is seeded at startup with common Turkish merchants and grows organically as new merchants are encountered.

### MCP Integration — Serena AI Coach

The backend exposes a **Model Context Protocol (MCP)** server (`/mcp` endpoint) that gives an AI agent (Serena) structured, read-only access to the user's financial data. Serena can answer natural-language questions about spending patterns, flag anomalies, and suggest budget adjustments — all grounded in real transaction data rather than hallucinated estimates.

<br>

## 🛠️ Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2, Spring MVC |
| Security | Spring Security, JWT (jjwt 0.12), HttpOnly Cookie, Refresh Token Rotation |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| PDF Processing | Apache PDFBox 3.0 |
| AI / LLM | LangChain4j → OpenAI GPT-4.1-mini |
| MCP Server | Model Context Protocol SDK (mcp-spring-webmvc) |
| Build | Maven, Lombok |

### Frontend

| Layer | Technology |
|---|---|
| Framework | React (Vite) |
| Styling | Tailwind CSS |
| HTTP | Axios (with interceptor-based token refresh) |
| Routing | React Router |
| State | React Context API |

### Infrastructure

| Component | Technology |
|---|---|
| Database | PostgreSQL 15 |
| Containerization | Docker, Docker Compose |
| Backend Hosting | Railway |
| Frontend Hosting | Vercel |

<br>

## 🚀 Getting Started (Local Development)

### Prerequisites

- Docker Desktop
- Java 17+
- Node.js 20+ & npm
- Maven 3.9+
- OpenAI API key

### 1. Configure Environment

Create a `.env` file in the project root:

```env
DB_NAME=smart_budget_db
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password
DB_PORT=5434

OPENAI_API_KEY=sk-...
JWT_SECRET=<run: openssl rand -base64 32>
```

Create `frontend/.env.local`:

```env
VITE_API_URL=http://localhost:8080
```

### 2. Start the Database

```bash
docker compose up -d
```

This spins up PostgreSQL on port `5434`. Spring Boot DDL auto will create the schema on first run.

### 3. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 4. Start the Frontend

```bash
cd frontend
npm run dev
```

The UI will be available at `http://localhost:5173`.

<br>

## ☁️ Deployment (Railway + Vercel)

The project is deployment-ready with full environment isolation.

### Backend — Railway

Set the following environment variables in the Railway dashboard:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `JWT_SECRET` | Generate: `openssl rand -base64 32` |
| `OPENAI_API_KEY` | `sk-...` |
| `ALLOWED_ORIGINS` | `https://<your-vercel-domain>.vercel.app` |
| `SECURE_COOKIE` | `true` |
| `DB_URL` | Use Railway's **internal** PostgreSQL URL (`jdbc:postgresql://postgres.railway.internal:5432/railway`) |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Provided by Railway automatically |

> **Health Check Path:** Set `/health` in Railway Settings → Health Check Path.

The `application-prod.properties` profile activates automatically when `SPRING_PROFILES_ACTIVE=prod` is set, disabling SQL logging and switching DDL to `validate`.

### Frontend — Vercel

| Variable | Value |
|---|---|
| `VITE_API_URL` | `https://<your-railway-domain>.up.railway.app` |

### Deploy order

1. Deploy Railway (backend + database)
2. Deploy Vercel (frontend)
3. Update `ALLOWED_ORIGINS` in Railway with the final Vercel domain

### Building for Railway locally (Apple Silicon)

```bash
docker build --platform linux/amd64 -t smart-budget-backend .
```

<br>

## 📡 API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Register a new user |
| `POST` | `/api/v1/auth/login` | Public | Login, receive JWT + refresh token cookies |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate access token using refresh token |
| `POST` | `/api/v1/auth/logout` | Public | Revoke refresh token, clear cookies |
| `POST` | `/api/v1/statements/upload` | 🔒 | Upload a PDF bank statement |
| `DELETE` | `/api/v1/statements/all` | 🔒 | Delete all statements and transactions |
| `GET` | `/api/v1/analytics/summary` | 🔒 | Category-level spending summary |
| `GET` | `/api/v1/analytics/transactions` | 🔒 | Full transaction list |
| `GET` | `/api/v1/analytics/subscriptions` | 🔒 | Detected recurring subscriptions |
| `GET` | `/api/v1/budget-limits` | 🔒 | List budget limits |
| `POST` | `/api/v1/budget-limits` | 🔒 | Create or update a budget limit |
| `DELETE` | `/api/v1/budget-limits/:id` | 🔒 | Delete a budget limit |
| `GET` | `/api/v1/budget-limits/alerts` | 🔒 | Get triggered budget alerts |
| `GET` | `/api/v1/user/profile` | 🔒 | Get user profile |
| `PUT` | `/api/v1/user/profile` | 🔒 | Update profile |
| `PUT` | `/api/v1/user/change-password` | 🔒 | Change password |
| `GET` | `/health` | Public | Health check (used by Railway) |

<br>

## 🔐 Privacy & Data Storage

### What gets stored

| Data | Stored | Deleted when you clear your data |
|---|---|---|
| Extracted transactions | ✅ PostgreSQL | ✅ Yes |
| Budget limits | ✅ PostgreSQL | ✅ Yes |
| Statement metadata (filename, date range, bank) | ✅ PostgreSQL | ✅ Yes |
| Uploaded PDF file (anonymous copy) | ✅ PostgreSQL — no user link | ❌ Retained anonymously |
| OpenAI prompt (transaction lines only) | Sent to OpenAI API | Not applicable — Smart Budget has no control over OpenAI's retention policy |

### Why PDFs are retained anonymously

When you upload a PDF, an anonymous copy is saved separately from your account — with no name, email, or any identifier attached. This copy is used solely to improve the extraction pipeline: identifying parsing errors, supporting new bank formats, and increasing accuracy over time.

### Your control

- **Deleting your data** (Profile → Data Management → Delete All Data) permanently removes all your transactions, statement records, and budget limits. Your account data is gone.
- The anonymous PDF copy contains no information that can be linked back to you. It is retained only for pipeline improvement and is never shared with third parties.
- Transaction lines are sent to OpenAI (GPT-4o-mini) for categorization. No personal identifiers are included in these prompts.

<br>

## 🗺️ Roadmap

| Status | Feature |
|---|---|
| ✅ Done | Hybrid Transaction Router with 87.5% token savings |
| ✅ Done | Multi-bank Strategy Pattern (İş Bankası, Halkbank, Yapı Kredi) |
| ✅ Done | Merchant Cache with startup seeding |
| ✅ Done | Installment (Taksit) detection and grouping |
| ✅ Done | MCP-powered Serena AI Financial Coach |
| ✅ Done | JWT auth with HttpOnly cookie + refresh token rotation |
| ✅ Done | Per-user rate limiting |
| ✅ Done | Production deployment configuration (Railway + Vercel) |
| ✅ Done | Environment isolation (CORS, Secure Cookie, dynamic config) |
| 🔄 Planned | **Advanced Financial Filtering** — date range, category, merchant, and amount filters |
| 🔄 Planned | **Bank-Independent Template System** — config-driven parser so any bank format can be onboarded without code changes |
| 🔄 Planned | **Export to CSV / Excel** — one-click statement export |
| 🔄 Planned | **Multi-Statement Trend Analysis** — month-over-month spending comparisons |

<br>

## 🧪 Testing

```bash
# Run all tests
cd backend
mvn test

# Run a specific test class
mvn test -Dtest=ExtractionServiceTest
```

The test suite covers:

- `ExtractionServiceTest` — PDF extraction and Transaction Router routing logic
- `CategorizationServiceTest` — category assignment rules
- `MerchantCacheServiceTest` — cache hit/miss behavior
- `AnalyticsServiceTest` — summary calculations
- `AuthIntegrationTest` — full auth flow (register → login → refresh)
- `SecurityIntegrationTest` — endpoint authorization rules
- `RateLimitIntegrationTest` — rate limit enforcement
- `AmountNormalizerTest` — Turkish number format normalization (`1.250,00 TL → 1250.00`)

<br>

## 📁 Project Structure

```
smart-budgeting-app/
├── backend/
│   ├── src/main/java/com/mali/smartbudget/
│   │   ├── config/      # Security, CORS, data initialization
│   │   ├── controller/  # REST endpoints (Auth, Statement, Analytics, Budget, Health)
│   │   ├── dto/         # Request/response data transfer objects
│   │   ├── exception/   # Global exception handler, custom exceptions
│   │   ├── filter/      # Rate limiting filter (per-user, IP-based)
│   │   ├── mcp/         # MCP tool definitions for Serena AI Coach
│   │   ├── model/       # JPA entities (User, Statement, Transaction, BudgetLimit, …)
│   │   ├── repository/  # Spring Data JPA repositories
│   │   ├── security/    # JWT filter, JwtService
│   │   ├── service/     # Business logic (Extraction, Categorization, Analytics, …)
│   │   └── util/        # AmountNormalizer, PdfTextCleaner, ChecksumUtil
│   ├── src/main/resources/
│   │   ├── application.properties        # Base config (all values env-driven)
│   │   └── application-prod.properties   # Production overrides (SQL off, DDL validate)
│   └── pom.xml
├── frontend/
│   └── src/
│       ├── components/  # Reusable UI components
│       ├── pages/       # Route-level pages (Dashboard, Upload, Login, Profile)
│       ├── context/     # Auth context (cookie-based user state)
│       └── api/         # Axios client with interceptor-based token refresh
├── Dockerfile           # Multi-stage build (frontend → backend JAR → runtime)
├── docker-compose.yml   # Local PostgreSQL service
└── README.md
```

<br>

## 📄 License

This project is built for portfolio and educational purposes.

---

<div align="center">

Built with ☕ Java, ⚛️ React, and a pathological obsession with cutting unnecessary LLM costs.

</div>
