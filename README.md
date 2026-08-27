# Resource Booking System

A secure, RESTful Resource Booking System built with **Spring Boot 3 / Java 17**, **Spring Security + JWT**, and **JPA/Hibernate** on **PostgreSQL or MySQL**.

Users can browse resources and manage their own reservations; administrators have full CRUD access to resources and all reservations.

---

## Tech Stack

- Java 17, Spring Boot 3.2.5
- Spring Web, Spring Security, Spring Data JPA
- JWT (jjwt 0.12.x) — stateless authentication
- PostgreSQL or MySQL (JPA/Hibernate)
- Bean Validation (Jakarta Validation)
- springdoc-openapi (Swagger UI)
- JUnit 5 + MockMvc + H2 (tests)
- Lombok, Maven

---

## Project Structure

```
src/main/java/com/booking/
├── BookingApplication.java
├── config/          # SecurityConfig, OpenApiConfig, DataSeeder
├── security/         # JwtUtil, JwtAuthenticationFilter, CustomUserDetailsService,
│                      # JwtAuthenticationEntryPoint (401), JwtAccessDeniedHandler (403)
├── controller/        # AuthController, ResourceController, ReservationController
├── service/           # UserService, ResourceService, ReservationService
├── repository/        # UserRepository, ResourceRepository, ReservationRepository
├── entity/            # User, Role, Resource, Reservation, ReservationStatus
├── dto/                # Request/response DTOs (auth, resource, reservation, error)
└── exception/          # Custom exceptions + GlobalExceptionHandler
```

Clean separation: **Controller → Service → Repository**, with DTOs at the boundary (entities are never returned directly) and a dedicated Security layer.

---

## Getting Started

### 1. Prerequisites

- JDK 17+
- Maven 3.8+
- PostgreSQL 13+ **or** MySQL 8+

### 2. Create the database

**PostgreSQL:**
```sql
CREATE DATABASE booking_db;
```

**MySQL** (or just let `createDatabaseIfNotExist=true` in the JDBC URL handle it):
```sql
CREATE DATABASE booking_db;
```

### 3. Configure environment variables

Copy `.env.example` to `.env` (or export the variables directly) and adjust as needed:

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | `jdbc:postgresql://localhost:5432/booking_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB username |
| `DB_PASSWORD` | `postgres` | DB password |
| `DB_DRIVER` | `org.postgresql.Driver` | JDBC driver class |
| `DB_DIALECT` | `org.hibernate.dialect.PostgreSQLDialect` | Hibernate dialect |
| `DDL_AUTO` | `update` | Hibernate schema strategy (`update`/`validate`/`none`) |
| `SHOW_SQL` | `false` | Log SQL statements |
| `JWT_SECRET` | *(dev default, change in prod)* | HMAC signing key, **min 32 characters** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Token lifetime in ms |
| `LOG_LEVEL` | `INFO` | Log level for `com.booking` package |

> **Note:** If your shell doesn't auto-load `.env`, export the vars manually or use a tool like `direnv`/`dotenv-cli`, or simply pass them as `-D` system properties / `--define` flags when running.

### 4. Run with PostgreSQL (default)

```bash
export DB_URL=jdbc:postgresql://localhost:5432/booking_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=please-change-this-to-a-long-random-secret-value

mvn spring-boot:run
```

### 5. Run with MySQL instead

Activate the `mysql` Spring profile (switches driver/dialect/URL defaults):

```bash
export DB_USERNAME=root
export DB_PASSWORD=root
export JWT_SECRET=please-change-this-to-a-long-random-secret-value

mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 6. Build and run the jar

```bash
mvn clean package
java -jar target/resource-booking-system-1.0.0.jar
```

The app starts on `http://localhost:8080` by default.

---

## Seed / Test Credentials

On first startup, `DataSeeder` automatically creates two accounts (if they don't already exist) plus 3 sample resources:

| Role | Username | Password |
|---|---|---|
| ADMIN | `admin` | `Admin@123` |
| USER  | `user`  | `User@123`  |

Passwords are stored **BCrypt-hashed** — never in plain text.

---

## API Documentation

Once running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI spec:

```
http://localhost:8080/v3/api-docs
```

Click **Authorize** in Swagger UI and paste `Bearer <your-jwt-token>` after logging in via `/auth/login`.

A ready-to-import **Postman collection** is included: [`postman_collection.json`](./postman_collection.json). It includes a pre-request/test script that automatically captures the JWT into collection variables (`adminToken`, `userToken`) after login.

---

## Authentication Flow

1. `POST /auth/login` with `{ "username": "...", "password": "..." }` → returns a JWT.
2. Send the token on every subsequent request: `Authorization: Bearer <token>`.
3. The token carries the username (subject) and role as a claim; **the server always re-derives the authenticated user from the token/DB** — it is never trusted from the request body. This means a USER can never create a reservation "as" someone else, even if they try to pass a different `userId` — there is no such field in the request DTO at all.

`POST /auth/register` is available for self-service **USER** signup (always assigned the `USER` role — you cannot register as ADMIN through the API; admin accounts are provisioned via the seeder).

---

## Roles & Permissions (RBAC)

| Action | ADMIN | USER |
|---|---|---|
| View resources (list/get) | ✅ | ✅ |
| Create / update / delete resources | ✅ | ❌ (403) |
| Create a reservation (for self) | ✅ | ✅ |
| View own reservations | ✅ | ✅ |
| View **any** user's reservations | ✅ | ❌ (403) |
| Cancel own reservation | ✅ | ✅ |
| Cancel another user's reservation | ✅ | ❌ (403) |
| Full update of any reservation (incl. status) | ✅ | ❌ (403) |
| Delete a reservation | ✅ | ❌ (403) |

RBAC is enforced at two levels:
- **Method security** (`@PreAuthorize("hasRole('ADMIN')")`) on admin-only endpoints.
- **Ownership checks in the service layer** for endpoints that both roles can call (e.g. `GET /api/reservations/{id}`, cancel) — a `USER` gets a `403` if they try to touch someone else's reservation, and reservation *listing* is automatically scoped to the caller's own records for `USER`s at the query level (not just filtered client-side).

---

## Endpoints

### Auth (public)
| Method | Path | Description |
|---|---|---|
| POST | `/auth/login` | Authenticate, returns JWT |
| POST | `/auth/register` | Self-register a new USER account |

### Resources
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/resources` | Any authenticated user | Paginated list |
| GET | `/api/resources/{id}` | Any authenticated user | Get by id |
| POST | `/api/resources` | ADMIN | Create |
| PUT | `/api/resources/{id}` | ADMIN | Update |
| DELETE | `/api/resources/{id}` | ADMIN | Delete |

### Reservations
| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/reservations` | Any authenticated user | Filter + paginate + sort (scoped to own for USER, all for ADMIN) |
| GET | `/api/reservations/{id}` | Owner or ADMIN | Get by id |
| POST | `/api/reservations` | Any authenticated user | Create (owner = JWT user) |
| PUT | `/api/reservations/{id}` | ADMIN | Full update, including status |
| PATCH | `/api/reservations/{id}/cancel` | Owner or ADMIN | Cancel |
| DELETE | `/api/reservations/{id}` | ADMIN | Delete |

**Filtering & pagination query params** on `GET /api/reservations`:
- `status` — `PENDING` \| `CONFIRMED` \| `CANCELLED`
- `minPrice`, `maxPrice` — decimal bounds
- `page`, `size` — standard Spring pagination (0-indexed page)
- `sort` — e.g. `sort=startTime,desc` or `sort=price,asc` (repeatable for multi-field sort)

Example:
```
GET /api/reservations?status=PENDING&minPrice=10&maxPrice=200&page=0&size=10&sort=startTime,desc
```

---

## Validation & Error Handling

All input is validated with Jakarta Bean Validation. Examples enforced:
- Reservation `startTime`/`endTime` must be in the future, and `endTime` must be after `startTime`.
- `price` must be a positive decimal.
- Resource `name` is required, `capacity` must be a positive integer.
- Reservation `status`, when supplied, must be one of `PENDING`/`CONFIRMED`/`CANCELLED` (invalid values are rejected with 400 by Jackson enum binding / bean validation).

Errors are returned as a consistent JSON shape:

```json
{
  "timestamp": "2026-08-28T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/reservations",
  "fieldErrors": { "price": "Price must be greater than 0" }
}
```

| Scenario | HTTP Status |
|---|---|
| Validation failure | 400 |
| Missing/invalid/expired JWT | 401 |
| Insufficient role / not the resource owner | 403 |
| Entity not found | 404 |
| Duplicate username/email etc. | 409 |
| Unhandled server error | 500 |

---

## Security Notes

- Passwords hashed with **BCrypt**.
- **Stateless** sessions (`SessionCreationPolicy.STATELESS`) — no server-side session state; every request is authenticated via the JWT.
- CSRF disabled (appropriate for a stateless token-based API, not cookie-session-based).
- JSON-formatted `401`/`403` responses (custom `AuthenticationEntryPoint` / `AccessDeniedHandler`) instead of default HTML error pages.
- The authenticated principal is resolved server-side (`SecurityContextHolder` → DB) for every reservation operation — request bodies never carry a `userId` field, closing off the classic "confused deputy" / IDOR vector where a client could claim someone else's identity.

---

## Running Tests

```bash
mvn test
```

Tests run against an **in-memory H2 database** (`application-test.yml`, `@ActiveProfiles("test")`) so no external DB is required to run the test suite. Coverage includes:

- `AuthControllerTest` — JWT login success/failure, validation errors, BCrypt hashing verification.
- `ResourceControllerSecurityTest` — RBAC on resource CRUD (USER read-only vs ADMIN full access), input validation, anonymous access rejection.
- `ReservationSecurityTest` — ownership enforcement (a USER cannot view/cancel another user's reservation), JWT-derived identity on creation, ADMIN-only update/delete, status+price filtering, invalid-token rejection.

> A note on this delivery: the sandbox this project was authored in has no outbound network access to Maven Central, so `mvn test` could not be executed here to produce a live pass/fail report. Every file was manually reviewed for API/signature correctness against Spring Boot 3.2.5 / Spring Security 6.2 / jjwt 0.12.5. Please run `mvn clean test` in your own environment to confirm — if anything doesn't compile, it is most likely a minor version-pin issue and easy to resolve.

---

## Sample cURL Walkthrough

```bash
# 1. Login as admin
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r .token)

# 2. Create a resource
curl -X POST http://localhost:8080/api/resources \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Studio A","description":"Recording studio","location":"B1","capacity":6,"available":true}'

# 3. Login as user and book it
UTOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"User@123"}' | jq -r .token)

curl -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $UTOKEN" -H "Content-Type: application/json" \
  -d '{"resourceId":1,"startTime":"2027-01-15T10:00:00","endTime":"2027-01-15T12:00:00","price":49.99}'

# 4. List my reservations, filtered
curl -H "Authorization: Bearer $UTOKEN" \
  "http://localhost:8080/api/reservations?status=PENDING&page=0&size=10"
```

---

## License

Provided as-is for evaluation/demo purposes.
