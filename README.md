# Resource Booking System

A secure, RESTful Resource Booking System built with **Spring Boot 3 / Java 17**, **Spring Security + JWT**, and **JPA/Hibernate** on **MySQL**.

Users can browse resources and manage their own reservations; administrators have full CRUD access to resources and all reservations.

---

## Tech Stack

- Java 17, Spring Boot 3.2.5
- Spring Web, Spring Security, Spring Data JPA
- JWT (jjwt 0.12.x) — stateless authentication
- MySQL (JPA/Hibernate) — PostgreSQL is also supported via a profile switch, see below
- Bean Validation (Jakarta Validation)
- springdoc-openapi (Swagger UI) + Postman collection
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
├── controller/         # AuthController, ResourceController, ReservationController, HomeController
├── service/           # UserService, ResourceService, ReservationService
├── repository/        # UserRepository, ResourceRepository, ReservationRepository
├── entity/            # User, Role, Resource, Reservation, ReservationStatus
├── dto/                # Request/response DTOs (auth, resource, reservation, error)
└── exception/          # Custom exceptions + GlobalExceptionHandler
```

Clean separation: **Controller → Service → Repository**, with DTOs at the boundary (entities are never returned directly) and a dedicated Security layer.

---

## Getting Started (MySQL)

### 1. Prerequisites

- JDK 17+
- Maven 3.8+ (or use an IDE like IntelliJ, which bundles Maven support)
- MySQL 8+ running locally

### 2. Database

You don't need to manually create the database — the JDBC URL used by the `mysql` profile includes `createDatabaseIfNotExist=true`, so MySQL creates `booking_db` automatically on first connection, as long as the server is running and your user can create databases.

If you'd rather create it yourself:
```sql
CREATE DATABASE booking_db;
```

### 3. Configuration for this setup

This project runs against **MySQL** on **port 9000** (instead of the Spring Boot default of 8080) in this environment. That requires two things when running the app:

**a) Activate the `mysql` Spring profile**, via one of:
- Program arguments: `--spring.profiles.active=mysql`
- VM options: `-Dspring.profiles.active=mysql`
- Environment variable: `SPRING_PROFILES_ACTIVE=mysql`

**b) Set environment variables** (in IntelliJ: Run → Edit Configurations → Environment variables, semicolon-separated on one line):
```
DB_USERNAME=root;DB_PASSWORD=<your_mysql_password>;JWT_SECRET=change-this-super-secret-key-min-32-chars-long-please;SERVER_PORT=9000
```

| Variable | Value used here | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` (or `--spring.profiles.active=mysql`) | `mysql` | Selects `application-mysql.yml` (MySQL driver/dialect/URL) |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | *(your MySQL root password)* | MySQL password |
| `SERVER_PORT` | `9000` | HTTP port the app listens on |
| `JWT_SECRET` | *(min 32 chars)* | HMAC signing key for JWTs — change in production |
| `JWT_EXPIRATION_MS` | `86400000` (24h, default) | Token lifetime in ms |
| `DDL_AUTO` | `update` (default) | Hibernate schema strategy |

### 4. Run it

**From an IDE (IntelliJ):** set the above as Program arguments / Environment variables on the `BookingApplication` run configuration, then Run.

**From the command line:**
```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
export SERVER_PORT=9000
export JWT_SECRET=change-this-super-secret-key-min-32-chars-long-please

mvn spring-boot:run
```

**As a packaged jar:**
```bash
mvn clean package
java -jar target/resource-booking-system-1.0.0.jar --spring.profiles.active=mysql
```

The app starts on `http://localhost:9000`.

### 5. Running against PostgreSQL instead

The `postgres` profile (and the plain `default` profile, which also targets PostgreSQL) are still available if needed — just activate `--spring.profiles.active=postgres` and swap the DB env vars accordingly. See `application-postgres.yml` for its defaults.

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

This project provides both, per the assignment's "Swagger/OpenAPI or Postman" requirement — use whichever you prefer.

### Postman (primary, as used in this setup)

A ready-to-import collection is included: [`postman_collection.json`](./postman_collection.json).

1. Postman → **Import** → select `postman_collection.json`.
2. Open the collection's **Variables** tab and set `baseUrl` to `http://localhost:9000` (it defaults to `8080`).
3. Run **Auth → Login as Admin** and **Auth → Login as User** first — each has a test script that automatically captures the returned JWT into the `adminToken` / `userToken` collection variables.
4. Every other request already references `{{adminToken}}` or `{{userToken}}` in its `Authorization` header, so you can just hit **Send** on any of them afterward.

### Swagger UI (alternative)

```
http://localhost:9000/swagger-ui.html
```

Click **Authorize**, log in via `/auth/login`, paste `Bearer <token>`. Visiting the app's root (`http://localhost:9000/`) also redirects here automatically.

Raw OpenAPI spec: `http://localhost:9000/v3/api-docs`

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
- **Ownership checks in the service layer** for endpoints both roles can call (e.g. `GET /api/reservations/{id}`, cancel) — a `USER` gets a `403` if they try to touch someone else's reservation, and reservation *listing* is automatically scoped to the caller's own records for `USER`s at the query level (not just filtered client-side).

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
GET http://localhost:9000/api/reservations?status=PENDING&minPrice=10&maxPrice=200&page=0&size=10&sort=startTime,desc
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

---

## Sample cURL Walkthrough

```bash
# 1. Login as admin
TOKEN=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r .token)

# 2. Create a resource
curl -X POST http://localhost:9000/api/resources \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Studio A","description":"Recording studio","location":"B1","capacity":6,"available":true}'

# 3. Login as user and book it
UTOKEN=$(curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"User@123"}' | jq -r .token)

curl -X POST http://localhost:9000/api/reservations \
  -H "Authorization: Bearer $UTOKEN" -H "Content-Type: application/json" \
  -d '{"resourceId":1,"startTime":"2027-01-15T10:00:00","endTime":"2027-01-15T12:00:00","price":49.99}'

# 4. List my reservations, filtered
curl -H "Authorization: Bearer $UTOKEN" \
  "http://localhost:9000/api/reservations?status=PENDING&page=0&size=10"
```

---
## 📜 License

This project is for educational and academic purposes.

---

## 👨‍💻 Author

**Brijesh Prasad**

🌐 Connect with me: 
- 🔗 [LinkedIn](https://www.linkedin.com/in/brijesh216) 
- 💻 [GitHub](https://github.com/brijesh216)

---

⭐ If you found this project helpful, consider giving it a star on GitHub!
