<div align="center">
  <h1>Saanjha 2.0 Backend Core</h1>
  <p><strong>The Engine Powering Next-Generation Collaboration & Communication</strong></p>
  <br />
</div>

Saanjha 2.0 is an enterprise-grade platform built to connect teams through seamless communication, workspace management, and high-fidelity media calling. 

This repository contains the backend architecture, developed with **Spring Boot 3**, engineered for scalability, resilience, and real-time processing capabilities.

---

## ✨ Amazing Features & Capabilities

- 🔐 **Robust Multi-Layer Authentication**: 
  - JWT-based authentication combined with OAuth2 (Google & GitHub) integration.
  - Multi-Factor Authentication (MFA) via TOTP, powered by `dev.samstevens.totp`.
- 💬 **High-Performance Real-Time Engine**: 
  - Native **STOMP-over-WebSocket** transport for instantaneous message delivery.
  - Granular chat features: typing indicators, real-time presence, reactions, and fan-out read receipts.
- 📞 **High-Fidelity Audio & Video Calling**: 
  - Seamlessly integrated with **LiveKit** to support resilient WebRTC communication, enabling screen sharing and encrypted interactions.
- 🏢 **Dynamic Workspace & Module Ecosystem**: 
  - Dedicated service domains including `admin`, `project`, `task`, `team`, `portfolio`, `feedback`, and `contribution` management modules.
- 🛡️ **Built-in Resilience & Observability**: 
  - Fault tolerance, rate-limiting, and circuit breakers driven by **Resilience4j** and **Bucket4j**.
  - Deep observability enabled through **Spring Boot Actuator** and **Prometheus** (Micrometer).
- 📢 **Omnichannel Notifications**: 
  - Integrated notification pipelines utilizing **RabbitMQ** (AMQP) for asynchronous event-driven architecture, alongside Spring Mail, Brevo SMTP, and a custom **NotificationHub SDK**.
- 📝 **Advanced Security & Content Sanitization**: 
  - Comprehensive protection against XSS vulnerabilities in user-authored content utilizing the **OWASP Java HTML Sanitizer**.
  - Secure media uploads with direct **Cloudinary** integration.

---

## 🏗️ Architecture & Tech Stack

Saanjha 2.0 is built on a highly modular monolith architecture ensuring robust domain boundaries while maintaining deployment simplicity. 

### 🔧 Core Technologies
- **Framework**: Spring Boot 3.3.4 (Java 21)
- **Database**: PostgreSQL (Spring Data JPA) with **Flyway** for automated, reliable migrations.
- **Cache & Rate Limiting**: Redis & Bucket4j
- **Message Broker**: RabbitMQ (AMQP) for async event distribution.
- **Real-Time Communication**: WebSockets + STOMP
- **Testing**: Testcontainers & JUnit 5 for reliable integration tests.
- **Observability**: Prometheus & Spring Boot Actuator

---

## 🚦 Getting Started (Local Development)

We provide a streamlined Docker Compose setup to instantly spin up the entire infrastructure required for local development.

### Prerequisites
- [Docker & Docker Compose](https://www.docker.com/)
- [Java 21](https://adoptium.net/)
- [Maven](https://maven.apache.org/)

### 1. Environment Setup
Configure your credentials in the `.env` file at the root of the project. The application supports overrides for seamless local execution.

```bash
# Ensure your LIVEKIT, CLOUDINARY, and DB variables are correctly set as per .env.example
```

### 2. Launching the Infrastructure
Start the required infrastructure (PostgreSQL, Redis, RabbitMQ) and the Spring Boot backend using Docker Compose:

```bash
docker-compose up -d
```
*Note: Health checks are configured to ensure the backend only starts once the database, cache, and message queue are fully ready to accept connections.*

### 3. Running Locally via Maven (Alternative)
If you prefer running the app directly on your host machine while only keeping infrastructure in Docker:
```bash
# Start only the databases/queues
docker-compose up -d saanjha-postgres saanjha-redis saanjha-rabbitmq

# Run the Spring Boot application
./mvnw spring-boot:run
```
---

## 🧪 Testing

Saanjha ensures high reliability through comprehensive testing powered by Testcontainers.

```bash
./mvnw clean test
```

---

<div align="center">
  <p>Built with ❤️ by the Saanjha Team</p>
</div>
