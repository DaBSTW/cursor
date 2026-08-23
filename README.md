# 📕 BookReports

**Sistema de reportes por libro para Minecraft, al estilo Hypixel.**
Sin comandos que memorizar, sin formularios en el chat: el jugador escribe `/report`, se abre un libro y todo lo demás son clics.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20--%2026.2-brightgreen)](https://papermc.io)
[![Paper](https://img.shields.io/badge/Server-Paper%20%7C%20Purpur%20%7C%20Folia-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](./LICENSE)

---

## ✨ ¿Por qué BookReports?

Los sistemas de reportes clásicos fallan por lo mismo: le piden al jugador que escriba `/report Steve hacks volando en el spawn` y esperan que el staff descifre eso. El resultado son reportes vagos, sin categorizar y difíciles de priorizar.

BookReports invierte el flujo: **el jugador solo elige**. El plugin le presenta un libro navegable donde selecciona a quién reporta, por qué, y opcionalmente añade un detalle. El staff recibe un ticket estructurado, categorizado y priorizado automáticamente.

| | Sistema clásico | BookReports |
|---|---|---|
| Entrada del jugador | Texto libre en el chat | Clics en un libro |
| Categorización | Manual, por el staff | Automática, desde el flujo |
| Priorización | Ninguna | Por categoría + consenso de reportantes |
| Revisión | Leer el chat / logs | Panel GUI con reclamo y resolución |
| Anti-abuso | Nada o cooldown básico | Cooldown, límite diario, anti-duplicado, penalización por falsos |

---

## 🎬 El flujo en 20 segundos

```
/report Steve
   │
   ├─ 📖 Página 1  ¿Reportar a Steve?          [ Continuar ] [ Cancelar ]
   │
   ├─ 📖 Página 2  Categoría                    ⚔ Hacks  💬 Chat  🏷 Nombre  🐛 Bugs …
   │
   ├─ 📖 Página 3  Sub-motivo                   Killaura  Speed  Fly  Reach  X-Ray
   │
   ├─ 📖 Página 4  ¿Detalles?                   [ Añadir ] → yunque  |  [ Omitir ]
   │
   ├─ 📖 Página 5  Resumen                       [ ✔ Enviar ]  [ ✘ Cancelar ]
   │
   └─ ✅ Reporte #REP-000123 enviado. Gracias.
```

Y del lado del staff:

```
/reportadmin
   │
   └─ 🗂 Cola de revisión (GUI)
        ├─ 🔴 #123  Steve — Hacks/Killaura      3 reportantes · hace 2 min
        ├─ 🟡 #122  Alex  — Chat/Spam           hace 14 min
        └─ ⚪ #121  Herobrine — Nombre          hace 1 h
             │
             └─ Detalle → [Reclamar] [Teleport] [Historial] [Sancionar] [Rechazar] [Falso]
```

---

La cola de revisión (`/reportadmin`) además filtra por prioridad, busca por nombre del jugador reportado, y tiene un filtro "solo mis reclamos" — útil en colas grandes con varios miembros de staff activos a la vez.

## 🚀 Instalación

1. Descarga `BookReports-1.0.0.jar` desde [Releases](../../releases) (o Modrinth / Hangar).
2. Colócalo en la carpeta `plugins/` de tu servidor **Paper 1.21.x – 26.2**.
3. Reinicia el servidor.
4. Listo. Funciona con la configuración por defecto y SQLite — sin base de datos externa.

**Requisitos:**
- Paper **1.21.x hasta 26.2** (o Purpur / Folia) — `api-version: '1.21'` es un mínimo, no un techo: el jar funciona sin cambios en cualquier build igual o más nueva, incluyendo el nuevo esquema de versionado `año.drop` de Mojang (26.1, 26.2, …).
- Java 21+ (corre igual de bien bajo runtimes más nuevos, como los que exige Paper 26.x)

> ⚠️ Spigot y CraftBukkit no están soportados: el flujo del libro depende de los componentes de Adventure nativos de Paper.

---

## ⚙️ Configuración

Todo es configurable sin recompilar. Extracto de `config.yml`:

```yaml
report:
  cooldown-seconds: 120
  daily-limit: 10
  prevent-self-report: true
  prevent-duplicate-pending: true

  categories:
    hacks:
      display: "&a⚔ Hacks / Cheats"
      priority: HIGH
      sub-reasons: [killaura, speed, fly, reach, xray, other]
    chat_abuse:
      display: "&e💬 Chat abusivo / Spam"
      priority: MEDIUM
      sub-reasons: [spam, harassment, advertising, other]

priority-escalation:
  distinct-reporters-threshold: 3   # 3 jugadores distintos en 10 min…
  window-seconds: 600
  escalate-to: HIGH                 # …escalan el reporte automáticamente

storage:
  type: sqlite                      # sqlite | mysql
```

Añadir una categoría nueva es añadir una entrada al YAML y ejecutar `/reportsreload`. El libro se regenera solo.

**Idiomas incluidos:** `en_US`, `es_ES`, `pt_BR`, `de_DE`, `fr_FR`, `ru_RU`, `zh_CN`. Todos los textos usan [MiniMessage](https://docs.advntr.dev/minimessage/format.html), así que puedes reescribirlos por completo desde `locale/`, o añadir tu propio idioma con un archivo `locale/<código>.yml` nuevo.

---

## 🕹️ Comandos y permisos

### Jugadores

| Comando | Permiso | Descripción |
|---|---|---|
| `/report <jugador>` | `bookreports.report` *(default: true)* | Abre el libro apuntando a ese jugador |
| `/report` | `bookreports.report` | Abre el libro con la lista de jugadores online |
| `/report tool` | `bookreports.report.tool` | Entrega el ítem de reporte rápido *(opcional)* |
| `/report status` | `bookreports.report` | Muestra el estado de tus últimos reportes propios |

### Staff

| Comando | Permiso | Descripción |
|---|---|---|
| `/reportadmin` · `/reports` | `bookreports.staff` | Abre la cola de revisión |
| `/reportadmin view <id>` | `bookreports.staff` | Detalle de un reporte |
| `/reportadmin claim <id>` | `bookreports.staff` | Reclama un reporte (evita trabajo duplicado) |
| `/reportadmin teleport <id>` | `bookreports.staff` | Te teletransporta al jugador reportado |
| `/reportadmin resolve <id> <acción>` | `bookreports.staff.resolve` | Resuelve desde consola |
| `/reportadmin history <jugador>` | `bookreports.staff` | Historial completo de un jugador |
| `/reportadmin notifications toggle` | `bookreports.staff.notify` | Alertas en vivo on/off |
| `/reportadmin stats reporter <jugador>` | `bookreports.staff` | Precisión histórica de un reportante |
| `/reportadmin stats staff <jugador>` | `bookreports.staff` | Reportes resueltos y tiempo promedio de un staff |
| `/reportsreload` | `bookreports.admin` | Recarga config y traducciones en caliente |
| `/reportadmin checkupdate` | `bookreports.admin` | Chequea de inmediato si hay una versión nueva |
| `/reportadmin update` | `bookreports.admin` | Descarga y prepara la última versión (un clic desde el aviso en el chat) |

---

## 🛡️ Anti-abuso

BookReports asume que alguien intentará abusar del sistema de reportes, y lo maneja de fábrica:

- **Cooldown** configurable entre reportes.
- **Límite diario** por jugador.
- **Anti-duplicado**: no puedes reportar dos veces al mismo jugador mientras tu reporte siga abierto.
- **Anti-brigading**: si varios jugadores reportan al mismo objetivo por lo mismo, se agrupan en un solo caso priorizado en lugar de inundar la cola.
- **Penalización por reportes falsos**: el staff puede marcar un reporte como falso; la reincidencia multiplica el cooldown del reportante.
- **Sesiones firmadas**: cada clic del libro se valida contra el dueño de la sesión, su vigencia y la transición de estado esperada. Copiar el comando interno de otro jugador no funciona.

---

## 📎 Evidencia automática y métricas

- **Contexto de chat automático**: BookReports mantiene un buffer corto (últimas ~10 líneas) del chat de cada jugador. Al crear un reporte, ese contexto se adjunta automáticamente al ticket — el reportante no tiene que escribir ni copiar nada, y el staff ve de inmediato lo que dijo el jugador reportado justo antes.
- **Evidencia automática de CoreProtect** *(opcional)*: si CoreProtect está instalado, cada reporte nuevo se adjunta un resumen de la actividad reciente de bloques del jugador reportado (ej. `3x break, 1x place (last 5m)`) — configurable en `coreprotect:` (`config.yml`), desactivable sin afectar el resto del plugin.
- **Sanción vinculada al reporte**: cuando el staff sanciona desde el menú rápido, el tipo de sanción y su duración (ej. `BAN (7d)`) quedan grabados en el propio ticket, no solo como una nota de texto libre — auditable desde el detalle del reporte.
- **Precisión de reportantes**: cada reportante acumula un % de precisión (reportes que terminaron en sanción vs. rechazados/falsos) visible en el detalle del ticket y vía `/reportadmin stats reporter <jugador>` — útil para dar más peso a reportantes confiables.
- **Rendimiento de staff**: `/reportadmin stats staff <jugador>` muestra cuántos reportes ha resuelto cada miembro del staff y su tiempo promedio de reclamo a resolución, para evaluar carga y actividad del equipo.

---

## 🔌 Integraciones

Todas opcionales — si el plugin no está presente, la función simplemente se desactiva sin errores.

- **PlaceholderAPI** — `%bookreports_pending_count%`, `%bookreports_my_cooldown%`, `%bookreports_target_report_count%`
- **LiteBans / AdvancedBan / EssentialsX** — sanciones con un clic desde el panel de staff (en ese orden de preferencia si hay más de uno instalado)
- **CoreProtect** — adjunta automáticamente actividad reciente de bloques del jugador reportado como evidencia
- **Discord** — webhook que avisa al staff offline al crearse un reporte de alta prioridad, y de nuevo cuando se resuelve
- **Velocity / BungeeCord** — panel de staff unificado en toda la red sobre MySQL compartido
- **LuckPerms / Vault** — permisos estándar
- **bStats** — estadísticas de uso anónimas y agregadas (servidor, versión, config), desactivable con `metrics.enabled: false`
- **Chequeo automático de versión** — avisa por consola (y opcionalmente a los ops al conectarse, con un texto clicable) si hay una versión más nueva disponible en GitHub Releases o Modrinth, configurable en `update-checker:`. Nunca bloquea el arranque ni falla si no hay red.
  - **Actualización con un clic**: el aviso a los ops incluye un `[Actualizar ahora]` clicable — al tocarlo, el plugin descarga el jar nuevo y lo deja preparado en `plugins/update/` (el mecanismo propio de Bukkit/Paper), listo para instalarse solo la próxima vez que el servidor reinicie. Nunca reinicia el servidor por su cuenta ni reemplaza el jar en caliente — eso no es seguro de hacer con el servidor corriendo.
  - **Bloqueo por versión desactualizada** (activado siempre, no configurable): mientras el checker sepa que hay una versión más nueva, `/report` y el ítem de reporte dejan de funcionar para cualquiera sin `bookreports.admin` (ven un simple "servicio no disponible"), mientras que los ops siguen con acceso normal más el aviso de actualización con un clic. La única forma de evitarlo es mantener BookReports actualizado, o apagar el chequeo entero con `update-checker.enabled: false`.

---

## 🧩 API para desarrolladores

BookReports expone eventos de Bukkit y una API programática registrada en el `ServicesManager`.

```java
BookReportsAPI api = Bukkit.getServicesManager().load(BookReportsAPI.class);

api.submitReport(reporterUuid, targetUuid, "hacks", "killaura", "Volando en el spawn")
   .thenAccept(report -> getLogger().info("Ticket creado: #" + report.id()));
```

Eventos disponibles:

```java
@EventHandler
public void onReportCreate(ReportCreateEvent event) {
    // Cancelable: veta el reporte antes de que se guarde
    if (isMuted(event.getReporter())) {
        event.setCancelled(true);
    }
}
```

| Evento | Cancelable | Cuándo |
|---|---|---|
| `ReportCreateEvent` | ✅ | Antes de persistir |
| `ReportCreatedEvent` | ❌ | Después de persistir |
| `ReportClaimedEvent` | ❌ | Al reclamar |
| `ReportResolvedEvent` | ❌ | Al resolver |
| `ReportFalseMarkedEvent` | ❌ | Al marcar como falso |

---

## 🏗️ Compilar desde el código fuente

```bash
git clone https://github.com/DaBSTW/cursor.git
cd cursor
./gradlew build
```

El jar final queda en `build/libs/BookReports-<version>.jar`.

```bash
./gradlew test          # tests unitarios y de integración (MockBukkit + SQLite en memoria)
./gradlew runServer     # levanta un Paper de pruebas con el plugin ya cargado
```

---

## 📊 Rendimiento

- **Cero consultas SQL en el hilo principal.** Toda la persistencia es asíncrona; los callbacks vuelven al hilo correcto vía un adaptador compatible con Bukkit y Folia.
- **Cachés acotadas** (Caffeine) para sesiones, cooldowns y contadores diarios.
- **Jar ligero**: sin frameworks pesados, solo HikariCP, SQLite JDBC y Caffeine, todos relocalizados para no chocar con otros plugins.

---

## 📤 Dónde publicarlo (gratis)

El chequeo automático de versión (`update-checker:`) ya sabe leer **GitHub Releases** y **Modrinth** de fábrica — publicar en cualquiera de los dos lo deja funcionando sin tocar código:

- **[Modrinth](https://modrinth.com)** — recomendado como principal: API limpia y estable, CDN rápido, buen descubrimiento, y es donde cada vez más admins de Paper/Purpur/Folia buscan primero.
- **[Hangar](https://hangar.papermc.io)** — el repositorio oficial de PaperMC, gratis, pensado específicamente para plugins de Paper/Velocity/Folia. Buena visibilidad dentro del propio ecosistema Paper.
- **GitHub Releases** — si el código ya vive en GitHub (como este repo), crear un Release ahí es gratis e inmediato — `update-checker.source: github` (el valor por defecto) ya apunta a `DaBSTW/cursor`.
- **[SpigotMC](https://www.spigotmc.org/resources/)** — la comunidad más grande y establecida; vale la pena para alcance, aunque el checker de este plugin no lee spigot todavía.

No hace falta elegir solo uno — publicar en varios a la vez (cross-posting) es normal y gratis en todos los casos.

## 📚 Documentación

- [`SPECS.md`](./SPECS.md) — especificación técnica completa
- [`ROADMAP.md`](./ROADMAP.md) — ruta de trabajo por fases
- [Wiki](../../wiki) — guías de configuración e integración

---

## 🤝 Contribuir

Los PRs son bienvenidos. Antes de abrir uno:

1. Lee [`SPECS.md`](./SPECS.md) — el diseño está documentado, y las decisiones tienen su razón.
2. Ejecuta `./gradlew build test` y asegúrate de que pasa.
3. Añade tests para cualquier regla de negocio nueva (especialmente en anti-abuso y en la máquina de estados del libro).

Para reportar bugs o proponer features, abre un [issue](../../issues).

---

## 📄 Licencia

MIT — ver [`LICENSE`](./LICENSE).
