# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows
[Semantic Versioning](https://semver.org/).

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
- `es_ES` and `en_US` locales, fully externalized via MiniMessage.

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
