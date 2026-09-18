# Consensus

A multi-tenant trade reconciliation platform built with Spring Boot. Supports schema-per-tenant isolation, real-time trade break detection, fuzzy matching, break aging analysis, and Excel export with MinIO object storage.

**Live API:** https://api.getconsensus.xyz

---

## Features

- **Multi-tenancy** — schema-per-tenant isolation via Hibernate; each tenant gets a provisioned Postgres schema with Flyway migrations
- **Authentication** — stateless JWT with role-based access control
- **Trade Reconciliation** — ingests trades from multiple counterparties, detects breaks, and runs fuzzy matching to resolve near-matches
- **Break Aging** — tracks unresolved breaks over time with materiality scoring
- **Analytics & Export** — generates Excel reports, stores them in MinIO, and delivers via email
- **Real-time** — WebSocket feed for live break status updates
- **Simulation** — built-in market data simulator for testing reconciliation flows end-to-end
- **Event-driven** — Kafka for async trade ingestion and break lifecycle events

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 25, Spring Boot 4.1 |
| Web | Spring MVC, Spring WebSocket |
| Security | Spring Security, JWT (jjwt 0.12) |
| Persistence | PostgreSQL 16, Hibernate, Flyway |
| Messaging | Apache Kafka 3.7 |
| Object Storage | MinIO |
| Email | Spring Mail (MailHog in dev) |
| API Docs | SpringDoc OpenAPI 3 |
| Containerisation | Docker, Docker Compose |

---

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 25 (for local development without Docker)

### Run locally

```bash
# Start all infrastructure (Postgres, Kafka, MinIO, MailHog)
docker-compose up

# App starts automatically via Spring Boot DevTools compose integration
./gradlew bootRun
```

Swagger UI: http://localhost:8080/swagger-ui.html

### Environment variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing secret | required |
| `DB_PASSWORD` | Postgres password | required |
| `MINIO_ACCESS_KEY` | MinIO access key | required |
| `MINIO_SECRET_KEY` | MinIO secret key | required |
| `MAIL_HOST` | SMTP host | `smtp.gmail.com` |
| `MAIL_USERNAME` | SMTP username | — |
| `MAIL_PASSWORD` | SMTP password | — |

Copy `.env.example` to `.env` and fill in values before running.

---

## API Overview

### Tenant Provisioning

```
POST /tenants/register          Register a new tenant (provisions DB schema)
```

### Authentication

```
POST /auth/register             Register a user within a tenant
POST /auth/login                Login and receive JWT
```

All subsequent requests require:
```
Authorization: Bearer <token>
X-Tenant-ID: tenant_<name>
```

### Trade Breaks

```
GET  /breaks                    List active trade breaks
GET  /breaks/{id}               Get break detail
POST /breaks/{id}/resolve       Manually resolve a break
```

### Analytics

```
POST /analytics/export          Submit export job
GET  /analytics/export/{id}/status   Poll job status
GET  /analytics/export/{id}/download Download Excel report
```

### Admin

```
POST /admin/pending-confirm-sweep   Sweep stale pending confirmations
```

Full interactive docs available at https://api.getconsensus.xyz/swagger-ui.html

---

## Deployment

The production setup uses Docker Compose on a VPS with a Cloudflare Tunnel for HTTPS.

```
Client → Cloudflare (TLS) → cloudflared tunnel → Docker → Spring Boot
```

Build and push multi-arch image:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t vtrixe/consensus:latest --push .
```

Pull and restart on VPS:

```bash
docker-compose -f docker-compose.yml pull app
docker-compose -f docker-compose.yml up -d app
```

---

## Project Structure

```
src/main/java/com/example/consensus/
├── auth/           JWT filter, security config, user details
├── tenant/         Multi-tenancy: schema provisioning, Hibernate wiring
├── web/            Trade break and admin REST controllers
├── reconciliation/ Break detection and materiality scoring
├── breakaging/     Break aging lifecycle
├── fuzzymatch/     Fuzzy matching engine
├── analytics/      Export service, Excel generation
├── simulation/     Market data simulator
├── loader/         Trade data ingestion
├── outbound/       External integrations
├── websocket/      Real-time WebSocket feed
└── worker/         Background job workers
```

---

## License

[MIT](LICENSE)
