# Enterprise E-Commerce RESTful API

[![CI](https://github.com/FathiMurad/ecommerce-api/actions/workflows/ci.yml/badge.svg)](https://github.com/FathiMurad/ecommerce-api/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker_Compose-Enabled-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![Prometheus](https://img.shields.io/badge/Prometheus-Metrics-E6522C?logo=prometheus&logoColor=white)](https://prometheus.io/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-ready, secure, and containerized backend REST API for an enterprise supermarket e-commerce platform built with **Spring Boot 4**, **Java 21**, **Spring Security (Stateless JWT)**, **Hibernate / Spring Data JPA**, and **PostgreSQL**.

---

## Architectural Highlights

- **Stateless Authentication & RBAC**: JWT-based authentication featuring role separation (`ROLE_CUSTOMER` vs. `ROLE_ADMIN`) with route-level authorization and HMAC-SHA256 password pepper hashing.
- **Transactional Consistency & Pessimistic Locking**: Atomic checkout workflows enforcing strict inventory validation, balance deductions, purchase price snapshots, and automated cart clearance.
- **Asynchronous Domain Events**: Decoupled order event processing via `@TransactionalEventListener(phase = AFTER_COMMIT)` backed by a dedicated bounded `ThreadPoolTaskExecutor`.
- **Brute-Force & DoS Mitigation**: Custom, thread-safe in-memory Token-Bucket rate limiting filter isolating client IP addresses across authentication and checkout endpoints (`HTTP 429 Too Many Requests`).
- **Production Observability & Metrics**: Micrometer & Spring Boot Actuator integration exposing Prometheus metrics (`/actuator/prometheus`), business counters (`ecommerce.orders.placed`), and payment latency timers.
- **Resilient Financial Transactions**: Payment gateway simulation featuring duplicate transaction prevention, financial audit logging, and automated inventory rollbacks on failure.
- **Deterministic Order Lifecycle**: State-machine-governed order progression (`PENDING` -> `CONFIRMED` -> `SHIPPED` -> `DELIVERED` / `CANCELLED`), preventing illegal state transitions and automatically restoring inventory upon cancellation.
- **Containerized Infrastructure**: Production-grade multi-stage Docker build running under an unprivileged user (`appuser`), orchestrated alongside PostgreSQL with automated health checks.
- **Automated Quality Gate**: Comprehensive integration test suite using `MockMvc` against live PostgreSQL instances, verified via GitHub Actions CI pipeline.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Platform** | Java 21 (LTS) |
| **Framework** | Spring Boot 4.1.1 (Web, Security, Data JPA, Validation, Actuator) |
| **Security & Cryptography** | Spring Security 6, JJWT 0.12.6, BCrypt + Global HMAC-SHA256 Pepper |
| **Database & ORM** | PostgreSQL 16, Hibernate 7, HikariCP Connection Pool |
| **Concurrency & Async** | Spring Task Execution (`ThreadPoolTaskExecutor`), Domain Application Events |
| **Observability** | Micrometer, Prometheus Metrics Registry, Spring Boot Actuator |
| **API Documentation** | OpenAPI 3.1 / Swagger UI (`springdoc-openapi`) |
| **Containerization** | Docker (Multi-stage build, Alpine JRE 21), Docker Compose |
| **Testing** | JUnit 5, MockMvc, AssertJ, Spring Boot TestContext |
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
  | payment_status|                                     | image_url     |
  +---------------+                                     +---------------+

```

---

## Getting Started

### Prerequisites

* **Docker Engine** & **Docker Compose**
* **Java 21 JDK** & **Maven** (optional, wrapper `./mvnw` provided)

### 1. Clone Repository & Setup Environment

```bash
git clone https://github.com/FathiMurad/ecommerce-api.git
cd ecommerce-api

# Copy example environment configuration
cp .env.example .env

```

Ensure secrets in `.env` are set or use default development credentials.

### 2. Run with Docker Compose (Recommended)

Build and launch the containerized application and PostgreSQL database:

```bash
docker compose up --build -d

```

The API service will automatically await PostgreSQL healthiness before starting. Verify status via:

```bash
docker compose ps

```

### 3. Run Locally (Development Mode)

Start the PostgreSQL service container:

```bash
docker compose up -d postgres

```

Run the Spring Boot application using Maven:

```bash
./mvnw spring-boot:run

```

---

## API Documentation & Observability

### Interactive Documentation

* **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI Schema**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Production Observability Endpoints

* **Health Status**: `GET /actuator/health`
* **Prometheus Metrics**: `GET /actuator/prometheus`
* **Application Info**: `GET /actuator/info`

### Seeded Credentials

| Role | Email | Password |
| --- | --- | --- |
| **System Administrator** | `admin@ecommerce.com` | `Admin@123456` |
| **Default Customer** | `customer@ecommerce.com` | `Customer@123456` |

---

## Core API Endpoints

### Authentication & Security *(Rate Limited)*

* `POST /api/auth/register` - Register a customer account and receive a JWT.
* `POST /api/auth/login` - Authenticate and acquire bearer token.

### Catalog Management

* `GET /api/products` - Paged and sorted product catalog (Public).
* `GET /api/products/{id}` - Product details with associated images (Public).
* `POST /api/products` - Create new product (*Admin only*).
* `PUT /api/products/{id}` - Modify product metadata and inventory (*Admin only*).
* `DELETE /api/products/{id}` - Soft/hard delete product (*Admin only*).

### Shopping Cart

* `GET /api/cart` - Retrieve current session cart.
* `POST /api/cart/items` - Add item to cart with stock pre-validation.
* `PUT /api/cart/items/{itemId}` - Update item quantity.
* `DELETE /api/cart/items/{itemId}` - Remove item from cart.

### Order Processing & Lifecycle *(Rate Limited Checkout)*

* `POST /api/orders/checkout` - Atomic transaction deducting stock, creating order, and firing asynchronous events.
* `GET /api/orders/my-orders` - Paged order history for authenticated user.
* `GET /api/orders/{trackingNumber}` - Look up order details by tracking code.
* `PATCH /api/orders/{trackingNumber}/status` - Advance order lifecycle state (*Admin only*).

### Payments & Auditing

* `POST /api/payments/process` - Process mock transaction, record payment audit, and trigger rollback on failure.

---

## Running the Automated Test Suite

Execute the full suite of integration, security, and repository tests against live PostgreSQL:

```bash
./mvnw clean verify

```