# 📕 BookReports

**El sistema de reportes por libro para Minecraft, al estilo Hypixel.**
Sin comandos que memorizar, sin formularios en el chat: el jugador escribe `/report`, se abre un libro, y todo lo demás son clics. El staff recibe un ticket estructurado, categorizado y priorizado automáticamente — sin tener que descifrar reportes vagos escritos a mano.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20--%2026.2-brightgreen)](https://papermc.io)
[![Paper](https://img.shields.io/badge/Server-Paper%20%7C%20Purpur%20%7C%20Folia-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](./LICENSE)
[![Price](https://img.shields.io/badge/Precio-Gratis-success)](#-instalación-en-30-segundos)

---

## ✨ ¿Por qué BookReports?

Los sistemas de reportes clásicos fallan siempre por lo mismo: le piden al jugador que escriba `/report Steve hacks volando en el spawn` y esperan que el staff descifre eso a las 3 AM. El resultado son reportes vagos, sin categorizar, imposibles de priorizar.

BookReports invierte el flujo: **el jugador solo elige**. Un libro navegable le pregunta a quién reporta, por qué, y opcionalmente le deja añadir un detalle. Nada de eso llega al staff como texto libre — llega como un ticket ya estructurado.

| | Sistema clásico | BookReports |
|---|---|---|
| Entrada del jugador | Texto libre en el chat | Clics en un libro |
| Categorización | Manual, por el staff | Automática, desde el flujo |
| Priorización | Ninguna | Por categoría + consenso de reportantes |
| Revisión | Leer el chat / logs a mano | Panel GUI con reclamo, historial y resolución |
| Evidencia | El staff la busca aparte | Contexto de chat y actividad de bloques, adjuntos solos |
| Anti-abuso | Nada, o un cooldown básico | Cooldown, límite diario, anti-duplicado, anti-falsos |

---

## 🎬 Así se ve

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

La cola además filtra por prioridad, tiene un toggle "solo mis reclamos", y busca por nombre del jugador reportado — pensada para colas grandes con varios miembros de staff activos a la vez.

---

## 🚀 Instalación en 30 segundos

1. Descargá el `.jar` más reciente.
2. Soltalo en la carpeta `plugins/` de tu servidor.
3. Reiniciá el servidor.
4. Listo. Arranca con SQLite desde el primer segundo — sin base de datos externa, sin configuración previa.

**Requisitos:**
- Paper **1.21.x hasta 26.2** (o Purpur / Folia) — `api-version: '1.21'` es un piso, no un techo: el jar funciona sin cambios en cualquier build igual o más nueva, incluyendo el nuevo esquema de versionado `año.drop` de Mojang (26.1, 26.2, …).
- Java 21+ (corre igual de bien bajo runtimes más nuevos, como los que exige Paper 26.x).

> ⚠️ Requiere **Paper** o un fork de Paper (Purpur, Folia). Spigot/CraftBukkit puro no está soportado: el flujo del libro depende de los componentes nativos de Adventure que solo Paper expone.

---

## 🧩 Funcionalidades

### 🎮 Para los jugadores
- Reporte guiado 100% por clics — nada de texto libre obligatorio.
- Categorías y sub-motivos totalmente configurables, sin recompilar nada.
- `/report status`: seguí el estado de tus reportes sin tener que preguntarle a nadie.
- Item físico opcional (`/report tool`) — clic derecho sobre un jugador y listo.

### 🛠️ Para el staff
- Cola de revisión en GUI: filtra por estado, categoría, prioridad, "solo mis reclamos", y busca por nombre del jugador reportado.
- Reclamo con protección anti-duplicado — dos miembros del staff no pueden trabajar el mismo ticket a la vez.
- Sanciona con un clic (LiteBans / AdvancedBan / EssentialsX) directamente desde el detalle del reporte.
- Historial completo por jugador, con teletransporte de un clic.
- Alertas en vivo para reportes de alta prioridad, con toggle on/off por staff.

### 📎 Evidencia automática — la parte que ahorra tiempo real
- **Contexto de chat automático**: las últimas líneas que dijo el jugador reportado quedan adjuntas solas al ticket. El reportante no escribe ni copia nada.
- **CoreProtect** *(opcional)*: si está instalado, cada reporte se adjunta un resumen de la actividad reciente de bloques del reportado (ej. `3x break, 1x place (last 5m)`).
- **Sanción auditada**: qué se aplicó y por cuánto tiempo queda grabado en el propio ticket, no solo como una nota de texto libre que alguien puede escribir mal.
- **Precisión de reportantes**: cada jugador acumula un % de aciertos histórico — identificá a tus reportantes confiables de un vistazo.
- **Rendimiento de staff**: reportes resueltos y tiempo promedio de reclamo a resolución, por cada miembro del equipo.

### 🛡️ Anti-abuso, de fábrica
- Cooldown configurable + límite diario por jugador.
- Anti-duplicado: no podés reportar dos veces al mismo jugador mientras el reporte siga abierto.
- Anti-brigading: varios reportes del mismo caso se agrupan en un solo ticket priorizado, en vez de inundar la cola.
- Penalización automática por reportes falsos reincidentes (multiplica el cooldown).
- Sesiones firmadas: cada clic del libro se valida contra su dueño, su vigencia y la transición esperada — nadie puede reenviar o falsificar el clic de otro jugador.

### 🌍 Multi-idioma
7 idiomas incluidos de fábrica — `en_US`, `es_ES`, `pt_BR`, `de_DE`, `fr_FR`, `ru_RU`, `zh_CN` — totalmente editables sin tocar código, vía [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Sumar un idioma nuevo es soltar un archivo `locale/<código>.yml` más.

### 🔄 Se actualiza casi solo
Chequeo automático de nuevas versiones (GitHub Releases o Modrinth), con aviso a los ops y **actualización con un solo clic** — el plugin se descarga la versión nueva y la deja lista para instalarse sola en el próximo reinicio. Nunca reinicia el servidor por su cuenta ni toca el jar en caliente.

---

## 🔌 Integraciones

Todas opcionales — si el plugin correspondiente no está instalado, la función simplemente se apaga sola, sin errores ni configuración extra.

| Integración | Qué hace |
|---|---|
| **LiteBans / AdvancedBan / EssentialsX** | Sanciones con un clic desde el panel de staff (en ese orden de preferencia si hay más de una instalada) |
| **CoreProtect** | Adjunta automáticamente actividad reciente de bloques del jugador reportado como evidencia |
| **PlaceholderAPI** | `%bookreports_pending_count%`, `%bookreports_my_cooldown%`, `%bookreports_target_report_count%` |
| **Discord** | Webhook que avisa al staff offline al crearse un reporte de alta prioridad, y de nuevo al resolverse |
| **Velocity / BungeeCord** | Panel de staff unificado en toda la red, sobre una base de datos MySQL compartida |
| **Vault** (+ LuckPerms u otro) | Muestra el prefijo/sufijo de rango junto al nombre en la cola de staff y el detalle del reporte |
| **bStats** | Estadísticas de uso anónimas y agregadas, desactivable con `metrics.enabled: false` |

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

Añadir una categoría nueva es añadir una entrada al YAML y ejecutar `/reportsreload` — el libro se regenera solo, sin reiniciar el servidor.

---

## 📊 Rendimiento

- **Cero consultas SQL en el hilo principal.** Toda la persistencia es asíncrona; los callbacks vuelven al hilo correcto vía un adaptador compatible tanto con Bukkit como con Folia.
- **Cachés acotadas** (Caffeine) para sesiones, cooldowns y contadores diarios — nada crece sin límite.
- **Jar liviano**: sin frameworks pesados, solo HikariCP, SQLite JDBC y Caffeine, todos relocalizados para no chocar con otros plugins.

---

## ❓ Preguntas frecuentes

**¿Es gratis?**
Sí, 100% gratis y de código abierto bajo licencia MIT.

**¿En qué versiones funciona?**
Cualquier build de Paper (o Purpur/Folia) entre **1.21.x y 26.2**, incluyendo el nuevo esquema de versionado `año.drop` de Mojang.

**¿Necesito configurar una base de datos?**
No. Usa SQLite por defecto, cero configuración. Si administrás una red de servidores, podés apuntar a MySQL compartido para tener un panel de staff unificado en toda la red.

**¿Funciona si no tengo LiteBans, EssentialsX o CoreProtect?**
Sí. Todas las integraciones son opcionales — sin ellas instaladas, esas funciones simplemente no aparecen, sin errores ni configuración extra.

**¿Le pega al rendimiento del servidor?**
No debería: cero consultas SQL en el hilo principal, cachés acotadas para lo que se consulta seguido, y un jar liviano sin dependencias pesadas.

**¿Soporta Folia?**
Sí, vía un adaptador de scheduler que detecta automáticamente si el servidor corre Bukkit o Folia.

**¿Funciona en Spigot puro?**
No — depende de componentes nativos de Adventure que solo Paper (y sus forks) exponen.

**¿Cómo me entero de las actualizaciones?**
El plugin chequea solo si hay una versión nueva y te avisa por consola, y a los ops al conectarse con un botón clicable — con un clic descarga la actualización y queda lista para el próximo reinicio.

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

## 📚 Más información

- [`SPECS.md`](./SPECS.md) — especificación técnica completa
- [`ROADMAP.md`](./ROADMAP.md) — ruta de trabajo por fases
- [`CHANGELOG.md`](./CHANGELOG.md) — historial de versiones

### Para desarrolladores

```bash
git clone https://github.com/DaBSTW/cursor.git
cd cursor
./gradlew build          # el jar queda en build/libs/
./gradlew test           # tests unitarios y de integración (MockBukkit + SQLite en memoria)
```

Los PRs son bienvenidos — antes de abrir uno, leé [`SPECS.md`](./SPECS.md), corré `./gradlew build test`, y sumá tests para cualquier regla de negocio nueva. Para bugs o ideas, abrí un [issue](../../issues).

---

## 📄 Licencia

MIT — ver [`LICENSE`](./LICENSE).
