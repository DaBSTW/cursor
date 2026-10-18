# Ficha de Modrinth: Pregen

Todo lo necesario para publicar Pregen en Modrinth. Copia cada bloque en su campo.

---

## 1. Campos del formulario

| Campo | Valor |
|---|---|
| **Project type** | Plugin |
| **Name** | `Pregen` |
| **URL (slug)** | `pregen` (si está ocupado: `pregen-plugin` o `pregen-chunks`) |
| **Summary** (máx. 256) | `Safe, adaptive chunk pre-generator for Paper & Folia 1.21–26.x. Auto-throttles to protect your TPS, dry-run trims with automatic backups, per-admin selections, Brigadier commands and a boss bar.` |
| **Client side** | Unsupported |
| **Server side** | Required |
| **Loaders** | Paper, Folia, Purpur |
| **Game versions** | 1.21 → 1.21.11 y 26.1 → 26.3 (marca todas las de esos rangos) |
| **Categories** | `Optimization`, `Utility`, `World Generation`, `Management` (Modrinth deja elegir hasta 3 principales; usa las tres primeras y `Management` como adicional) |
| **License** | Elige una antes de publicar (ver sección 7) |
| **Source code** | URL de tu repositorio |
| **Issues** | URL de issues de tu repositorio |
| **Discord** | Opcional |

**Versión 1.0.0** (pestaña *Versions → Create version*)

| Campo | Valor |
|---|---|
| Version number | `1.0.0` |
| Version title | `Pregen 1.0.0` |
| Release channel | Release |
| Loaders | Paper, Folia, Purpur |
| Game versions | Las mismas de arriba |
| File | `Pregen-1.0.0.jar` |
| Dependencies | Ninguna |

---

## 2. Descripción (inglés; pégala en *Description*)

```markdown
# Pregen

**Pre-generate your world without hurting your server.**
Pregen generates terrain ahead of time so players never lag while exploring, and it does it *safely*: it watches your tick time and slows itself down before your TPS drops, and trimming chunks never touches a loaded world.

One jar for **Paper, Folia and Purpur, from 1.21 to 26.3**. No dependencies. No telemetry.

---

## ✨ Why Pregen

### 🧠 It protects your TPS on its own
Most pre-generators push a fixed number of chunks no matter what. Pregen reads the server's tick time every second and **adapts its speed automatically**: it backs off as soon as ticks get slow and speeds up again when there is headroom. You can also hold generation while players are online and let it run when the server is empty.

### 🛡️ Trimming that can't corrupt your world
Deleting chunks from region files while the world is loaded is risky. Pregen does it in three safe steps:
1. **Dry run**: `/pregen trim outside` shows exactly how many chunks and regions would go and how much space you'd free. Nothing is touched.
2. **Confirm**: `/pregen confirm` schedules it.
3. **Next restart**: the trim runs **before any world loads**, after an **automatic backup** of every affected region.

Interrupted by a crash? It simply runs again: it's idempotent and never overwrites your original backup. You can even keep only the chunks players actually visited (`inhabited <ticks>`).

### 👥 Built for teams
Every admin (and the console) has **their own selection**. Two staff members can set up different areas at the same time without overwriting each other.

### ⌨️ Modern commands
Native **Brigadier** commands with tab completion, red inline errors and per-subcommand permissions, so players only see what they can use. Clickable help. Radius in blocks, chunks, regions or thousands: `500`, `32c`, `2r`, `5k`, `+100`.

### 📊 See what's happening
- **Boss bar** with live progress per world (`/pregen watch`)
- Speed (chunks/s), **ETA** and elapsed time that survives restarts
- `/pregen doctor` checks your server setup and tells you what's slowing generation down

### 🌍 Speaks your players' language
Spanish and English included. Every player sees messages in **their own client language**. All texts are editable with **MiniMessage** (gradients, hover, click).

### 💾 Never lose progress
Progress is saved atomically every few seconds and on shutdown. Tasks **resume automatically** after a restart, even after a crash.

---

## 📐 Shapes & patterns

**9 shapes:** square, rectangle, circle, ellipse, diamond, triangle, pentagon, hexagon, star.

**6 generation orders:**
| Pattern | Best for |
|---|---|
| `region` *(default)* | Finishes each 32×32 region file at once: the easiest on your disk |
| `spiral` | Every chunk next to the previous one |
| `concentric` | Square rings from the center |
| `loop` | Row by row |
| `csv` | Your own list of chunks |
| `world` | Re-load every existing chunk (e.g. to upgrade an old world) |

---

## 🚀 Quick start

```
/pregen center spawn
/pregen radius 5k
/pregen shape circle
/pregen start
/pregen watch
```
Or in one line: `/pregen start world circle 5k 0 0`

---

## 📜 Commands

| Command | Description |
|---|---|
| `/pregen start [world] [shape] [radius] [x z]` | Start generating |
| `/pregen pause \| resume \| cancel [world]` | Control tasks |
| `/pregen status` · `/pregen watch` | Progress in chat or boss bar |
| `/pregen world \| center \| radius \| corners \| worldborder \| shape \| pattern` | Build your selection |
| `/pregen trim <outside\|inside> [inhabited <ticks>]` | Dry run, then schedule a trim |
| `/pregen trim pending \| unschedule <world>` | Manage scheduled trims |
| `/pregen doctor` · `/pregen reload` | Diagnostics and reload |

Alias: `/pg`

## 🔑 Permissions
- `pregen.admin`: everything, including trim
- `pregen.operator`: generation only, no destructive or admin commands (great for moderators)
- `pregen.command.<name>`: fine-grained per command
- `pregen.notify`: receive start/finish/error announcements

---

## ⚙️ Configuration highlights
- `max-concurrent-chunks`: one budget **shared by all tasks**, so three worlds don't triple the load
- `adaptive-throttle`: target milliseconds per tick
- `pause-when-players-online`: generate only on an empty server
- `radius-limit`: cap the radius (handy for hosting providers)
- `trim.backup`: automatic backups before trimming (on by default)

Invalid values never break the plugin: they fall back to safe defaults with one clear console warning.

---

## 🔌 For developers
A documented API (`PregenApi` via Bukkit's services manager) plus real **Bukkit events**: `PregenTaskStartEvent` (cancellable), `PregenTaskProgressEvent`, `PregenTaskFinishEvent`, `PregenTrimScheduledEvent` (cancellable).

---

## 🆚 Coming from Chunky?
Chunky is a great, battle-tested tool. Pregen takes a different approach to the things that matter most on a live server:

| | Pregen | Chunky 1.5.3 (Bukkit) |
|---|:---:|:---:|
| Adapts speed to your server's tick time | ✅ | ❌ fixed concurrency |
| One selection per admin | ✅ | ❌ shared by everyone |
| Trim with dry-run counts | ✅ | ❌ |
| Automatic backup before trimming | ✅ | ❌ asks you to back up manually |
| Trim never edits a loaded world | ✅ runs at startup | ❌ edits files at runtime, then requires a restart |
| Never edits your `server.properties` | ✅ only warns | ❌ changes `pause-when-empty-seconds` on Paper |
| Brigadier commands with tab completion | ✅ | ❌ |
| Boss bar progress | ✅ | ❌ |
| Messages in each player's own language | ✅ | ❌ one server-wide language |
| Setup diagnostics (`/pregen doctor`) | ✅ | ❌ |
| Tuning in `config.yml` (not JVM flags) | ✅ | ❌ |
| Telemetry | None | bStats |
| Folia, resume after restart, disk-space check, 9 shapes, CSV import | ✅ | ✅ |

<sub>Comparison based on the publicly observable behaviour of Chunky 1.5.3 for Bukkit/Paper. Chunky also runs on Fabric, Forge and other platforms; Pregen focuses on Paper and Folia.</sub>

---

**Requirements:** Paper, Folia or Purpur 1.21–26.3 · Java 21 (1.21.x) or Java 25 (26.x)
**Tip:** on 1.21.2+, set `pause-when-empty-seconds=0` in `server.properties` so an empty server keeps generating; `/pregen doctor` will remind you.
```

---

## 3. Descripción en español (opcional)

Puedes usarla en una página aparte o en un enlace "Español" dentro de la descripción.

```markdown
# Pregen

**Pre-genera tu mundo sin dañar tu servidor.**
Pregen genera el terreno por adelantado para que los jugadores no sufran lag al explorar, y lo hace *de forma segura*: vigila el tiempo por tick y frena antes de que bajen tus TPS, y el recorte de chunks nunca toca un mundo cargado.

Un solo jar para **Paper, Folia y Purpur, de la 1.21 a la 26.3**. Sin dependencias. Sin telemetría.

## ✨ Por qué Pregen
- 🧠 **Protege tus TPS solo:** ajusta su velocidad cada segundo según el tiempo por tick. También puede esperar a que no haya jugadores conectados.
- 🛡️ **Recorte sin riesgo:** primero una simulación con cifras exactas, después confirmas y se ejecuta en el próximo reinicio, antes de cargar los mundos y con **copia de seguridad automática**. Puedes conservar solo los chunks que los jugadores visitaron.
- 👥 **Pensado para equipos:** cada admin tiene **su propia selección**.
- ⌨️ **Comandos modernos Brigadier:** autocompletado, errores en rojo y permisos por subcomando. Radios como `500`, `32c`, `2r`, `5k`, `+100`.
- 📊 **BossBar** de progreso, ritmo, ETA y `/pregen doctor` para revisar tu servidor.
- 🌍 **Español e inglés** según el idioma del cliente de cada jugador; mensajes editables con MiniMessage.
- 💾 **No pierdes progreso:** guardado atómico y reanudación automática tras reiniciar, incluso tras un crash.

## 🚀 Inicio rápido
/pregen center spawn → /pregen radius 5k → /pregen shape circle → /pregen start → /pregen watch

**Requisitos:** Paper, Folia o Purpur 1.21–26.3 · Java 21 (1.21.x) o Java 25 (26.x)
```

---

## 4. Changelog de la versión 1.0.0

```markdown
## Pregen 1.0.0: first release

- Adaptive chunk pre-generation for Paper, Folia and Purpur 1.21–26.3 (one jar)
- Auto-throttle based on tick time; optional hold while players are online
- 9 shapes and 6 generation patterns (region, spiral, concentric, loop, CSV, existing world)
- Safe trimming: dry run, confirmation, execution at next startup with automatic backups, optional inhabited-time filter
- Per-admin selections, Brigadier commands, boss bar progress, ETA
- Automatic resume after restarts and crashes
- Spanish and English messages per player, MiniMessage formatting
- `/pregen doctor` setup diagnostics
- Developer API and Bukkit events
```

---

## 5. Imágenes para la galería (recomendado)

Las páginas con capturas reciben bastantes más descargas. Sugerencias, en este orden:

1. **Portada (featured):** la BossBar verde "Pregen · world · 64.2% · 45.0 c/s · ETA 00:02:10" sobre un paisaje.
2. **La simulación del trim** en el chat: el mensaje amarillo con chunks, regiones y espacio liberado.
3. **`/pregen status`** con dos mundos generándose a la vez.
4. **La ayuda clicable** (`/pregen`) mostrando los comandos.
5. **`/pregen doctor`** con sus comprobaciones.
6. **Antes/después** de un trim: vista de mapa (BlueMap, Dynmap o un mapa del mundo) con la zona recortada.

**Icono:** 512×512 px, sin texto pequeño. Por ejemplo, un bloque de hierba con una cuadrícula de chunks y una flecha de "carga", o un pico con un engranaje. Mantén el degradado verde→azul del prefijo del plugin (`#3ddc84 → #1e90ff`) para que la marca sea coherente.

---

## 6. Etiquetas y palabras clave

En el texto ya aparecen las palabras que se suelen buscar: *pre-generator, pregen, chunk, world generation, trim, worldborder, Folia, Paper, TPS, lag*. No hace falta repetirlas.

---

## 7. Antes de publicar: checklist

- [ ] **Licencia:** Modrinth la exige. MIT es la opción más permisiva; GPL-3.0 obliga a que los forks sigan siendo abiertos. Pregen es código propio, así que puedes elegir libremente.
- [ ] **Repositorio:** Pregen vive ahora dentro de `cursor/pregen`. Para la ficha conviene un repositorio propio (por ejemplo `pregen`) para que el enlace *Source* lleve directo al plugin.
- [ ] **Página de Issues** para que te reporten errores ahí y no en los comentarios.
- [ ] **Capturas** (sección 5).
- [ ] Revisa que el slug `pregen` esté libre.
- [ ] Prueba el `.jar` final en un servidor de pruebas antes de subirlo.

### Sobre la comparación con Chunky
La tabla solo incluye diferencias comprobadas al analizar Chunky 1.5.3 para Bukkit/Paper. Evita añadir afirmaciones sin medir, como "2× más rápido": Modrinth puede retirar contenido engañoso y la comunidad suele comprobar estas cosas. Si quieres presumir de velocidad, mide ambos plugins en la misma máquina, con la misma semilla y el mismo radio, y publica los números con la metodología.
