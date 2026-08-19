# ROADMAP.md — Ruta de trabajo de BookReports

Ruta de implementación derivada de [`SPECS.md`](./SPECS.md). Cada fase es entregable e independientemente verificable: al final de cada una el plugin **compila, arranca en un Paper 1.21.x y hace algo demostrable**. No se avanza a la siguiente fase sin cerrar el criterio de aceptación de la actual.

**Leyenda de esfuerzo:** `S` = ~medio día · `M` = ~1-2 días · `L` = ~3-5 días

---

## Fase 0 — Andamiaje del proyecto

> Objetivo: un jar que carga en el servidor y no hace nada más. Todo lo demás se construye encima.

- [x] `S` Inicializar proyecto Gradle (Kotlin DSL) con Java 21 toolchain y `paper-api:1.21.x` como `compileOnly`.
- [x] `S` Configurar plugin Shadow (`com.gradleup.shadow`) con relocación de `com.zaxxer`, `com.github.benmanes.caffeine` y `org.sqlite` bajo `dev.bookreports.libs.*`.
- [x] `S` Crear `paper-plugin.yml` (formato moderno de Paper, no `plugin.yml` legacy) con `api-version: '1.21'`, bootstrapper y declaración de `softdepend` (PlaceholderAPI, LiteBans, AdvancedBan).
- [x] `S` Clase `BookReportsPlugin` con `onEnable`/`onDisable` vacíos + log de arranque con versión.
- [x] `S` `.gitignore`, `.editorconfig`, licencia y estructura de paquetes de §14 (paquetes vacíos con `package-info.java`).
- [x] `M` CI en GitHub Actions: build + tests en cada push/PR, artefacto del jar subido en cada run.
- [x] `S` `SchedulerAdapter` con detección de Folia en runtime y dos implementaciones (`BukkitSchedulerImpl`, `FoliaSchedulerImpl`) — se hace **desde el día 1** porque retrofitear scheduling después toca todos los callbacks.

**Criterio de aceptación:** `./gradlew build` produce un jar; el servidor lo carga y lo lista en `/plugins`.

---

## Fase 1 — Configuración e i18n

> Objetivo: toda cadena de texto y toda constante de comportamiento vive en YAML antes de escribir lógica que las consuma.

- [x] `M` `ConfigManager`: carga/valida `config.yml`, con valores por defecto y **validación estricta al arrancar** (una categoría con `priority` inválida debe fallar ruidosamente en el log, no silenciosamente en runtime).
- [x] `M` Modelo tipado de categorías: `ReportCategory` (id, display, priority, sub-reasons) parseado una vez a memoria, no leído del YAML en caliente.
- [x] `M` `LocaleManager` con soporte MiniMessage y resolución de placeholders (`{player}`, `{ticket_id}`, `{cooldown}`).
- [x] `S` Ficheros `locale/es_ES.yml` y `locale/en_US.yml` completos (todas las claves, aunque las features aún no existan).
- [x] `S` Comando `/reportsreload` con permiso `bookreports.admin`, recarga config + locale sin tocar sesiones activas.
- [x] `S` Tests unitarios del parser de config: categorías bien/mal formadas, sub-reasons vacías, prioridad desconocida.

**Criterio de aceptación:** cambiar el `display` de una categoría en `config.yml` + `/reportsreload` se refleja sin reiniciar el servidor.

---

## Fase 2 — Persistencia

> Objetivo: poder guardar y leer reportes desde consola, antes de que exista UI alguna.

- [x] `M` `StorageManager` con HikariCP, conexión async en `onEnable` y apagado limpio del pool en `onDisable`.
- [x] `M` Dialectos separados: scripts en `resources/db/sqlite/` y `resources/db/mysql/` (difieren en `AUTOINCREMENT` vs `AUTO_INCREMENT`).
- [x] `M` Sistema de migraciones propio: tabla `br_meta` con `schema_version`, migraciones incrementales idempotentes.
- [x] `M` `ReportDao`: `insert`, `findByUuid`, `findById`, `findByTarget`, `findByStatus` (paginado), `countByReporterSince`, `updateStatus`, `claim`.
- [x] `S` `PenaltyDao`: `insert`, `countByPlayerSince`.
- [x] `S` Modelos inmutables `Report` y `ReportPenalty` (records Java 21).
- [x] `M` Tests de integración contra `jdbc:sqlite::memory:` cubriendo cada método del DAO + una migración de v1 a v2.
- [ ] `S` Verificación manual contra MySQL real (docker) — el SQL "compatible" siempre esconde una sorpresa de tipos/timestamp.

**Criterio de aceptación:** un comando temporal de debug inserta un reporte y lo lee de vuelta, en SQLite y en MySQL, sin bloquear el hilo principal (verificado con timings o Spark).

---

## Fase 3 — Núcleo de servicio y anti-abuso

> Objetivo: toda la lógica de negocio funcionando y testeada, invocable por API, todavía sin interfaz de usuario.

- [x] `M` `ReportService.submitReport(...)`: valida (auto-reporte, target existe, duplicado pendiente, cooldown, límite diario) → dispara `ReportCreateEvent` cancelable → persiste → dispara `ReportCreatedEvent`.
- [x] `M` `CooldownService` con Caffeine, respetando el multiplicador por reportes falsos.
- [x] `S` Contador de límite diario con caché de 60s por jugador (no golpear la DB en cada intento).
- [x] `M` `PriorityCalculator`: prioridad base por categoría + escalado por consenso (N reporteros distintos / ventana temporal).
- [x] `M` Lógica de resolución: `claim` con TTL de 15 min y auto-liberación programada, `resolve`, `markFalse` (+ inserción de penalización).
- [x] `S` Eventos públicos de §9 (`ReportCreateEvent`, `ReportCreatedEvent`, `ReportClaimedEvent`, `ReportResolvedEvent`, `ReportFalseMarkedEvent`).
- [x] `S` `BookReportsAPI` registrada en el `ServicesManager` de Bukkit.
- [x] `L` Batería de tests unitarios: cada regla anti-abuso con su caso de borde (cooldown justo expirado, límite diario en el cambio de día UTC, escalado con exactamente N-1 y N reporteros).

**Criterio de aceptación:** todo §8 (anti-abuso) verificado por tests automáticos; un plugin externo de prueba puede cancelar un reporte vía `ReportCreateEvent`.

---

## Fase 4 — Flujo del libro (la pieza central de UX)

> Objetivo: el `/report` de Hypixel funcionando. Es la fase de mayor riesgo: la máquina de estados y la validación de clics concentran casi todos los bugs de seguridad del plugin.

- [ ] `M` `ReportSession` (record) + `SessionManager` con caché Caffeine, expiración por inactividad y **una sola sesión activa por jugador**.
- [ ] `M` Máquina de estados explícita: tabla de transiciones válidas `ReportState × acción → ReportState`, con rechazo por defecto (whitelist, no blacklist).
- [ ] `M` `SelectOptionCommand` (`/breport:select <sessionId> <optionId>`): comando oculto que valida propietario de sesión + sesión viva + transición legal antes de actuar. **Los tres checks son obligatorios** (§13).
- [ ] `M` `BookBuilder` + `ComponentUtil`: construcción de páginas con Adventure, `ClickEvent.runCommand`, `HoverEvent.showText` y estilo de corchetes tipo Hypixel.
- [ ] `M` Páginas: `TargetConfirmPage`, `CategoryPage`, `SubReasonPage` (salta si la categoría no tiene sub-reasons), `SummaryPage`, `ResultPage`.
- [ ] `M` Página de selección de objetivo cuando `/report` se usa sin argumento: lista paginada de jugadores online.
- [ ] `M` `AnvilInputGUI` propio para la evidencia de texto libre + sanitización server-side (strip de formato, longitud máxima).
- [ ] `S` Estado degradado en cooldown: el enlace de envío se renderiza gris y **sin** `ClickEvent`, mostrando el tiempo restante.
- [ ] `S` Rate limiting de `/report` (1/seg) contra spam de apertura de libros.
- [ ] `M` Item físico opcional `report-tool` + listener de clic derecho sobre jugador (tras `enable-report-tool`).
- [ ] `M` Tests con MockBukkit del flujo completo, incluyendo los caminos de ataque: sessionId ajeno, sessionId expirado, salto de estado por comando pegado a mano.

**Criterio de aceptación:** dos cuentas reales completan el flujo de principio a fin sin escribir nada salvo `/report`; un tercer jugador copiando el comando `/breport:select` del primero recibe rechazo.

---

## Fase 5 — Panel de staff

> Objetivo: el otro lado del sistema — que los reportes se revisen, no solo se acumulen.

- [ ] `M` Framework de GUI paginado reutilizable (`PaginatedView` base): navegación, slots de borde, manejo de clics, cierre seguro.
- [ ] `M` `ReportQueueView`: 54 slots, cabezas de jugador, lore con metadatos, filtros por estado/categoría, orden por prioridad y antigüedad.
- [ ] `S` Indicador visual de prioridad (glow para HIGH) y badge de agrupación ("3 reportes en 10 min").
- [ ] `M` `ReportDetailView`: reclamar, teleport, historial, ver evidencia completa, resolver (sancionar/rechazar), marcar falso.
- [ ] `M` Sub-menú de resolución con motivos rápidos preconfigurados.
- [ ] `M` Sub-comandos de consola `/reportadmin list|view|claim|resolve|history` (imprescindibles para scripting y para operar sin cliente gráfico).
- [ ] `M` Notificación en vivo al staff online: mensaje clicable `[Ver] [Teleport]`, sonido y action bar; toggle persistente por jugador.
- [ ] `S` Nodos de permisos completos de §10 declarados en `paper-plugin.yml` con sus defaults.
- [ ] `S` Protección contra doble-resolución: si dos staff resuelven el mismo reporte en paralelo, el segundo recibe un aviso claro en vez de sobrescribir.

**Criterio de aceptación:** un reporte creado en Fase 4 se ve, se reclama y se resuelve íntegramente desde el GUI, y el reportante recibe feedback del desenlace.

---

## Fase 6 — Integraciones opcionales

> Objetivo: conectar con el ecosistema existente. Todo aquí es *soft-depend*: si el plugin externo no está, BookReports funciona igual con la feature deshabilitada.

- [ ] `M` `PunishmentBridge` (interfaz) + implementaciones detectadas en `onEnable` para LiteBans y AdvancedBan; fallback "acción externa aplicada" cuando no hay ninguno.
- [ ] `S` `DiscordNotifier`: webhook async con `HttpClient` de Java, filtrado por prioridad mínima, con timeout y sin reintentos infinitos.
- [ ] `S` `PlaceholderExpansionImpl` con los placeholders de §11.
- [ ] `M` Sincronización en red con proxy: canal `bookreports:sync`, columna `server` poblada, panel de staff capaz de mostrar y reclamar reportes de otros backends sobre MySQL compartido.
- [ ] `S` Verificación de que arrancar sin **ninguna** integración presente no produce warnings ni stacktraces.

**Criterio de aceptación:** el plugin arranca limpio en un servidor pelado y en uno con las cuatro integraciones activas, sin cambios de configuración más allá de activar cada flag.

---

## Fase 7 — Endurecimiento, rendimiento y release

> Objetivo: pasar de "funciona en mi servidor de pruebas" a "funciona en producción con 200 jugadores".

- [ ] `M` Pasada de seguridad completa sobre §13: revisar cada punto de entrada de input del usuario (comando, anvil, click de GUI) y su sanitización.
- [ ] `M` Auditoría de hilos: confirmar con Spark/timings que ninguna consulta SQL toca el hilo principal y que todo callback a la API de Bukkit vuelve al hilo correcto.
- [ ] `S` Acotar todas las cachés Caffeine (`maximumSize`) y verificar que no hay fugas de memoria tras miles de sesiones.
- [ ] `M` QA manual del checklist completo de §15 en Paper y, si se soporta, en Folia.
- [ ] `M` Prueba de carga: 500 reportes sembrados, medir apertura del panel de staff y consultas de historial.
- [ ] `S` Documentación de usuario: README, wiki de configuración, ejemplos de `config.yml` comentados.
- [ ] `S` Versionado semántico, `CHANGELOG.md` y release automatizada en CI al taggear.
- [ ] `S` Publicación en Modrinth/Hangar con descripción, capturas del flujo del libro y matriz de compatibilidad.

**Criterio de aceptación:** v1.0.0 taggeada, jar publicado, sin issues abiertos de severidad alta.

---

## Post-v1 (backlog)

Estos puntos **no** bloquean la 1.0 y se priorizan según feedback real de servidores en producción:

- Adjuntar enlaces a capturas/clips mediante servicio externo de subida (solo enlace, sin hosting propio).
- Panel web de staff sobre la misma base de datos, vía API REST opcional.
- Auto-moderación básica: sugerencia automática de categoría por detección de palabras prohibidas.
- Detección automática del idioma del cliente para i18n (explícitamente fuera de v1 en §2).
- Sistema de apelaciones propio (hoy delegado a plugins externos).

---

## Riesgos y decisiones a vigilar

- **Máquina de estados del libro (Fase 4)** — concentra el riesgo de seguridad del proyecto. Un `ClickEvent.runCommand` es texto que cualquier jugador puede leer y reescribir; los tres checks de validación no son opcionales.
- **Compatibilidad SQLite ↔ MySQL (Fase 2)** — el "SQL portable" siempre se rompe en tipos de timestamp y autoincremento. Probar contra MySQL real *antes* de construir encima, no después.
- **Folia (Fases 0 y 7)** — barato si el `SchedulerAdapter` existe desde el inicio; caro si se intenta añadir al final.
- **Límite de páginas del libro** — un `WRITTEN_BOOK` tiene tope de 100 páginas y ~256 caracteres útiles por página; la lista paginada de jugadores online debe respetarlo en servidores llenos.
- **Alcance de las integraciones (Fase 6)** — cada plugin de castigos soportado es mantenimiento recurrente. Empezar con dos y ampliar solo bajo demanda.
