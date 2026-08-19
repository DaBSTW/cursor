# CODESTYLE.md — BookReports

Conventions for this codebase. These are rules, not suggestions: CI enforces what can be automated, review enforces the rest.

Five principles drive everything below:

1. **English only** — every identifier, comment, log line and commit message.
2. **Debuggable** — a failure in production must be diagnosable from the logs alone.
3. **Comments are terse, technical, and only when necessary.**
4. **Always formatted** — formatting is never a review topic; the tool decides.
5. **Production-ready structure** — small units, explicit boundaries, no surprises.

> This document is written in English for the same reason the code is. User-facing text is a separate concern: it lives in `locale/*.yml` (`es_ES`, `en_US`, …) and is never hardcoded — see §2.

---

## 1. Language

Everything inside `src/` is English. No exceptions, no mixed-language identifiers.

```java
// Bad
private void enviarReporte(UUID jugador) { }
private boolean estaEnCooldown;

// Good
private void submitReport(UUID reporter) { }
private boolean onCooldown;
```

This includes:

- Class, method, field, parameter and local variable names
- Comments and Javadoc
- Log messages and exception messages
- Config keys and locale keys (`report.cooldown-seconds`, not `reporte.enfriamiento`)
- Commit messages, branch names and PR descriptions
- Test names and test data

**The only Spanish in this repository** is player-facing copy in `locale/es_ES.yml` and the top-level design docs (`SPECS.md`, `ROADMAP.md`, `README.md`).

---

## 2. No hardcoded user-facing strings

Any text a player or staff member can read goes through `LocaleManager`. A string literal that reaches a player is a bug, even in English.

```java
// Bad
player.sendMessage("You must wait before reporting again.");

// Good
player.sendMessage(locale.get("report.cooldown-active", Map.of("cooldown", remaining)));
```

Log messages are the opposite: always English literals, never localized.

---

## 3. Formatting

Formatting is fully automated. **Never** hand-format, never argue about it in review.

```bash
./gradlew spotlessApply    # format
./gradlew spotlessCheck    # verify (runs in CI, fails the build)
```

The configured rules:

| Rule | Value |
|---|---|
| Indentation | 4 spaces, no tabs |
| Line length | 120 columns |
| Braces | K&R, always braced — even single-statement `if` |
| Imports | No wildcards, no unused, deterministic order |
| Trailing whitespace | Stripped |
| File ending | Single newline |
| Encoding | UTF-8 |

```java
// Bad
if (report == null) return;

// Good
if (report == null) {
    return;
}
```

A PR that fails `spotlessCheck` is not reviewed until it passes.

---

## 4. Comments

**Default: no comment.** Code that needs a comment to be understood should usually be rewritten instead. When a comment is genuinely necessary, keep it short and strictly technical.

### Write a comment only when it explains *why*

```java
// Bad — restates the code
// Increment the counter
counter++;

// Bad — narrates the obvious
// Get the player from the event
Player player = event.getPlayer();

// Good — explains a non-obvious constraint
// Written books cap at 100 pages; the online-player list must page below that.
private static final int MAX_BOOK_PAGES = 100;

// Good — explains a decision the reader would otherwise undo
// Re-checked server-side: the anvil GUI's client limit is trivially bypassable.
evidence = sanitize(evidence, MAX_EVIDENCE_LENGTH);
```

### Never

- Commented-out code — git remembers it, delete it
- Banner separators (`// ===== SERVICES =====`) — if a class needs sections, split the class
- `TODO` / `FIXME` without an issue link (`// TODO(#42): ...`)
- Javadoc that repeats the signature (`@param player the player`)
- Changelog comments (`// modified by X on 2026-01-01`)
- Comments that will silently rot when the code beside them changes

### Javadoc

Required only on `dev.bookreports.api.*` — that package is consumed by third-party plugins and is a stability contract. Document behaviour, thread affinity, nullability and failure modes.

```java
/**
 * Submits a report on behalf of the given reporter.
 *
 * <p>Safe to call from any thread. The returned future completes on an internal
 * pool — dispatch back through the scheduler before touching the Bukkit API.
 *
 * @return a future completing exceptionally with {@link ReportRejectedException}
 *         if a cooldown, daily limit or duplicate check rejects the report
 */
CompletableFuture<Report> submitReport(UUID reporter, UUID target, String categoryId);
```

Everywhere else, Javadoc is optional and usually unnecessary.

---

## 5. Debuggability

The standard: **an incident is diagnosable from the logs, without a debugger and without reproducing it.**

### Never swallow an exception

```java
// Bad — the failure vanishes
try {
    dao.insert(report);
} catch (SQLException ignored) {
}

// Bad — bypasses the plugin logger, no context
catch (SQLException e) {
    e.printStackTrace();
}

// Good
catch (SQLException e) {
    throw new StorageException("Failed to insert report for target=" + targetUuid, e);
}
```

Every `catch` either handles the error meaningfully, rethrows with added context, or logs at `WARNING`/`SEVERE` with the cause attached. There is no fourth option.

### Never drop a `CompletableFuture`

An unobserved future failure is invisible. Every chain terminates in `whenComplete` or `exceptionally`.

```java
// Bad — if this fails, nothing is logged, ever
reportService.submitReport(reporter, target, category);

// Good
reportService.submitReport(reporter, target, category)
        .whenComplete((report, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Report submission failed for reporter=" + reporter, error);
                return;
            }
            scheduler.runForPlayer(player, () -> openResultPage(player, report));
        });
```

### Carry identifiers in every log line

A log line without an identifier cannot be correlated with anything. Use `key=value`, and always include the relevant `reportId`, `sessionId` or `uuid`.

```java
// Bad
logger.warning("Invalid session");

// Good
logger.warning("Rejected selection: sessionId=" + sessionId + " actor=" + actor
        + " reason=SESSION_OWNER_MISMATCH");
```

### Fail loudly at startup, never silently at runtime

A malformed `config.yml` disables the plugin with a clear message. It does not fall back to a default and surprise the operator three hours later.

```java
// Good
throw new ConfigurationException(
        "Category '" + id + "' has unknown priority '" + raw + "'; expected one of " + Arrays.toString(Priority.values()));
```

### Debug logging

Behind `debug: true` in `config.yml`, guarded so it costs nothing when disabled. Debug logs trace state transitions — the part of this plugin that is hardest to reason about after the fact.

```java
if (config.debug()) {
    logger.info("Session transition: sessionId=" + id + " " + from + " -> " + to);
}
```

### Make invalid states unrepresentable

Prefer enums and records over boolean flags and mutable bags. `ReportState` as an enum with an explicit transition table is debuggable; three loose booleans are not.

---

## 6. Structure

### Package by feature, not by layer

Follow the layout in [`SPECS.md` §14](./SPECS.md). `book/`, `gui/`, `session/`, `storage/` — a change to the book flow touches one package.

### Size limits

Soft ceilings; crossing one means the unit is doing too much:

- Method: **40 lines**
- Class: **300 lines**
- Parameters: **5** (beyond that, pass a record)
- Nesting: **3 levels** (use guard clauses)

```java
// Bad
public void handle(Player player, String id) {
    if (player != null) {
        Session session = sessions.get(id);
        if (session != null) {
            if (session.owner().equals(player.getUniqueId())) {
                // real work, four levels deep
            }
        }
    }
}

// Good
public void handle(Player player, String id) {
    Session session = sessions.get(id);
    if (session == null || !session.owner().equals(player.getUniqueId())) {
        logger.warning("Rejected selection: sessionId=" + id + " actor=" + player.getUniqueId());
        return;
    }
    // real work, flat
}
```

### Immutability by default

- Domain models are `record`s
- Fields are `final` unless mutation is required
- Collections returned from methods are unmodifiable
- No static mutable state, ever — it breaks `/reload` and makes tests order-dependent

### Dependencies are explicit

Constructor injection, no service lookups buried inside methods, no `BookReportsPlugin.getInstance()` singleton reached from arbitrary code.

```java
// Bad — hidden dependency, untestable
public class ReportService {
    public void submit(...) {
        BookReportsPlugin.getInstance().getStorage().insert(...);
    }
}

// Good — dependencies visible in the signature
public final class ReportService {
    private final ReportDao dao;
    private final CooldownService cooldowns;

    public ReportService(ReportDao dao, CooldownService cooldowns) {
        this.dao = dao;
        this.cooldowns = cooldowns;
    }
}
```

### Program against interfaces at boundaries

Storage, punishment bridges and the scheduler are interfaces with swappable implementations. Business logic never references `SQLiteReportDao` or `LiteBansBridge` directly.

### Naming

| Kind | Convention | Example |
|---|---|---|
| Class | `PascalCase`, noun | `ReportQueueView` |
| Interface | `PascalCase`, no `I` prefix | `PunishmentBridge` |
| Method | `camelCase`, verb | `submitReport`, `isOnCooldown` |
| Constant | `UPPER_SNAKE_CASE` | `MAX_EVIDENCE_LENGTH` |
| Boolean | reads as a predicate | `hasPendingReport`, not `pendingFlag` |
| Package | lowercase, singular | `dev.bookreports.session` |

No abbreviations beyond established ones (`uuid`, `id`, `dao`, `api`). `rpt`, `mgr` and `tmp` are not names.

---

## 7. Production-ready discipline

### Threading

The single hardest constraint in a Paper plugin. Get it wrong and the server stalls.

- **No blocking I/O on the main thread.** Ever. Not "just this one small query".
- All Bukkit API calls happen on the correct thread — dispatch through `SchedulerAdapter`, never `Bukkit.getScheduler()` directly (Folia compatibility).
- Shared mutable state is either confined to one thread or explicitly synchronized; document which in a one-line comment when it is not obvious.

### Resources

Always try-with-resources for `Connection`, `PreparedStatement`, `ResultSet`. `onDisable` closes the Hikari pool, cancels scheduled tasks and flushes pending writes.

### Null

- Parameters and returns are non-null by default
- Absence is `Optional<T>` on return types — never `Optional` as a field or parameter
- Validate public entry points with `Objects.requireNonNull(x, "x")`

### Bounds

Every cache declares `maximumSize` and an expiry. Every paginated query has a `LIMIT`. Unbounded is a memory leak with a delay.

---

## 8. Testing

- Business rules require tests: anti-abuse, session transitions, priority calculation, config parsing.
- Test names state the behaviour: `rejectsSelectionWhenSessionOwnerDiffers()`, not `test3()`.
- One behaviour per test, no shared mutable fixture state between tests.
- Integration tests run against `jdbc:sqlite::memory:` and MockBukkit.
- Bug fixes ship with the regression test that fails without the fix.

---

## 9. Git

- Commits are imperative, English, scoped: `Add cooldown multiplier for repeat false reporters`
- One logical change per commit; formatting-only changes go in their own commit
- Never commit commented-out code, debug prints or `.orig`/`.rej` files
- `./gradlew build test spotlessCheck` passes locally before pushing

---

## 10. Enforcement

| Check | Tool | Blocking |
|---|---|---|
| Formatting | Spotless | ✅ CI |
| Static analysis | Checkstyle + Error Prone | ✅ CI |
| Tests | JUnit 5 + MockBukkit | ✅ CI |
| Everything else here | Code review | ✅ |

When a rule and readability genuinely conflict, readability wins — say so in the PR and explain why. Silently ignoring a rule is not that.
