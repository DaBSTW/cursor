# SPECS.md — Plugin de Reportes por Libro ("BookReports")

## 1. Resumen

Plugin para servidores **Minecraft: Java Edition** que implementa un sistema de reportes de jugadores mediante una interfaz de **libros escritos (written books)**, inspirado en el flujo de `/report` de Hypixel: el jugador nunca escribe comandos con parámetros a mano, navega páginas de un libro y hace clic en enlaces de texto para seleccionar objetivo, categoría, sub-motivo y confirmar. Todo el flujo es asíncrono, persistente y auditable por el staff.

- **Versión objetivo de Minecraft**: 1.21.x (última release estable al momento de redactar; el plugin se compila contra el mapping más reciente y declara `api-version: '1.21'`).
- **Servidor soportado**: PaperMC (y forks compatibles: Purpur, Folia-ready opcional — ver §12).
- **Lenguaje**: Java 21 (LTS, requerido por Paper 1.20.5+).
- **Build**: Gradle (Kotlin DSL) con Shadow plugin para relocar dependencias.

No se soporta Bukkit/Spigot puro como target principal porque el sistema de libros depende de **Adventure Components** (`net.kyori.adventure`) para `ClickEvent`/`HoverEvent` ricos, ya nativos en la API de Paper.

---

## 2. Objetivos y no-objetivos

### Objetivos
- Reportar a un jugador con **cero fricción de tipeo**: `/report <jugador>` abre un libro; todo lo demás es clic.
- Categorías y sub-motivos configurables sin recompilar (YAML).
- Evidencia opcional (texto libre corto, capturado vía **anvil GUI**, no chat, para no contaminar el chat público).
- Cola de revisión para staff con panel en **inventario GUI** (no libro — el staff necesita ver lista/paginación con acciones rápidas de clic único: teleport, ver historial, resolver, banear).
- Persistencia en base de datos (SQLite por defecto, MySQL/MariaDB opcional) con historial completo.
- Anti-abuso: cooldown por jugador, límite diario, detección de reportes duplicados/en masa (brigading), penalización por reportes falsos reincidentes.
- Integración opcional con Discord (webhook) para notificar reportes de alta prioridad al staff offline.
- API pública (eventos Bukkit) para que otros plugins (p. ej. sistemas de castigo, ban-managers) reaccionen a reportes.

### No-objetivos (fuera de alcance v1)
- Sistema de apelaciones de baneos (se puede integrar con plugins existentes tipo LiteBans vía hook, no se reimplementa).
- Grabación/replay de sesiones de juego como evidencia automática.
- Multi-idioma con detección automática por cliente (se soporta i18n manual vía `locale/*.yml`, pero no auto-detección del idioma de cliente en v1).

---

## 3. Arquitectura general

```
┌─────────────────────────────────────────────────────────────┐
│                        BookReportsPlugin                     │
│  (JavaPlugin, punto de entrada, bootstrap de módulos)        │
└───────────────┬───────────────────────────┬──────────────────┘
                │                           │
        ┌───────▼────────┐         ┌────────▼─────────┐
        │  Módulo Report  │         │  Módulo Staff     │
        │  (flujo jugador)│         │  (panel revisión) │
        └───────┬────────┘         └────────┬─────────┘
                │                           │
        ┌───────▼───────────────────────────▼─────────┐
        │              ReportService (core)             │
        │  orquesta validación, cooldown, persistencia   │
        └───────┬───────────────────────────┬─────────┘
                │                           │
        ┌───────▼────────┐         ┌────────▼─────────┐
        │  Storage Layer  │         │  Notification     │
        │  (SQLite/MySQL, │         │  Layer (Discord    │
        │  HikariCP, DAO) │         │  webhook, in-game)  │
        └────────────────┘         └───────────────────┘
```

### 3.1 Patrón de módulos
- **Bootstrap**: `BookReportsPlugin#onEnable` inicializa `ConfigManager` → `StorageManager` (async connect) → `LocaleManager` → `ReportService` → registra comandos, listeners y `PlaceholderExpansion` (opcional, PlaceholderAPI).
- **Inyección manual simple** (sin framework DI pesado): un `ServiceLocator`/`Container` liviano expone singletons vía getters del plugin principal. Evita dependencias extra (Guice) para mantener el jar ligero.
- **Todo I/O de base de datos es asíncrono** (`Bukkit#getScheduler().runTaskAsynchronously`/`CompletableFuture` con executor propio), nunca bloquea el hilo principal del servidor.

---

## 4. Flujo de usuario (UX del libro)

### 4.1 Entrada al flujo
Comandos disponibles:
- `/report <jugador>` — abre directamente el libro de categorías apuntando al jugador dado.
- `/report` (sin argumento) — abre un libro con lista paginada de jugadores online clicables (excluye al propio ejecutor y a staff exento si `hide-staff-from-list: true`).
- Clic derecho con un jugador seleccionado en tablist + `/report` mediante item físico opcional (`report-tool`, ver §4.6) — solo si `enable-report-tool: true` en config.

### 4.2 Estructura del libro (páginas)

El libro es un `ItemStack(Material.WRITTEN_BOOK)` generado dinámicamente por `BookBuilder`, entregado con `Player#openBook(Book)` (API Paper, sin necesidad de darlo en el inventario). Cada página usa `Component` de Adventure; las "opciones" son fragmentos de texto con:
- `ClickEvent.runCommand("/bookreports-select <sessionId> <optionId>")` — comando interno oculto (registrado vía la API de comandos Brigadier de Paper, no autocompletable) que avanza la máquina de estados de la sesión. Su nombre distintivo evita colisiones con comandos de otros plugins ahora que Paper (≥26.x) ya no soporta el prefijo de fallback de `commands:` en YAML.
- `HoverEvent.showText(...)` con descripción ampliada de la categoría.
- Formato: corchetes de color `§a[ Cheating / Hacks ]` estilo Hypixel, con subrayado al hover.

**Páginas del flujo estándar:**

1. **Página 1 — Confirmación de objetivo**
   - "Vas a reportar a: **<jugador>**"
   - Info corta: última vez visto, si ya tiene un reporte tuyo pendiente contra él (evita duplicados).
   - Opciones: `[ Continuar ]` / `[ Cancelar ]`.

2. **Página 2 — Categoría principal**
   - Lista definida en `config.yml > categories` (ver §7.1). Ejemplo por defecto:
     - `⚔ Hacks / Cheats`
     - `💬 Chat abusivo / Spam`
     - `🏷 Nombre de usuario o skin inapropiado`
     - `🐛 Abuso de bugs / Exploits`
     - `🛡 Suplantación de staff`
     - `❓ Otro`

3. **Página 3 — Sub-motivo** (dependiente de la categoría elegida, definido por config; p. ej. para "Hacks": `Killaura`, `Speed`, `Fly`, `Reach`, `X-Ray`, `Otro`).

4. **Página 4 — Evidencia (opcional)**
   - `[ Agregar detalles ]` → abre **anvil GUI** (`AnvilGUI` patrón custom, sin dependencia externa obligatoria — implementación propia con `InventoryView` de tipo `ANVIL`) para texto libre de hasta 100 caracteres (link a clip, coordenadas, testigos).
   - `[ Omitir ]` continúa sin evidencia.
   - Nota: el libro en sí **no permite input de texto libre dentro de sus páginas** (limitación de Minecraft: un `WRITTEN_BOOK` es de solo lectura una vez enviado con `openBook`), por eso el paso de texto libre se delega a un anvil GUI, no a otra página del libro.

5. **Página 5 — Resumen y confirmación final**
   - Recapitula jugador, categoría, sub-motivo, evidencia.
   - `[ ✔ Enviar reporte ]` / `[ ✘ Cancelar ]`.

6. **Página 6 — Resultado**
   - Mensaje de éxito + número de ticket (`#REP-000123`) + tiempo estimado de revisión si está configurado.
   - Se cierra el libro automáticamente a los N segundos (`Player#closeInventory` vía scheduler) o al clic en `[ Cerrar ]`.

### 4.3 Máquina de estados de la sesión

Cada flujo activo se modela como `ReportSession`:

```java
record ReportSession(
    UUID sessionId,
    UUID reporterId,
    UUID targetId,
    ReportState state,      // TARGET_CONFIRM, CATEGORY, SUBREASON, EVIDENCE, SUMMARY, DONE, EXPIRED
    String categoryId,
    String subReasonId,
    String evidenceText,
    Instant createdAt
) {}
```

- Sesiones viven en memoria (`Cache<UUID, ReportSession>` — Caffeine, expiración a los 5 minutos de inactividad).
- Cada `runCommand` de selección valida que `sessionId` pertenezca al jugador que ejecuta (evita spoofing por otro jugador copiando el comando) y que el `state` actual coincida con la transición esperada (evita replays de páginas viejas tras `/reload` o cambios de config).
- Al expirar o cancelar, se libera la sesión sin persistir nada.

### 4.4 Anti-doble-clic / concurrencia
- Un jugador solo puede tener **una sesión de reporte activa a la vez** (nuevo `/report` cierra la anterior).
- Los `ClickEvent` de comando incluyen el `sessionId`; si el comando llega con un `sessionId` que ya no es el activo (p. ej. el jugador abrió el libro dos veces), se ignora y se informa "Esta página ya no es válida, usa /report de nuevo".

### 4.5 Cooldown y límites (ver también §8)
- Al escribir `/report`, si el jugador está en cooldown, se le muestra el libro igual pero la página de confirmación final indica el tiempo restante y deshabilita el enlace de envío (se muestra en gris, sin `ClickEvent`).

### 4.6 Item físico opcional ("report-tool")
- Item configurable (por defecto `Material.WRITABLE_BOOK` con nombre "Reportar Jugador", dado vía `/report tool` si `enable-report-tool: true`).
- Clic derecho sobre un jugador con el item en mano → abre el libro directamente con el objetivo preseleccionado (equivalente a `/report <jugador>`).

---

## 5. Panel de staff (revisión)

El staff **no** usa libros para revisar (no permite paginación eficiente ni acciones múltiples por fila); usa un **GUI de inventario** estándar, patrón *Chest GUI paginado*.

### 5.1 Comando y permisos
- `/reportadmin` (alias `/rvw`, `/reports`) — permiso `bookreports.staff`.
- Sub-comandos: `/reportadmin list [página]`, `/reportadmin view <id>`, `/reportadmin claim <id>`, `/reportadmin resolve <id> <acción>`, `/reportadmin history <jugador>`.

### 5.2 GUI principal (`ReportQueueView`)
- Chest de 54 slots (6 filas), fila superior = filtros (Pendientes / En revisión / Resueltos / Todos, por categoría), filas centrales = lista paginada de reportes (ítem = cabeza del jugador reportado, lore con categoría, reportante, tiempo transcurrido, prioridad por color), fila inferior = paginación + cerrar.
- Prioridad visual: bordes de ítem (glow/encantamiento falso) según `priority` (ALTA = reportes de hacks con ≥3 reportantes distintos en 10 min, MEDIA, BAJA).
- Clic izquierdo en un reporte → abre `ReportDetailView`.

### 5.3 GUI de detalle (`ReportDetailView`)
Acciones disponibles como ítems clicables:
- `[ Reclamar ]` (asigna `reviewer` = staff actual, evita doble-trabajo, TTL de reclamo 15 min con auto-liberación).
- `[ Teletransportarse al reportado ]` (si está online).
- `[ Ver historial del reportado ]` (abre lista de reportes previos, resueltos o no).
- `[ Ver evidencia ]` (chat clicable con el texto completo si excede el lore).
- `[ Resolver: Sancionar ]` → sub-menú con acciones rápidas (kick / mute / ban temporal — vía hook a plugin de castigos existente, ver §11) o marcar "acción externa aplicada" si se sancionó manualmente.
- `[ Resolver: Rechazar ]` → motivo rápido (`No hay evidencia suficiente`, `No es una infracción`, `Duplicado`).
- `[ Marcar reporte falso ]` → aplica penalización configurable al reportante (ver §8.3).

### 5.4 Notificación en vivo al staff
- `bookreports.staff.notify` recibe mensaje clicable en chat al crearse un reporte de prioridad ALTA (`[Ver] [Teleport]`), configurable on/off por jugador vía `/reportadmin notifications toggle`.
- Sonido + action bar opcional (`config.yml > staff.alert-sound`).

---

## 6. Modelo de datos

### 6.1 Entidad `Report`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `INTEGER PK AUTOINCREMENT` | Ticket numérico visible al usuario (`#REP-000123`). |
| `uuid` | `CHAR(36)` | Identificador interno estable (para APIs/eventos). |
| `reporter_uuid` | `CHAR(36)` | FK lógica a jugador. |
| `reporter_name` | `VARCHAR(16)` | Snapshot del nombre al momento del reporte. |
| `target_uuid` | `CHAR(36)` | Jugador reportado. |
| `target_name` | `VARCHAR(16)` | Snapshot del nombre. |
| `category_id` | `VARCHAR(32)` | Clave de `config.yml > categories`. |
| `sub_reason_id` | `VARCHAR(32)` | Nullable si la categoría no tiene sub-motivos. |
| `evidence_text` | `VARCHAR(256)` | Nullable. |
| `server` | `VARCHAR(64)` | Nombre del servidor/instancia (para redes con proxy — ver §12). |
| `status` | `ENUM` (`PENDING`,`IN_REVIEW`,`RESOLVED_ACTION`,`RESOLVED_REJECTED`,`RESOLVED_DUPLICATE`,`FALSE_REPORT`) | |
| `priority` | `ENUM` (`LOW`,`MEDIUM`,`HIGH`) | Calculado al crear y recalculable. |
| `reviewer_uuid` | `CHAR(36)` | Nullable, quién reclamó/resolvió. |
| `resolution_note` | `VARCHAR(256)` | Nullable. |
| `created_at` | `TIMESTAMP` | |
| `claimed_at` | `TIMESTAMP` | Nullable. |
| `resolved_at` | `TIMESTAMP` | Nullable. |

### 6.2 Esquema SQL (compatible SQLite y MySQL con tipos equivalentes)

```sql
CREATE TABLE IF NOT EXISTS br_reports (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid            CHAR(36)      NOT NULL UNIQUE,
    reporter_uuid   CHAR(36)      NOT NULL,
    reporter_name   VARCHAR(16)   NOT NULL,
    target_uuid     CHAR(36)      NOT NULL,
    target_name     VARCHAR(16)   NOT NULL,
    category_id     VARCHAR(32)   NOT NULL,
    sub_reason_id   VARCHAR(32),
    evidence_text   VARCHAR(256),
    server          VARCHAR(64)   NOT NULL DEFAULT 'default',
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    priority        VARCHAR(10)   NOT NULL DEFAULT 'LOW',
    reviewer_uuid   CHAR(36),
    resolution_note VARCHAR(256),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at      TIMESTAMP,
    resolved_at     TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_br_reports_target   ON br_reports(target_uuid);
CREATE INDEX IF NOT EXISTS idx_br_reports_status   ON br_reports(status);
CREATE INDEX IF NOT EXISTS idx_br_reports_reporter ON br_reports(reporter_uuid, created_at);

CREATE TABLE IF NOT EXISTS br_report_penalties (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    player_uuid   CHAR(36)   NOT NULL,
    reason        VARCHAR(64) NOT NULL,   -- 'FALSE_REPORT', 'ABUSE_COOLDOWN', ...
    applied_at    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_br_penalties_player ON br_report_penalties(player_uuid);
```

### 6.3 Capa de acceso a datos
- Patrón **DAO + HikariCP** (`ReportDao`, `PenaltyDao`), interfaz común para SQLite y MySQL (driver seleccionado por config, mismo SQL ANSI-compatible; se evita `AUTO_INCREMENT` de MySQL usando `INTEGER PRIMARY KEY AUTOINCREMENT` solo en dialecto SQLite y `AUTO_INCREMENT` en el dialecto MySQL, gestionado por dos scripts de migración separados en `resources/db/sqlite/` y `resources/db/mysql/`).
- Migraciones simples versionadas por `schema_version` en tabla `br_meta` (no se añade una librería de migraciones pesada tipo Flyway para mantener el jar liviano; migraciones incrementales con `if not exists` + `ALTER TABLE` idempotente en código).

---

## 7. Configuración

### 7.1 `config.yml` (extracto relevante)

```yaml
storage:
  type: sqlite            # sqlite | mysql
  mysql:
    host: localhost
    port: 3306
    database: bookreports
    user: root
    password: ""
    pool-size: 10

report:
  cooldown-seconds: 120
  daily-limit: 10
  session-timeout-seconds: 300
  prevent-self-report: true
  prevent-duplicate-pending: true   # no permite reportar 2 veces al mismo target por el mismo reporter mientras haya un PENDING

  categories:
    hacks:
      display: "&a⚔ Hacks / Cheats"
      priority: HIGH
      sub-reasons: [killaura, speed, fly, reach, xray, other]
    chat_abuse:
      display: "&e💬 Chat abusivo / Spam"
      priority: MEDIUM
      sub-reasons: [spam, harassment, advertising, other]
    inappropriate_name:
      display: "&6🏷 Nombre o skin inapropiado"
      priority: LOW
      sub-reasons: []
    bug_abuse:
      display: "&c🐛 Abuso de bugs / Exploits"
      priority: HIGH
      sub-reasons: []
    staff_impersonation:
      display: "&4🛡 Suplantación de staff"
      priority: HIGH
      sub-reasons: []
    other:
      display: "&7❓ Otro"
      priority: LOW
      sub-reasons: []

priority-escalation:
  distinct-reporters-threshold: 3
  window-seconds: 600
  escalate-to: HIGH

false-report-penalty:
  enabled: true
  threshold-in-30-days: 3
  cooldown-multiplier: 4          # multiplica el cooldown normal
  mute-minutes: 0                 # 0 = deshabilitado, >0 aplica mute vía hook

enable-report-tool: false

staff:
  alert-sound: "ENTITY_EXPERIENCE_ORB_PICKUP"
  claim-timeout-minutes: 15

discord:
  enabled: false
  webhook-url: ""
  min-priority-to-notify: HIGH

placeholderapi: true
locale: es_ES
```

### 7.2 `locale/es_ES.yml` / `locale/en_US.yml`
Todos los textos del libro, GUI y mensajes de chat externalizados (soporte `MiniMessage` de Adventure para formato: `<green>`, `<hover:show_text:'...'>`, etc.), con placeholders (`{player}`, `{category}`, `{ticket_id}`, `{cooldown}`).

---

## 8. Reglas anti-abuso

### 8.1 Cooldown
- Persistido en memoria (`Caffeine` cache `Map<UUID, Instant>`) más respaldo en DB (`br_report_penalties` con `reason='COOLDOWN'` solo si se desea cooldown persistente entre reinicios; por defecto in-memory basta ya que el cooldown es corto).

### 8.2 Límite diario
- Verificado contra `COUNT(*) FROM br_reports WHERE reporter_uuid=? AND created_at >= <inicio del día UTC>`, cacheado 60s por jugador para no golpear la DB en cada intento.

### 8.3 Reportes falsos / penalización
- Cuando staff marca `FALSE_REPORT`, se inserta fila en `br_report_penalties`.
- Si un jugador acumula `false-report-penalty.threshold-in-30-days`, se multiplica su cooldown (`cooldown-multiplier`) y, si está configurado, se aplica mute vía hook.

### 8.4 Prevención de reportes duplicados / brigading
- `prevent-duplicate-pending`: bloquea que el mismo reporter reporte al mismo target dos veces mientras el primer reporte siga `PENDING`/`IN_REVIEW` (se le informa el ticket existente).
- Cálculo de prioridad por consenso: si ≥N reporteros distintos reportan al mismo `target_uuid` en la misma `category_id` dentro de la ventana configurada, se auto-escala `priority` a `HIGH` y se agrupan visualmente en el GUI de staff (badge "3 reportes en los últimos 10 min").

---

## 9. Eventos de API pública (para otros plugins)

Paquete `dev.bookreports.api.event`, todos extienden `org.bukkit.event.Event` (algunos `Cancellable`):

```java
public class ReportCreateEvent extends Event implements Cancellable { /* antes de persistir */ }
public class ReportCreatedEvent extends Event { /* después de persistir, no cancelable */ }
public class ReportClaimedEvent extends Event { }
public class ReportResolvedEvent extends Event { /* incluye ReportResolution enum */ }
public class ReportFalseMarkedEvent extends Event { }
```

`ReportCreateEvent` cancelable permite a otros plugins (p. ej. anti-spam) vetar un reporte antes de guardarlo (ej. si el reportante está muteado).

### 9.1 API programática
`BookReportsAPI` (obtenida vía `Bukkit.getServicesManager().load(BookReportsAPI.class)`, registrado como `ServicePriority.Normal`):

```java
public interface BookReportsAPI {
    CompletableFuture<Report> submitReport(UUID reporter, UUID target, String categoryId, String subReasonId, String evidence);
    CompletableFuture<List<Report>> getReportHistory(UUID target);
    CompletableFuture<Optional<Report>> getReport(UUID reportUuid);
    boolean isOnCooldown(UUID reporter);
}
```

---

## 10. Comandos y permisos (resumen)

| Comando | Permiso | Descripción |
|---|---|---|
| `/report [jugador]` | `bookreports.report` (default: true) | Abre el libro de reporte. |
| `/report tool` | `bookreports.report.tool` | Entrega el item físico opcional. |
| `/report status` | `bookreports.report` | Lista el estado de los últimos reportes propios. |
| `/reportadmin list` | `bookreports.staff` | Abre cola de revisión (filtrable por estado, categoría, prioridad, "solo míos" y nombre del reportado). |
| `/reportadmin view <id>` | `bookreports.staff` | Abre detalle. |
| `/reportadmin claim <id>` | `bookreports.staff` | Reclama un reporte. |
| `/reportadmin resolve <id> <acción>` | `bookreports.staff.resolve` | Resuelve sin abrir GUI (uso en consola/scripts). |
| `/reportadmin history <jugador>` | `bookreports.staff` | Historial completo. |
| `/reportadmin notifications toggle` | `bookreports.staff.notify` | Activa/desactiva alertas. |
| `/reportadmin checkupdate` | `bookreports.admin` | Chequea de inmediato si hay una versión nueva (GitHub/Modrinth). |
| `/reportadmin update` | `bookreports.admin` | Descarga la última versión y la deja en `plugins/update/` para el próximo reinicio. |
| `/reportsreload` | `bookreports.admin` | Recarga config y locale en caliente. |

---

## 11. Integraciones opcionales

- **PlaceholderAPI**: `%bookreports_pending_count%`, `%bookreports_my_cooldown%`, `%bookreports_target_report_count%`.
- **LuckPerms**: solo para checks de permisos estándar (Vault-perms también soportado como fallback).
- **Sistema de castigos** (LiteBans, AdvancedBan, etc.): hook opcional vía **interfaz propia** `PunishmentBridge` con implementaciones por plugin detectado en `onEnable` (reflection/soft-depend); si ninguno está presente, las acciones rápidas de sanción del GUI quedan deshabilitadas y solo se permite "marcar como resuelto (acción externa)".
- **Discord**: webhook simple (HTTP POST JSON) enviado de forma async con `HttpClient` de Java 11+, sin librería externa.
- **Proxy (Velocity/BungeeCord)**: opcional, mediante Plugin Messaging Channel (`bookreports:sync`) para que reportes creados en un backend sean visibles/reclamables desde el panel de staff en cualquier servidor de la red que comparta la misma base de datos MySQL. La columna `server` identifica el origen.

---

## 12. Consideraciones de rendimiento y compatibilidad

- **Folia**: el plugin evita `BukkitScheduler` global cuando detecta Folia en runtime; usa `RegionScheduler`/`GlobalRegionScheduler` según corresponda (clase `SchedulerAdapter` con dos implementaciones, seleccionada por `Bukkit.getServer().getClass()` o el método estándar de detección de Folia). Toda tarea de apertura de libro/GUI se ejecuta en el hilo de la entidad correspondiente.
- **Async DB**: ninguna consulta se ejecuta en el hilo principal; `ReportService` retorna `CompletableFuture` y los callbacks que tocan la API de Bukkit (abrir GUI, enviar mensajes) se re-despachan al hilo correcto vía el `SchedulerAdapter`.
- **Cache**: Caffeine para sesiones activas, cooldowns y contadores diarios, con tamaño máximo acotado (`maximumSize(10_000)`) para evitar fugas de memoria en servidores con mucho tráfico de reportes.
- **Tamaño del jar**: sin dependencias pesadas; único shading necesario es HikariCP + driver MySQL (SQLite JDBC embebido) + Caffeine, todos relocados bajo `dev.bookreports.libs.*`.

---

## 13. Seguridad

- Todos los comandos internos del flujo (`/bookreports-select ...`) están registrados como comando **no listado** (no aparece en `/help`, `tab-complete` deshabilitado) y validan:
  1. Que el ejecutor sea el `reporter` dueño de la sesión.
  2. Que el `sessionId` exista y no haya expirado.
  3. Que la transición de estado sea válida (previene "saltos" de página vía comandos copiados/pegados).
- Rate limiting a nivel de comando (`bookreports.report` limitado a 1 ejecución/seg vía filtro simple) para mitigar spam de apertura de libros.
- Sanitización de `evidence_text`: strip de códigos de color/formato inyectados, longitud máxima aplicada tanto en el anvil GUI (límite de cliente) como server-side (defensa en profundidad).
- Los nombres de jugador se guardan como snapshot pero toda lógica de negocio usa `UUID` como identidad canónica (soporta cambios de nombre).

---

## 14. Estructura de paquetes propuesta

```
dev.bookreports
├── BookReportsPlugin.java
├── api/
│   ├── BookReportsAPI.java
│   └── event/ (ReportCreateEvent, ReportCreatedEvent, ...)
├── command/
│   ├── ReportCommand.java
│   ├── ReportAdminCommand.java
│   └── internal/ SelectOptionCommand.java   (comando oculto del flujo del libro)
├── session/
│   ├── ReportSession.java
│   └── SessionManager.java
├── book/
│   ├── BookBuilder.java
│   ├── pages/ (TargetConfirmPage, CategoryPage, SubReasonPage, SummaryPage, ResultPage)
├── gui/
│   ├── ReportQueueView.java
│   ├── ReportDetailView.java
│   └── AnvilInputGUI.java
├── service/
│   ├── ReportService.java
│   ├── CooldownService.java
│   └── PriorityCalculator.java
├── storage/
│   ├── StorageManager.java
│   ├── dao/ ReportDao.java, PenaltyDao.java
│   └── model/ Report.java, ReportPenalty.java
├── integration/
│   ├── DiscordNotifier.java
│   ├── PunishmentBridge.java (+ impls)
│   └── PlaceholderExpansionImpl.java
├── config/
│   ├── ConfigManager.java
│   └── LocaleManager.java
└── util/
    ├── SchedulerAdapter.java
    └── ComponentUtil.java
```

---

## 15. Plan de pruebas

- **Unitarias**: `PriorityCalculator`, `SessionManager` (transiciones válidas/ inválidas), `CooldownService`, parsers de config.
- **Integración**: `MockBukkit` para simular flujo completo `/report` → selección de páginas → persistencia en SQLite en memoria (`jdbc:sqlite::memory:`).
- **Manuales / QA en servidor de pruebas**:
  - Flujo feliz completo con 2 cuentas.
  - Cooldown y límite diario.
  - Reporte duplicado bloqueado.
  - Escalado de prioridad por consenso (3 reporteros).
  - Panel de staff: reclamar, resolver, marcar falso, penalización aplicada.
  - Recarga en caliente (`/reportsreload`) no rompe sesiones activas.
  - Comportamiento en Folia (si se soporta) y en servidor Paper estándar.

---

## 16. Dependencias (build.gradle.kts, resumen)

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // MySQL driver aportado por el propio servidor (Paper lo incluye) o shading opcional
}
```

- Todas las `implementation` se relocan con el plugin `com.gradleup.shadow` para evitar colisiones con otros plugins del servidor.

---

## 17. Roadmap (fuera de v1, referencia futura)

- Adjuntar capturas de pantalla/clip vía integración con servicio externo de subida (solo enlace, no hosting propio).
- Panel web de staff (fuera del cliente de Minecraft) consumiendo la misma base de datos vía API REST opcional.
- Auto-moderación básica (detección de palabras prohibidas) que sugiera categoría automáticamente.
