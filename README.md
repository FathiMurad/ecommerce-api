# Enterprise E-Commerce RESTful API

[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker_Compose-Enabled-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A production-ready, secure, and containerized backend REST API for an enterprise e-commerce platform built with **Spring Boot 4**, **Java 21**, **Spring Security (Stateless JWT)**, **Hibernate / Spring Data JPA**, and **PostgreSQL**.

---

## Architectural Highlights

- **Stateless Authentication & RBAC**: JWT-based authentication featuring role separation (`ROLE_CUSTOMER` vs. `ROLE_ADMIN`) with method-level security and route-level authorization filters.
- **Transactional Consistency**: Atomic checkout workflows enforcing strict inventory validation, balance deductions, purchase price snapshots, and shopping cart clearance.
- **Resilient Financial Transactions**: Simulated payment gateway with duplicate payment prevention, financial audit logging, and automated inventory rollbacks on transaction failure.
- **Deterministic Order Lifecycle**: State-machine-governed order progression (`PENDING` -> `CONFIRMED` -> `SHIPPED` -> `DELIVERED` / `CANCELLED`), preventing invalid status transitions and handling stock release on administrative cancellations.
- **Containerized Infrastructure**: Production-grade multi-stage Docker build and multi-container orchestration with PostgreSQL health checks.
- **Automated Quality Gate**: Comprehensive integration testing suite using `MockMvc` and Spring Boot TestContext against live PostgreSQL instances, verified via GitHub Actions CI.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & Platform** | Java 21 (LTS) |
| **Framework** | Spring Boot 4.1.1 (Web, Security, Data JPA, Validation) |
| **Security & Token** | Spring Security, JJWT 0.12.6, BCrypt |
| **Database & ORM** | PostgreSQL 16, Hibernate 7, HikariCP |
| **API Documentation** | OpenAPI 3.1 / Swagger UI (`springdoc-openapi`) |
| **Containerization** | Docker, Docker Compose (Multi-stage build) |
| **Testing** | JUnit 5, MockMvc, AssertJ, Mockito |
| **CI/CD** | GitHub Actions |

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

* **Docker** and **Docker Compose**
* **Java 21 JDK** and **Maven** (optional, wrapper `./mvnw` included)

### 1. Clone Repository

```bash
git clone https://github.com/<your-username>/ecommerce-api.git
cd ecommerce-api
```

### 2. Run with Docker Compose (Recommended)

Launch both the Spring Boot API and the PostgreSQL database container:

```bash
docker compose up --build -d
```

The application will wait for the PostgreSQL container to pass health checks before booting.

### 3. Run Locally with Terminal / IntelliJ

Ensure PostgreSQL is running:

```bash
docker compose up -d postgres
```

Run the application:

```bash
./mvnw spring-boot:run
```

---

## API Documentation (Swagger)

Once the application is running, the interactive Swagger UI and OpenAPI specifications are accessible at:

* **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI Schema**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Seeded Credentials

| Role | Email | Password |
| --- | --- | --- |
| **System Administrator** | `admin@ecommerce.com` | `Admin@123456` |

---

## Core API Endpoints

### Authentication

* `POST /api/auth/register` - Register a customer account and receive a JWT.
* `POST /api/auth/login` - Authenticate and acquire bearer token.

### Catalog (Public & Admin Protected)

* `GET /api/products` - Paged and sorted product catalog.
* `GET /api/products/{id}` - Product details with associated images.
* `POST /api/products` - Create new product (*Admin only*).
* `PUT /api/products/{id}` - Modify product metadata and inventory (*Admin only*).
* `DELETE /api/products/{id}` - Soft/hard delete product (*Admin only*).

### Shopping Cart

* `GET /api/cart` - Retrieve current session cart.
* `POST /api/cart/items` - Add item to cart with stock pre-validation.
* `PUT /api/cart/items/{itemId}` - Update item quantity.
* `DELETE /api/cart/items/{itemId}` - Remove item from cart.

### Order Processing & Lifecycle

* `POST /api/orders/checkout` - Atomic transaction deducting stock and clearing cart.
* `GET /api/orders/my-orders` - Paged order history for authenticated user.
* `GET /api/orders/{trackingNumber}` - Look up order by tracking code.
* `PATCH /api/orders/{trackingNumber}/status` - Advance order lifecycle state (*Admin only*).

### Payments

* `POST /api/payments/process` - Process mock transaction, bind to order, and trigger inventory rollback on failure.

---

## Running the Automated Test Suite

Execute the full suite of integration and security tests:

```bash
./mvnw clean test
```
