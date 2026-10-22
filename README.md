# 🦅 PriceHawk — Smart E-Commerce Price Tracker (Amazon + Flipkart)

[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![React 18](https://img.shields.io/badge/React-18.3-blue.svg)](https://react.dev/)
[![Chrome Extension](https://img.shields.io/badge/Chrome%20Extension-Manifest%20V3-yellow.svg)](https://developer.chrome.com/docs/extensions/mv3/intro/)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL%2015-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![Tests Passing](https://img.shields.io/badge/Tests-106%20Backend%20%7C%2018%20Frontend%20Passed-success.svg)](#testing)

**PriceHawk** is a full-stack, automated e-commerce price tracking and alert ecosystem designed for **Amazon India** and **Flipkart**. It empowers shoppers to track product prices with a single click, view synchronized historical price charts, compare deals across competing stores side-by-side, and receive instantaneous drop alerts via in-app notifications and email.

---

## 🌟 Key Features

### 🛒 1. One-Click Tracking from Browser
- Automatically detects active Amazon or Flipkart product pages.
- Instantly captures title, high-resolution thumbnail, live price, and currency directly from DOM or fallback server-side scraping.
- Eliminates manual copy-pasting—simply click **"Track Current Tab Product"**.

### 🔁 2. Cross-Platform Matching & Comparison
- Automatically discovers counterpart products across competing platforms (e.g., finds the corresponding Flipkart item for an Amazon product and vice versa).
- Highlights the **Best Deal** with dynamic green badges and price differential calculations.
- Allows manual fallback linking using "+ Paste URL" if auto-discovery is ambiguous.

### 📈 3. Interactive SVG Price History Analytics
- Synchronized dual-store timeline chart rendering exact historical price snapshots.
- Real-time statistics: **All-Time Low**, **Current Price**, and **All-Time High**.
- Dedicated tabs to switch between Amazon and Flipkart trendlines.
- **Dynamic Live Reloading**: The graph instantly updates upon manual refresh or auto-link without closing the accordion.

### 🔔 4. Dual Alert Modes
- **Normal Alert Mode (Default)**: Automatically triggers whenever the price drops below its previous verified price.
- **Target Price Mode**: Enter a custom target budget to only be notified when the price meets or falls below your specific threshold.
- Seamlessly toggle or remove budget limits with one click to switch back to normal mode.

### 📬 5. Smart Notification Center & Email Alerts
- Tab badge counter displays strictly **unread** notifications (resets to 0 once all alerts are acknowledged).
- Relative timestamps (`🕒 Just now`, `2h ago`) with read confirmation tags.
- **Automated 48-Hour Cleanup**: Read alerts are automatically purged from both the database and dashboard after 2 days.
- **Multi-Channel Email Delivery**: Integrated with **Brevo (Sendinblue)** and **Resend** for instant HTML alert dispatch to registered user emails.

### 🛡️ 6. Resilient Anti-Bot Scraping Engine
- Upgraded multi-tier scraper with realistic Chrome 124 browser headers (`sec-ch-ua`, `User-Agent`, `Accept-Language`).
- Gracefully handles Flipkart `503 Service Unavailable` / Akamai challenges using fallback snapshot guarantees—ensuring dual-store price timelines never drop out of sync.

---

## 🏛️ Architecture Overview

```
 ┌────────────────────────────────────────────────────────────┐
 │                  Chrome Browser Extension                  │
 │      (React 18 + Vite + Manifest V3 + Chrome Storage)      │
 └─────────────┬────────────────────────────────┬─────────────┘
               │                                │
        HTTP / JSON REST                 Content Scripts
        (JWT Auth Header)             (DOM Metadata Extract)
               │                                │
               ▼                                ▼
 ┌────────────────────────────────────────────────────────────┐
 │               PriceHawk Spring Boot 3 Backend              │
 │  ┌──────────────────────────────────────────────────────┐  │
 │  │ Controllers: Auth, Product, Alert, Health, Export   │  │
 │  ├──────────────────────────────────────────────────────┤  │
 │  │ Services: Product, Tracking, CrossSearch, Email     │  │
 │  ├──────────────────────────────────────────────────────┤  │
 │  │ Anti-Bot Scraper Engine: Jsoup + RestOperations      │  │
 │  ├──────────────────────────────────────────────────────┤  │
 │  │ Schedulers: PriceCheckScheduler (Hourly / Fixed)    │  │
 │  └──────────────────────────────────────────────────────┘  │
 └─────────────┬────────────────────────────────┬─────────────┘
               │                                │
               ▼                                ▼
 ┌───────────────────────────┐   ┌────────────────────────────┐
 │   PostgreSQL (Supabase)   │   │ Email Services             │
 │   - Users & Item Sources  │   │ - Brevo (Transactional API)│
 │   - Price History Points  │   │ - Resend (Fallback API)    │
 │   - Smart Price Alerts    │   └────────────────────────────┘
 └───────────────────────────┘
```

---

## 🧰 Tech Stack

| Layer | Technologies |
|---|---|
| **Browser Extension** | React 18, Vite, JavaScript (ES2022), Chrome Extensions API (Manifest V3), Vanilla CSS Glassmorphism |
| **Backend Core** | Java 21 LTS, Spring Boot 3.3.4, Spring Security, Spring Web, Spring Data JPA |
| **Scraping Engine** | Jsoup 1.18.1, Spring RestOperations / RestTemplate, Custom Regex Parsers |
| **Database** | PostgreSQL 15 (Supabase Cloud) in Production, H2 In-Memory for Unit Tests |
| **Authentication** | Stateless JWT (JSON Web Tokens) with BCrypt password hashing |
| **Email Providers** | Brevo Transactional Email API, Resend Email API |
| **Testing** | JUnit 5, Mockito, AssertJ, Spring MockMvc, Vitest, React Testing Library |
| **Containerization** | Docker Multi-Stage Build, Eclipse Temurin JRE 21, Render Cloud Deployment |

---

## 📂 Project Structure

```text
PriceHawk/
├── backend/                             # Spring Boot 3 Application
│   ├── src/
│   │   ├── main/java/com/pricehawk/
│   │   │   ├── config/                  # SecurityConfig, CorsConfig, DatabaseSchemaInitializer
│   │   │   ├── controller/              # REST Endpoints (Auth, Products, Alerts, Health)
│   │   │   ├── dto/                     # Request/Response Data Transfer Objects
│   │   │   ├── model/                   # JPA Entities (User, TrackedItem, ItemSource, PriceHistory, PriceAlert)
│   │   │   ├── repository/              # Spring Data JPA Repositories
│   │   │   ├── scheduler/               # Hourly PriceCheckScheduler & Alert Auto-Purge
│   │   │   ├── scraper/                 # Multi-Tier Scraper (Amazon, Flipkart parsers)
│   │   │   └── service/                 # Business Logic & Brevo/Resend Email Integrations
│   │   └── test/java/com/pricehawk/     # 106 Unit & MockMvc Integration Tests
│   ├── Dockerfile                       # Multi-stage optimized production container
│   ├── .dockerignore                    # Build ignore rules
│   └── pom.xml                          # Maven build dependencies
│
├── extension/                           # Chrome Extension (Manifest V3)
│   ├── src/
│   │   ├── components/                  # PriceHistoryChart & UI components
│   │   ├── content/                     # Content scripts for Amazon & Flipkart tab scraping
│   │   ├── services/                    # Axios/Fetch API client communicating with backend
│   │   ├── test/                        # 18 Vitest + React Testing Library test suites
│   │   └── App.jsx                      # Main Popup Extension UI & State Management
│   ├── manifest.json                    # Chrome Extension Manifest V3 configuration
│   ├── package.json                     # Vite & frontend dependencies
│   ├── vite.config.js                   # Extension bundle config
│   ├── .env.example                     # Environment template
│   └── dist/                            # Production build artifact
│
└── PriceHawk-Extension-v1.0.0.zip       # Pre-packaged, ready-to-load Chrome Extension
```

---

## 🚀 Getting Started

### 1. Prerequisites
- **Java**: 21 LTS (or Java 17+)
- **Maven**: 3.9+
- **Node.js**: 18+ and npm
- **Database**: PostgreSQL (or cloud Supabase instance)

---

### 2. Backend Setup & Run

#### A. Configure Environment Variables
Create a `.env` file in `/backend` (or set system environment variables):
```env
PORT=8080
DB_URL=jdbc:postgresql://localhost:5432/pricehawk
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=your_super_secret_jwt_key_at_least_32_characters_long
BREVO_API_KEY=your_brevo_api_key_optional
RESEND_API_KEY=your_resend_api_key_optional
ALERT_FROM_EMAIL=alerts@pricehawk.com
```

#### B. Run Automated Tests
Runs all 106 tests against an in-memory H2 database (zero PostgreSQL setup required):
```bash
cd backend
mvn clean test
```

#### C. Run the Backend Locally
```bash
cd backend
mvn spring-boot:run
```
Backend will start on `http://localhost:8080`. Health check endpoint:
```bash
curl http://localhost:8080/health
# Response: {"status":"UP"}
```

---

### 3. Extension Setup & Run

#### A. Install Dependencies
```bash
cd extension
npm install
```

#### B. Configure Extension `.env`
Create `/extension/.env`:
```env
# Local Development:
VITE_API_BASE_URL=http://localhost:8080

# Production Deployment (e.g. Render):
# VITE_API_BASE_URL=https://your-backend.onrender.com
```

#### C. Run Extension Tests
```bash
npm test -- --run
```

#### D. Build the Extension
```bash
npm run build
```

#### E. Load into Google Chrome
1. Open Google Chrome and browse to `chrome://extensions/`.
2. Turn ON the **Developer mode** switch (top-right corner).
3. Click **Load unpacked**.
4. Select the **`PriceHawk/extension/dist`** directory.
5. Pin **PriceHawk** to your browser toolbar!

---

## 🌐 API Reference

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/auth/register` | Register new user account | No |
| `POST` | `/auth/login` | Authenticate user & receive JWT | No |
| `GET` | `/health` | Application health status | No |
| `GET` | `/api/products` | Get user's tracked products with sources | Yes (JWT) |
| `POST` | `/api/products` | Track a new product (Amazon or Flipkart) | Yes (JWT) |
| `DELETE` | `/api/products/{id}` | Delete a tracked product & cascades | Yes (JWT) |
| `POST` | `/api/products/{id}/refresh` | Trigger live price refresh for all sources | Yes (JWT) |
| `POST` | `/api/products/{id}/sources` | Auto-match or manually link counterpart store | Yes (JWT) |
| `PATCH` | `/api/products/{id}/alerts` | Update target price & alert mode | Yes (JWT) |
| `GET` | `/api/products/{id}/history` | Get price history points for chart | Yes (JWT) |
| `GET` | `/api/alerts` | Get price drop alerts (auto-purges >48h read) | Yes (JWT) |
| `PATCH` | `/api/alerts/{id}/read` | Mark alert as read | Yes (JWT) |

---

## 🚢 Production Deployment (Render + Docker)

PriceHawk backend includes an enterprise multi-stage [`backend/Dockerfile`](file:///Users/ronakgondaliya/Ronak/PriceHawk/backend/Dockerfile).

1. **Deploy to Render**:
   - Create a new **Web Service** on Render connected to your repository.
   - **Root Directory**: `backend`
   - **Runtime**: `Docker`
   - **Environment Variables**: Add `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `BREVO_API_KEY`.
2. **Configure Extension for Production**:
   - In `extension/.env`, set `VITE_API_BASE_URL=https://<your-render-service>.onrender.com`.
   - Run `npm run build && cd dist && zip -r ../../PriceHawk-Extension-v1.0.0.zip ./*`.
   - Distribute the resulting `PriceHawk-Extension-v1.0.0.zip` to users!

---

## 🧪 Testing Summary

- **Backend**: **106 / 106** unit & integration tests passing (`JUnit 5`, `Mockito`, `MockMvc`, H2).
- **Extension**: **18 / 18** frontend test suites passing (`Vitest`, `React Testing Library`).
- Coverage spans: Authentication, CORS filters, multi-tier scraping fallback, resilient dual-store sync, alert triggers, 48-hour purge, and UI interactions.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
