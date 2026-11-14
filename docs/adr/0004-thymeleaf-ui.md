# 0004. Replace JSF with Spring MVC + Thymeleaf

**Status:** Accepted (2026-11-14)

## Context

The legacy UI is one JSF page (registration form + member table) backed by a request-scoped CDI
bean. JSF is component-based with server-side view state; Spring MVC is request-based.

## Decision

Server-rendered Spring MVC + Thymeleaf, keeping the same page: form, field errors, success
message, member table.

| JSF | Spring MVC + Thymeleaf |
|---|---|
| `@Model` bean exposing `#{newMember}` | `@Controller` + `@ModelAttribute("newMember")` |
| `<h:inputText value="#{newMember.name}">` | `<input th:field="*{name}">` |
| `<h:message for="name">` | `<span th:errors="*{name}">` |
| `FacesMessage` "Registered!" | Flash attribute + Post/Redirect/Get |
| `#{members}` producer + CDI event refresh | Load the list in the GET handler |
| View state (incidental CSRF protection) | Spring Security CSRF token |

## Consequences

- Post/Redirect/Get fixes the resubmit-on-refresh bug (B14) and removes the need for the CDI
  event that refreshed the list.
- Output is escaped with `th:text`; `th:utext` is not used.
- The UI is checked with a short manual parity checklist rather than the HTTP contract suite.
