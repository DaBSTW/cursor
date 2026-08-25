# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows
[Semantic Versioning](https://semver.org/).

## [Unreleased]

Everything from here down is new work on top of the frozen `v1.0.0` tag
(`f4a33ac`) — nothing below has shipped yet.

### Added
- Automatic mute for abusive reporters: `false-report-penalty.mute-minutes` (parsed since 1.0.0 but never wired
  up) now actually dispatches through the connected punishment bridge the moment a reporter's false-report count
  crosses `threshold-in-30-days`. Fires once per crossing, not on every subsequent false report.
- Live target status in the report detail view: gamemode, health and active potion effects, computed on open
  rather than stored — always current, costs nothing when the target is already online.
- Offline-player reporting: `/report <nick>` now resolves a player who isn't currently online (gated by
  `hasPlayedBefore()`), matching the existing offline-lookup pattern used by `/reportadmin history`/`stats`.
- Incident-location snapshot: both the target's and the reporter's position are captured at submission time and
  stored as a single compact string (same pattern as `chat_context`). The report detail view gained a
  "Teleport to incident location" button (separate from the existing live "Teleport", which follows the target
  wherever they are now) and a reporter↔target distance line — a credibility signal TigerReports doesn't surface.
- Persistent staff notes: `/reportadmin note <id> <text>` appends a timestamped note to a report, independent of
  the single resolution note; `/reportadmin notes <id>` (also a button in the detail view) lists them.
- Archive and purge: `/reportadmin archive <id>` hides an already-resolved report from the staff queue without
  deleting it (still reachable via `view`/`history`); `/reportadmin unarchive <id>` reverses it;
  `/reportadmin purge <id>` (`bookreports.admin`-only, logged) permanently deletes a report and its notes.
- Minimum evidence length: typed evidence shorter than 5 characters re-prompts instead of being accepted —
  never applies to "Skip".
- Vault integration (`Chat` service): when Vault plus a permission plugin that registers one (LuckPerms, etc.)
  are both present, rank prefix/suffix show next to player names in the staff queue and report detail view.

### Changed
- `Report`, `SubmitReportRequest` and `ReportDao` all gained new fields/methods for the above — additive only, no
  existing column or method signature was removed.

## [1.0.0] — 2026-08-19

Initial release, built out phase-by-phase per `ROADMAP.md`.

### Added
- Book-driven report flow (`/report`): target confirmation, category, sub-reason,
  optional free-text evidence (anvil GUI), summary and confirmation, with a
  reporter-owned, transition-whitelisted session state machine backing every
  book click (SPECS.md §13).
- Anti-abuse rules: per-player cooldown, daily limit, duplicate-pending block,
  consensus-based priority escalation, and a false-report penalty.
- SQLite (zero-config) and MySQL storage, hand-rolled versioned migrations,
  HikariCP pooling.
- Staff review panel: paginated queue GUI with status/category filters and a
  repeat-reporter grouping badge, report detail view (claim, teleport,
  history, resolve, mark false), and equivalent console commands under
  `/reportadmin`.
- Live in-game alerts for new HIGH-priority reports, with a per-player
  notifications toggle persisted across restarts.
- Automatic chat-context capture: a report's target's recent chat lines are
  attached to the ticket automatically, without the reporter typing or
  copying anything.
- Reporter accuracy and staff performance stats (`ReporterStats`,
  `StaffStats`), surfaced in the report detail view and via
  `/reportadmin stats reporter|staff <player>`.
- Automatic CoreProtect evidence (optional): a short summary of the target's
  recent block activity (e.g. `3x break, 1x place (last 5m)`) is attached to
  new reports the same way chat context is, configurable under
  `coreprotect:` in `config.yml`.
- Sanctions applied from the staff panel's quick-sanction menu are now
  recorded on the report itself (`sanctionType`/`sanctionDuration`, e.g.
  `BAN`/`7d`) — a structured audit trail independent of the free-text
  resolution note.
- `/report status`: reporters can check the status of their own recent
  tickets without waiting for a resolution message or asking staff.
- The staff queue GUI (`/reportadmin`) gained a priority filter, a
  "claimed by me" toggle, and a search box for the target's name, alongside
  the existing status/category filters.
- Automatic update checking (`update-checker:` in `config.yml`): logs a
  console line, and optionally notifies ops on join, when a newer version is
  available on GitHub Releases or Modrinth. `/reportadmin checkupdate`
  triggers an on-demand check. Never blocks startup and never throws on a
  network problem.
- One-click updates for ops: the update notification includes a clickable
  `[Update now]` that runs `/reportadmin update`, downloading the new jar
  and staging it in `plugins/update/` (Bukkit/Paper's own mechanism) —
  applied automatically the next time the server restarts. Never attempts a
  live class swap or a forced restart, both unsafe.
- `/report` and the report-tool item refuse everyone without
  `bookreports.admin` (a plain "not available" message) whenever a newer
  version is known to exist — admins are unaffected and keep the one-click
  updater. Unconditional, not configurable; the only way around it is
  keeping BookReports current or turning the checker off entirely
  (`update-checker.enabled: false`).
- Public API: `BookReportsAPI` (services manager) plus `ReportCreateEvent`
  (cancelable), `ReportCreatedEvent`, `ReportClaimedEvent`,
  `ReportResolvedEvent`, `ReportFalseMarkedEvent`.
- Optional integrations, all soft-depend: `PunishmentBridge` for LiteBans,
  AdvancedBan and EssentialsX, a Discord webhook notifier (report creation
  and resolution), a PlaceholderAPI expansion, and a proxy sync channel
  (`bookreports:sync`) for cross-backend alerts over a shared MySQL database.
- Anonymous bStats usage metrics, toggleable via `metrics.enabled`.
- Folia and Bukkit scheduler support via a single `SchedulerAdapter`
  abstraction.
- Seven bundled locales (`en_US`, `es_ES`, `pt_BR`, `de_DE`, `fr_FR`, `ru_RU`,
  `zh_CN`), fully externalized via MiniMessage.

### Fixed
- The plugin failed to enable on Paper 26.x with
  `UnsupportedOperationException: ... JavaPlugin#getCommand ...`: Paper
  dropped support for the YAML `commands:` block in paper-plugin.yml.
  Commands are now registered programmatically via the Brigadier lifecycle
  event (`BasicCommandAdapter`), synchronously in `onEnable` so the
  registration isn't missed while storage connects asynchronously
  afterwards. The internal book-click commands were renamed to
  `bookreports-select`/`bookreports-target` (from a `/breport:select`
  fallback-prefix trick that no longer exists) to keep them collision-free.
- The plugin printed an `SLF4J: Failed to load class "StaticLoggerBinder"...
  Defaulting to no-operation (NOP) logger` warning straight to stderr on
  every startup, which Paper then nags the author about. Caused by shading
  and relocating our own private, unbound copy of `slf4j-api` (a transitive
  of HikariCP/sqlite-jdbc), which hid Paper's own already-bound SLF4J from
  HikariCP's logger lookups. `slf4j-api` is now excluded from the shaded
  jar entirely, letting that logging go through the server's real logger —
  HikariCP's connection-pool startup lines now show up properly instead.
- The plugin failed to construct *any* Caffeine cache at runtime with
  `ClassNotFoundException: dev.bookreports.libs.caffeine.cache.SSMSA`:
  shadowJar's `minimize()` was stripping a Caffeine cache implementation
  class that's only ever loaded by reflection, never referenced directly in
  bytecode. Caffeine is now excluded from minimization, same as sqlite-jdbc
  already was.
- Both fixes were found and confirmed by actually booting the plugin on a
  real Paper 1.21.1 server and a real Paper 26.2 server (under Java 25), not
  just by auditing API compatibility on paper — see git history for the full
  investigation.

### Compatibility
- Verified functional on Paper 1.21.x through 26.2 (Mojang's new `year.drop`
  versioning): `ItemStack.of(...)` replaces the soon-to-be-removed
  `new ItemStack(...)` constructor throughout the GUI layer, and every other
  API this plugin touches (`BukkitScheduler`, `AsyncChatEvent`, `Enchantment`
  constants) was confirmed unchanged across that range. `api-version: '1.21'`
  is intentionally left as-is — it's a floor, not a ceiling.

### Security
- Every free-text field (report evidence) is sanitized and length-capped both
  where it's captured and again in the service layer, so the check holds
  even for callers that bypass the GUI through the public API.
- Internal book-click commands are unlisted, non-tab-completable, and
  validate session ownership, session freshness, and the requested state
  transition before acting on anything.

[1.0.0]: https://github.com/DaBSTW/cursor/releases/tag/v1.0.0
