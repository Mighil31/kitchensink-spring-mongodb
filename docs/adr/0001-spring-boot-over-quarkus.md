# 0001. Target Spring Boot on Java 21

**Status:** Accepted (2026-11-14)

## Context

The brief allows the latest stable Spring Boot or Quarkus on Java 21. Legacy is a Jakarta EE app
(CDI, EJB, JAX-RS, JPA, JSF) on JBoss EAP.

## Options

- **Quarkus** keeps the CDI / JAX-RS / JPA APIs, so the code diff would be smaller.
- **Spring Boot** has the largest ecosystem and hiring pool, first-class Spring Data MongoDB,
  Spring Security and Testcontainers `@ServiceConnection`, and is the most common target when
  teams leave EAP.

## Decision

Spring Boot (4.1.x) on Java 21.

## Consequences

- The persistence layer is rewritten for MongoDB either way, which removes most of Quarkus's
  API-compatibility advantage.
- CDI, EJB and JAX-RS concepts have to be mapped to Spring equivalents (`@Service`,
  `@RestController`, `ApplicationEventPublisher`, `@Transactional`).
- CDI request-scoped beans hold per-request state in fields; Spring controllers are singletons,
  so that state must move to method parameters and the model.
