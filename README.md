# Enterprise E-Commerce RESTful API

[![CI](https://github.com/FathiMurad/ecommerce-api/actions/workflows/ci.yml/badge.svg)](https://github.com/FathiMurad/ecommerce-api/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker_Compose-Enabled-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Prometheus](https://img.shields.io/badge/Prometheus-Metrics-E6522C?logo=prometheus&logoColor=white)](https://prometheus.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-minded, secure, and containerized backend REST API for an enterprise supermarket e-commerce platform built with **Spring Boot 4.1.2**, **Java 21**, **Spring Security (Stateless JWT)**, **Hibernate / Spring Data JPA**, and **PostgreSQL**.

---

## Architectural Highlights

- **Stateless Authentication & RBAC**: JWT-based authentication featuring role separation (`ROLE_CUSTOMER` vs. `ROLE_ADMIN`), route-level authorization, IDOR ownership enforcement, and HMAC-SHA256 password pepper hashing.
- **Transactional Consistency & Atomic Inventory Control**: Atomic checkout workflows enforcing conditional SQL stock decrements (`UPDATE ... WHERE stock_quantity >= :qty`) combined with JPA optimistic locking (`@Version`), purchase price snapshots, and automated cart clearance.
- **Asynchronous Domain Events**: Decoupled order event processing via `@TransactionalEventListener(phase = AFTER_COMMIT)` backed by a dedicated bounded `ThreadPoolTaskExecutor`.
- **Brute-Force & DoS Mitigation**: Thread-safe Caffeine-backed Token-Bucket rate limiting filter isolating client IP addresses (`getRemoteAddr()`) across authentication, checkout, payment, and all cart endpoints (`HTTP 429 Too Many Requests`), paired with `email + IP` scoped login lockout to prevent remote account-lockout DoS.
- **Observability & Metrics**: Micrometer & Spring Boot Actuator integration exposing Prometheus metrics (`/actuator/prometheus`), post-commit business counters (`ecommerce.orders.created`, `ecommerce.orders.confirmed`, `ecommerce.orders.cancelled`, `ecommerce.payments.processed`), and payment latency timers (`ecommerce.payment.processing.time`).
- **Resilient Payment & Lifecycle Management**: Single payment record per order with Luhn and expiry validation, automatic order cancellation and stock restoration after 5 consecutive payment declines, scheduled expiration of unpaid `PENDING` orders after 30 minutes (`PendingOrderExpiryJob`), and automatic `REFUNDED` transition when a paid order is cancelled.
- **Caching & Lazy Guest Cart Lifecycle**: Caffeine product catalog cache (2-minute TTL with immediate eviction on checkout, cancellation, or product updates) and lazy guest cart persistence with explicit activity timestamps (`markActive()`) and daily cleanup of carts inactive for over 30 days (`GuestCartCleanupJob`).
- **Containerized Infrastructure**: Multi-stage Docker build running under an unprivileged user (`appuser`), orchestrated alongside PostgreSQL with automated health checks.
- **Automated Quality Gate**: 59 automated integration and service tests using `MockMvc` against a live PostgreSQL instance, verified via GitHub Actions CI.

---

## Design Decisions

- **Inventory & Payment Concurrency**: Stock is decremented using a single conditional query (`UPDATE ... WHERE stock_quantity >= :qty`) alongside `@Version`, ensuring concurrent checkouts never oversell and losing requests receive a clean `409 Conflict` (`Insufficient stock`). Duplicate concurrent checkouts on the same cart token and duplicate concurrent payment requests on the same pending order are both guarded via optimistic locking and unique database constraints. Verified by `ConcurrencyIntegrationTest`.
- **IDOR & Guest Order Protection**: Orders belonging to registered users return `404 Not Found` (rather than `403 Forbidden`) to non-owners, preventing tracking number enumeration. Email-based order search (`GET /api/orders?customerEmail=...`) is restricted exclusively to `ROLE_ADMIN` so newly registered accounts cannot access historical guest orders sharing the same email address.
- **Payment Failures & Order Expiry**: A declined card keeps the order `PENDING` so the customer can retry with another card; on the 5th consecutive failure, the order is automatically cancelled and inventory is restored. Unpaid `PENDING` orders expire after 30 minutes via `PendingOrderExpiryJob` using `cancelIfPending` so an order paid concurrently is never cancelled.
- **Lazy Guest Carts**: Guest carts are created in the database only when the first item is added (`GET /api/cart` never inserts a database row). Every cart mutation or retrieval explicitly calls `markActive()` so `GuestCartCleanupJob` only purges genuinely abandoned carts inactive for over 30 days.
- **Transactional Integrity of Metrics & Events**: Business counters and asynchronous `OrderPlacedEvent` listeners execute only after database transaction commit (`afterCommit` / `AFTER_COMMIT`), preventing ghost metrics or emails on rolled-back transactions.

---

## Known Limitations

- **Simulated Payment Gateway**: Payment processing is an internal simulator (`PAYPAL` and `CASH_ON_DELIVERY` are auto-approved; cancelling a `CONFIRMED` paid order marks the payment `REFUNDED` without calling an external processor).
- **Schema Management**: Production configuration defaults to `spring.jpa.hibernate.ddl-auto=validate` (while `local` and `test` profiles use `update`), rather than versioned Flyway/Liquibase migrations.
- **Single-Instance State**: Rate limiter buckets and `@Scheduled` jobs run in-memory/per-node; multi-instance horizontal scaling would require distributed rate limiting (e.g., Redis) and distributed scheduler locks (e.g., ShedLock).
- **Actuator Exposure**: `/actuator/prometheus` is publicly accessible for local/portfolio monitoring and should be restricted at the network or ingress layer in production.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Platform** | Java 21 (LTS) |
| **Framework** | Spring Boot 4.1.2 (Web, Security, Data JPA, Validation, Cache, Actuator) |
| **Security & Cryptography** | Spring Security (Boot 4.1.2), JJWT 0.12.6, BCrypt + Global HMAC-SHA256 Pepper |
| **Database & ORM** | PostgreSQL 16, Hibernate 7, HikariCP Connection Pool |
| **Caching, Concurrency & Jobs** | Caffeine Cache, `ThreadPoolTaskExecutor`, Spring `@Scheduled`, Domain Events |
| **Observability** | Micrometer, Prometheus Metrics Registry, Spring Boot Actuator |
| **API Documentation** | OpenAPI 3.1 / Swagger UI (`springdoc-openapi` 3.2.0) |
| **Containerization** | Docker (Multi-stage build, Alpine JRE 21), Docker Compose |
| **Testing** | JUnit 5, MockMvc, AssertJ, Spring Boot TestContext (59 tests) |
| **CI/CD** | GitHub Actions Pipeline |

---

## Domain Model & Architecture

```text
  +---------------+          +---------------+          +---------------+
  |     User      | 1      * |     Cart      | 1      * |   CartItem    |
  |---------------|<-------->|---------------|<-------->|---------------|
  | id (PK)       |          | id (PK)       |          | id (PK)       |
  | email (UQ)    |          | user_id (FK)  |          | product_id(FK)|
  | password_hash |          | cart_token    |          | quantity      |
  | role (ENUM)   |          +---------------+          +---------------+
  +---------------+                                             | *
          | 1                                                   |
          |                                                     | 1
          | *                                           +---------------+          +---------------+
  +---------------+          +---------------+          |    Product    | *      1 |   Category    |
  |     Order     | 1      * |   OrderItem   | *      1 |---------------|<-------->|---------------|
  |---------------|<-------->|---------------|<-------->| id (PK)       |          | id (PK)       |
  | id (PK)       |          | id (PK)       |          | category_id   |          | name (UQ)     |
  | tracking_no   |          | order_id (FK) |          | name          |          | description   |
  | total_amount  |          | product_id(FK)|          | price         |          +---------------+
  | status (ENUM) |          | price_snapshot|          | stock_quantity|
  +---------------+          +---------------+          +---------------+
          | 1                                                   | 1
          |                                                     |
          | 1                                                   | *
  +---------------+                                     +---------------+
  |    Payment    |                                     | ProductImage  |
  |---------------|                                     |---------------|
  | id (PK)       |                                     | id (PK)       |
  | transaction_id|                                     | product_id(FK)|
  | failed_attempt|                                     | image_url     |
  | payment_status|                                     +---------------+
  +---------------+
```

---

## Getting Started

### Prerequisites

- **Docker Engine** & **Docker Compose**
- **Java 21 JDK** & **Maven** (optional, wrapper `./mvnw` provided)

### 1. Clone Repository & Setup Environment

```bash
git clone [https://github.com/FathiMurad/ecommerce-api.git](https://github.com/FathiMurad/ecommerce-api.git)
cd ecommerce-api

# Copy example environment configuration
cp .env.example .env
```

Before starting with Docker Compose, populate `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET_KEY`, and `SECURITY_PEPPER` in `.env` (for example using `openssl rand -base64 48`), as the default production configuration fails fast when required secrets or database credentials are unset. Optionally set `ADMIN_EMAIL` / `ADMIN_PASSWORD`, `CUSTOMER_EMAIL` / `CUSTOMER_PASSWORD`, and `CATALOG_SEEDING_ENABLED=true` to bootstrap initial accounts and catalog data.

### 2. Run with Docker Compose (Recommended)

Build and launch the containerized application and PostgreSQL database:

```bash
docker compose up --build -d
```

The API service automatically awaits PostgreSQL healthiness before starting. Verify status via:

```bash
docker compose ps
```

### 3. Run Locally (Development Mode)

Start the PostgreSQL service container:

```bash
docker compose up -d postgres
```

Run the Spring Boot application with the `local` profile enabled:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

---

## API Documentation & Observability

### Interactive Documentation

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI Schema**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Production Observability Endpoints

- **Health Status**: `GET /actuator/health`
- **Prometheus Metrics**: `GET /actuator/prometheus`
- **Application Info**: `GET /actuator/info`

### Seeded Credentials

> **Development only.** Initial accounts and demo catalog products are disabled by default in production (`CATALOG_SEEDING_ENABLED=false`) and are created only when explicitly configured via environment variables. The `local` Spring profile pre-populates the credentials below for local development:

| Role | Email | Password |
| --- | --- | --- |
| **System Administrator** | `admin@ecommerce.com` | `Admin@123456` |
| **Default Customer** | `customer@ecommerce.com` | `Customer@123456` |

---

## Core API Endpoints

### Authentication & Security *(Rate Limited)*

- `POST /api/auth/register` - Register a customer account (minimum 8-character password) and receive a JWT.
- `POST /api/auth/login` - Authenticate and acquire bearer token.

### Catalog Management

- `GET /api/products` - Paged and sorted product catalog (Public).
- `GET /api/products/{id}` - Product details with associated images (Public, cached).
- `POST /api/products` - Create new product (*Admin only*).
- `PUT /api/products/{id}` - Modify product metadata and inventory (*Admin only*).
- `DELETE /api/products/{id}` - Hard delete product; returns `409 Conflict` if referenced in existing orders (*Admin only*).

### Shopping Cart *(Rate Limited)*

- `GET /api/cart` - Retrieve current session cart (returns empty representation without DB write if none exists).
- `POST /api/cart/items` - Add item to cart with stock pre-validation.
- `PUT /api/cart/items/{productId}` - Update item quantity.
- `DELETE /api/cart/items/{productId}` - Remove item from cart.
- `DELETE /api/cart` - Clear all items from cart.

### Order Processing & Lifecycle *(Rate Limited Checkout)*

- `POST /api/orders/checkout` - Atomic transaction deducting stock, creating a `PENDING` order, evicting product cache, and firing asynchronous events.
- `GET /api/orders/my-orders` - Paged order history for authenticated user.
- `GET /api/orders/{trackingNumber}` - Look up order details by 128-bit tracking code (enforces IDOR ownership policy).
- `GET /api/orders?customerEmail=...` - Search orders by customer email (*Admin only*).
- `PATCH /api/orders/{trackingNumber}/status` - Advance order lifecycle state (*Admin only*; blocks confirming unpaid orders and marks paid orders `REFUNDED` on cancellation).

### Payments & Auditing *(Rate Limited)*

- `POST /api/payments/orders/{trackingNumber}` - Process simulated payment, record payment audit, and automatically cancel order / restore stock after 5 consecutive failures.

### Payment Gateway Simulation

The payment subsystem operates as an internal simulated gateway for development and testing:
- **Card Payments (`CREDIT_CARD`, `DEBIT_CARD`)**: Require full card details (`cardNumber` passing Luhn validation, `cardHolderName`, non-expired `expiryDate`, and `cvv`). Any 16-digit card number ending in `0000` deterministically simulates an issuing-bank decline (`FAILED`).
- **Alternative Methods (`PAYPAL`, `CASH_ON_DELIVERY`)**: Do not require card credentials and are automatically approved by the simulator.

---

## Running the Automated Test Suite

Execute the full suite of 59 automated integration and service tests (JUnit 5, MockMvc, AssertJ) against a live local PostgreSQL instance:

```bash
./mvnw clean verify
```