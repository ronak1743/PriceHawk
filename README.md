# PriceHawk - Price Tracker (Amazon + Flipkart)

A price tracking browser extension (Chrome Manifest V3) with a Spring Boot backend.

## Architecture

- **Backend (`/backend`)**: Spring Boot 3, Java 21/17+, PostgreSQL (runtime), H2 (in-memory for tests), Spring Data JPA.
- **Extension (`/extension`)**: React with plain JavaScript, Vite, Chrome Manifest V3, Vitest + React Testing Library.

---

## Getting Started

### 1. Prerequisites
- **Java**: 17+ (Java 21 LTS recommended)
- **Maven**: 3.8+
- **Node.js**: 18+ and npm
- **PostgreSQL**: (optional for running tests, required for local backend run)

---

### 2. Backend Setup & Run

#### Running Tests
Runs tests against in-memory H2 database (no PostgreSQL required):
```bash
cd backend
mvn clean test
```

#### Running Application
Make sure PostgreSQL is running and a database named `pricehawk` exists (or override via environment variables):
```bash
export DB_URL=jdbc:postgresql://localhost:5432/pricehawk
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

cd backend
mvn spring-boot:run
```
The backend server runs on `http://localhost:8080`.

#### Health Check
```bash
curl -i http://localhost:8080/health
```
Response:
```json
{"status":"UP"}
```

---

### 3. Extension Setup & Run

#### Install Dependencies
```bash
cd extension
npm install
```

#### Run Tests
```bash
npm test
```

#### Build Extension for Chrome
```bash
npm run build
```

To load in Chrome:
1. Open Chrome and navigate to `chrome://extensions/`.
2. Enable **Developer mode** (top right toggle).
3. Click **Load unpacked** and select the `/extension` (or `/extension/dist` as configured) folder.
