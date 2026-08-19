# 📕 BookReports

**Sistema de reportes por libro para Minecraft, al estilo Hypixel.**
Sin comandos que memorizar, sin formularios en el chat: el jugador escribe `/report`, se abre un libro y todo lo demás son clics.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen)](https://papermc.io)
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

## 🚀 Instalación

1. Descarga `BookReports-1.0.0.jar` desde [Releases](../../releases) (o Modrinth / Hangar).
2. Colócalo en la carpeta `plugins/` de tu servidor **Paper 1.21.x**.
3. Reinicia el servidor.
4. Listo. Funciona con la configuración por defecto y SQLite — sin base de datos externa.

**Requisitos:**
- Paper 1.21.x (o Purpur / Folia)
- Java 21+

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

**Idiomas incluidos:** `es_ES`, `en_US`. Todos los textos usan [MiniMessage](https://docs.advntr.dev/minimessage/format.html), así que puedes reescribirlos por completo desde `locale/`.

---

## 🕹️ Comandos y permisos

### Jugadores

| Comando | Permiso | Descripción |
|---|---|---|
| `/report <jugador>` | `bookreports.report` *(default: true)* | Abre el libro apuntando a ese jugador |
| `/report` | `bookreports.report` | Abre el libro con la lista de jugadores online |
| `/report tool` | `bookreports.report.tool` | Entrega el ítem de reporte rápido *(opcional)* |

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
| `/reportsreload` | `bookreports.admin` | Recarga config y traducciones en caliente |

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

## 🔌 Integraciones

Todas opcionales — si el plugin no está presente, la función simplemente se desactiva sin errores.

- **PlaceholderAPI** — `%bookreports_pending_count%`, `%bookreports_my_cooldown%`, `%bookreports_target_report_count%`
- **LiteBans / AdvancedBan** — sanciones con un clic desde el panel de staff
- **Discord** — webhook para avisar al staff offline de reportes de alta prioridad
- **Velocity / BungeeCord** — panel de staff unificado en toda la red sobre MySQL compartido
- **LuckPerms / Vault** — permisos estándar

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
