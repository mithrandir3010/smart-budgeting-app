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
- 🔐 **JWT Auth + Refresh Tokens** — Stateless authentication with access/refresh token rotation
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
- 🏦 İş Bankası
- 🏦 Halkbank
- 🏦 Yapı Kredi

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
| Language | Java 25 |
| Framework | Spring Boot 3.2, Spring MVC |
| Security | Spring Security, JWT (jjwt 0.12), Refresh Token Rotation |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL |
| PDF Processing | Apache PDFBox 3.0 |
| AI / LLM | LangChain4j 0.35 → OpenAI GPT-4o-mini |
| MCP Server | Model Context Protocol SDK (mcp-spring-webmvc 0.8) |
| Build | Maven, Lombok |

### Frontend

| Layer | Technology |
|---|---|
| Framework | React (Vite) |
| Styling | Tailwind CSS |
| HTTP | Axios |
| Routing | React Router |
| State | React Context API |

### Infrastructure

| Component | Technology |
|---|---|
| Database | PostgreSQL 15 (Docker) |
| Containerization | Docker, Docker Compose |

<br>

## 🚀 Getting Started

### Prerequisites

- Docker Desktop
- Java 21+
- Node.js 20+ & npm
- Maven 3.9+
- OpenAI API key

### 1. Configure Environment

Create a `.env` file in the project root (see `.env.example`):

```env
DB_NAME=smart_budget_db
DB_USERNAME=postgres
DB_PASSWORD=your_secure_password
DB_PORT=5434

OPENAI_API_KEY=sk-...
JWT_SECRET=your_256_bit_secret
```

### 2. Start the Database

```bash
docker compose up -d
```

This spins up PostgreSQL on port `5434`. Spring Boot's DDL auto will create the schema on first run.

### 3. Start the Backend

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### 4. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The UI will be available at `http://localhost:5173`.

<br>

## 📡 API Overview

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Login, receive JWT + refresh token |
| `POST` | `/api/auth/refresh` | Rotate access token using refresh token |
| `POST` | `/api/statements/upload` | Upload a PDF bank statement |
| `GET` | `/api/statements` | List all uploaded statements |
| `GET` | `/api/analytics/summary` | Get category-level spending summary |
| `GET` | `/api/analytics/transactions` | Paginated transaction list |
| `POST` | `/api/budget-limits` | Set a monthly budget limit for a category |
| `GET` | `/api/budget-limits/alerts` | Get triggered budget alerts |

<br>

## 🗺️ Roadmap

| Status | Feature |
|---|---|
| ✅ Done | Hybrid Transaction Router with 87.5% token savings |
| ✅ Done | Multi-bank Strategy Pattern (İş Bankası, Halkbank, Yapı Kredi) |
| ✅ Done | Merchant Cache with startup seeding |
| ✅ Done | Installment (Taksit) detection and grouping |
| ✅ Done | MCP-powered Serena AI Financial Coach |
| ✅ Done | JWT auth with refresh token rotation |
| ✅ Done | Per-user rate limiting |
| 🔄 Planned | **Advanced Financial Filtering** — date range, category, merchant, and amount filters on the transaction list |
| 🔄 Planned | **Bank-Independent Flexible Template System** — a configuration-driven parser template engine so any bank format can be onboarded without code changes |
| 🔄 Planned | **Export to CSV / Excel** — one-click statement export |
| 🔄 Planned | **Multi-Statement Trend Analysis** — month-over-month spending comparisons across multiple uploaded statements |

<br>

## 🧪 Testing

```bash
# Run all tests
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
├── src/main/java/com/mali/smartbudget/
│   ├── config/          # Security, CORS, data initialization
│   ├── controller/      # REST endpoints (Auth, Statement, Analytics, Budget)
│   ├── dto/             # Request/response data transfer objects
│   ├── exception/       # Global exception handler, custom exceptions
│   ├── filter/          # Rate limiting filter (per-user, IP-based)
│   ├── mcp/             # MCP tool definitions for Serena AI Coach
│   ├── model/           # JPA entities (User, Statement, Transaction, BudgetLimit, …)
│   ├── repository/      # Spring Data JPA repositories
│   ├── security/        # JWT filter, JwtService
│   ├── service/         # Business logic (Extraction, Categorization, Analytics, …)
│   └── util/            # AmountNormalizer, PdfTextCleaner, ChecksumUtil
├── frontend/
│   └── src/
│       ├── components/  # Reusable UI components (BudgetGuard, TransactionsTable, …)
│       ├── pages/       # Route-level pages (Dashboard, Upload, Login, Profile)
│       ├── context/     # Auth context (JWT storage, user state)
│       └── api/         # Axios API client
├── docker-compose.yml   # PostgreSQL service definition
└── pom.xml
```

<br>

## 📄 License

This project is built for portfolio and educational purposes.

---

<div align="center">

Built with ☕ Java, ⚛️ React, and a pathological obsession with cutting unnecessary LLM costs.

</div>
