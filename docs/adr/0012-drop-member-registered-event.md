# 0012. Don't port legacy's member-registered CDI event

**Status:** Accepted (2026-11-15)

## Context

Legacy's `MemberRegistration` fires a CDI `Event<Member>` after `em.persist`, synchronously and
inside the transaction. Its only observer, `MemberListProducer`, re-queries the member list so the
JSF page shows the new member on the same response.

| Option | Verdict |
|---|---|
| `ApplicationEventPublisher` + `@EventListener` | Works, but nothing would listen |
| `@TransactionalEventListener(AFTER_COMMIT)` | Only fires inside a Spring transaction. Registration is one document insert with no transaction (MongoDB transactions need a replica set; compose runs standalone), so it would silently never fire without `fallbackExecution = true` |
| **Drop it** | The new UI uses Post/Redirect/Get (ADR 0004): the GET after the redirect loads the list, so the event has no job |

## Decision

Drop it. `MemberRegistration` inserts and returns; no event is published.

## Consequences

- Less code, and no listener that looks like an extension point but never runs.
- When a real consumer appears (welcome email, search index, another service), publish a
  `MemberRegistered` event then. For anything outside this process, an in-memory event is lost on a
  crash after the insert; use a transactional outbox, or MongoDB change streams (replica set), so
  delivery survives restarts.
