# Pregen

Pre-generador de chunks para **Paper y Folia, de 1.21.x a 26.x**. Genera de antemano el terreno de una zona para que los jugadores no causen lag al explorar, y permite **recortar** (borrar) chunks de forma segura.

Es una implementación propia, inspirada en el comportamiento de Chunky pero sin reutilizar su código. Está escrita desde cero con otra arquitectura.

| | |
|---|---|
| Servidores | Paper 1.21 – 26.3, Folia 1.21 – 26.3 (también forks de Paper como Purpur) |
| Java | 21 (1.21.x) o 25 (26.x); el mismo `.jar` sirve para ambos |
| Dependencias | Ninguna |
| Probado en vivo | Paper 1.21.1 + Java 21, Paper 26.3 + Java 25, Folia 26.3 + Java 25 |

---

## Índice

1. [Estructura del proyecto](#1-estructura-del-proyecto)
2. [Compilar](#2-compilar)
3. [Instalar](#3-instalar)
4. [Comandos](#4-comandos)
5. [Permisos](#5-permisos)
6. [Configuración](#6-configuración)
7. [Recorte (trim) seguro](#7-recorte-trim-seguro)
8. [API para otros plugins](#8-api-para-otros-plugins)
9. [Decisiones técnicas](#9-decisiones-técnicas)
10. [Pruebas](#10-pruebas)
11. [Casos límite y limitaciones conocidas](#11-casos-límite-y-limitaciones-conocidas)
12. [Riesgos según la versión de Minecraft](#12-riesgos-según-la-versión-de-minecraft)
13. [Mejoras futuras](#13-mejoras-futuras)

---

## 1. Estructura del proyecto

```
pregen/
├── build.gradle.kts            Gradle (Kotlin DSL): Java 21, Spotless, Checkstyle, comprobación contra Paper 26.3
├── gradle.properties           Versión del plugin y de las APIs de Paper (mínima y más reciente)
├── config/                     Reglas de formato (Spotless) y estilo (Checkstyle)
└── src/
    ├── main/java/dev/pregen/
    │   ├── PregenPlugin.java   Punto de entrada: crea los servicios y gestiona su ciclo de vida
    │   ├── api/                API pública (contrato estable) + eventos Bukkit
    │   ├── command/            Comandos Brigadier, confirmaciones, ayuda
    │   ├── config/             Lectura y validación de config.yml
    │   ├── diagnostics/        /pregen doctor, lectura de server.properties
    │   ├── generation/         Motor: tareas, preparación, throttle, acceso a chunks de Paper
    │   ├── geometry/           Formas (cuadrado, círculo, estrella…)
    │   ├── i18n/               Mensajes MiniMessage en es/en con marcadores tipados
    │   ├── listener/           Limpieza al salir jugadores, arranque del servidor
    │   ├── pattern/            Órdenes de generación (región, espiral, CSV…)
    │   ├── persistence/        Guardado atómico en JSON de las tareas
    │   ├── progress/           Consola, BossBar, avisos
    │   ├── selection/          Selección inmutable por remitente
    │   ├── trim/               Lector/editor de regiones Anvil, NBT, recortes programados
    │   └── util/               Utilidades (coordenadas, hilos, ficheros)
    ├── main/resources/         paper-plugin.yml, config.yml, lang/es.yml, lang/en.yml
    └── test/java/dev/pregen/   186 pruebas unitarias (JUnit 5)
```

## 2. Compilar

Necesitas **JDK 21**. Para la comprobación contra la API más reciente hace falta además JDK 25; Gradle la descarga solo si no está instalada.

```bash
cd pregen
./gradlew build          # compila, formatea/verifica, ejecuta pruebas y comprueba la compatibilidad con 26.3
```

El plugin queda en `pregen/build/libs/Pregen-1.0.0.jar`.

Otras tareas útiles:

| Tarea | Para qué |
|---|---|
| `./gradlew test` | Solo las pruebas unitarias |
| `./gradlew spotlessApply` | Formatea el código |
| `./gradlew compileAgainstLatestPaper` | Compila contra Paper 26.3 para detectar APIs eliminadas o cambiadas |

Para cambiar las versiones de Paper usadas, edita `paperApiVersion` y `latestPaperApiVersion` en `gradle.properties`.

## 3. Instalar

1. Copia `Pregen-1.0.0.jar` en la carpeta `plugins/` del servidor.
2. Arranca el servidor. Se crea `plugins/Pregen/` con `config.yml` y `lang/`.
3. Ejecuta `/pregen doctor` para revisar la configuración del servidor.
4. Ajusta `config.yml` si hace falta y aplica los cambios con `/pregen reload`.

> **Importante (1.21.2+):** si `server.properties` tiene `pause-when-empty-seconds` mayor que 0, el servidor se pausa sin jugadores y la generación se detiene. Pregen te avisa (en `/pregen doctor` y al iniciar una tarea), pero **nunca modifica `server.properties`**. Ponlo a `0` (o `-1`) y reinicia.

Primer uso típico:

```
/pregen world world
/pregen center spawn
/pregen radius 5k
/pregen shape circle
/pregen start
/pregen watch          (barra de progreso)
```

O todo en una línea: `/pregen start world circle 5k 0 0`

## 4. Comandos

Comando principal: `/pregen`, alias `/pg`. Cada remitente (cada admin y la consola) tiene **su propia selección**.

| Comando | Descripción |
|---|---|
| `/pregen` · `/pregen help` | Ayuda, filtrada por tus permisos |
| `/pregen start [mundo] [forma] [radio] [x z]` | Inicia una tarea con tu selección; los argumentos opcionales la modifican antes |
| `/pregen pause [mundo]` | Pausa y guarda (sin mundo: todas) |
| `/pregen resume [mundo]` | Reanuda tareas pausadas o guardadas en disco (sin mundo: todas) |
| `/pregen cancel [mundo]` | Cancela y borra el progreso; pide `/pregen confirm` |
| `/pregen confirm` | Confirma la última acción delicada (caduca a los 30 s, configurable) |
| `/pregen status` | Progreso, ritmo, ETA y estado (en curso, pausada, en espera…) |
| `/pregen watch` | Muestra u oculta la BossBar de progreso (solo jugadores) |
| `/pregen selection` | Muestra tu selección |
| `/pregen world <mundo>` | Mundo por su nombre (`world_nether`), con autocompletado |
| `/pregen center <x> <z>` · `here` · `spawn` | Centro por coordenadas, tu posición o el spawn |
| `/pregen radius <radio> [radioZ]` | `500`, `32c` (chunks), `2r` (regiones), `1.5k` (miles), `+100`, `-2c` |
| `/pregen corners <x1> <z1> <x2> <z2>` | Rectángulo por esquinas |
| `/pregen worldborder` | Copia centro y radio del borde del mundo |
| `/pregen shape <forma>` | `square`, `rectangle`, `circle`, `ellipse`/`oval`, `diamond`, `triangle`, `pentagon`, `hexagon`, `star` |
| `/pregen pattern <patrón> [archivo]` | `region`, `concentric`, `spiral`, `loop`, `csv <archivo>`, `world` |
| `/pregen trim <outside\|inside> [inhabited <ticks>]` | Simula y, tras confirmar, programa un recorte para el próximo reinicio |
| `/pregen trim pending` | Lista los recortes programados |
| `/pregen trim unschedule <mundo>` | Anula un recorte programado |
| `/pregen silent` | Activa o desactiva el progreso periódico en consola |
| `/pregen interval <segundos>` | Frecuencia del progreso en consola |
| `/pregen doctor` | Diagnóstico del servidor |
| `/pregen reload` | Recarga `config.yml` e idiomas (no con tareas en curso) |

**Patrones:**

| Patrón | Orden |
|---|---|
| `region` (por defecto) | Completa cada archivo de región de 32×32 chunks antes de pasar al siguiente, en anillos desde el centro. Es el más amable con el disco. |
| `concentric` | Anillos cuadrados desde el centro |
| `spiral` | Espiral continua: cada chunk es vecino del anterior |
| `loop` | Fila por fila |
| `csv <archivo>` | Lee `plugins/Pregen/csv/<archivo>.csv`, con una línea `x,z` (coordenadas de chunk) por chunk |
| `world` | Carga todos los chunks que ya existen en disco, por ejemplo para actualizar un mundo antiguo |

## 5. Permisos

Todos son `op` por defecto, salvo `pregen.operator`.

| Permiso | Concede |
|---|---|
| `pregen.admin` | Todo, incluido trim, más `pregen.notify` |
| `pregen.operator` | Todo **excepto** trim, silent, doctor y reload. Pensado para moderadores. |
| `pregen.command.help` | Ver `/pregen` y la ayuda |
| `pregen.command.start` | `start` |
| `pregen.command.pause` · `resume` · `cancel` · `confirm` | Control de tareas |
| `pregen.command.status` · `watch` | Ver el progreso |
| `pregen.command.selection` | `selection`, `world`, `center`, `radius`, `corners`, `worldborder`, `shape`, `pattern` |
| `pregen.command.trim` | Recortes (destructivo) |
| `pregen.command.silent` | `silent` e `interval` |
| `pregen.command.doctor` · `reload` | Administración |
| `pregen.notify` | Recibir avisos de inicio, fin y errores de tareas |

Los subcomandos sin permiso no aparecen ni se autocompletan en el cliente.

## 6. Configuración

`plugins/Pregen/config.yml`. Los valores ausentes usan el valor por defecto. Un valor inválido se sustituye por el valor seguro y se avisa **una vez** en consola, indicando la clave y el rango permitido, sin parar el plugin.

| Clave | Por defecto | Qué hace |
|---|---|---|
| `language` | `es` | Idioma de la consola y por defecto (`es`/`en`) |
| `use-client-locale` | `true` | Cada jugador ve los mensajes en el idioma de su cliente si es es/en |
| `confirm-timeout-seconds` | `30` | Validez de `/pregen confirm` (5–600) |
| `debug` | `false` | Registros extra de diagnóstico |
| `generation.max-concurrent-chunks` | `32` | Máximo de chunks pedidos a la vez, **compartido** entre todas las tareas (1–1024) |
| `generation.adaptive-throttle.enabled` | `true` | Ajusta la concurrencia según el tiempo por tick |
| `generation.adaptive-throttle.target-mspt` | `45.0` | Objetivo en ms por tick (50 = 20 TPS) |
| `generation.adaptive-throttle.min-concurrent-chunks` | `4` | Mínimo de concurrencia mientras el throttle frena |
| `generation.skip-existing-chunks` | `true` | Salta los chunks ya generados sin cargarlos |
| `generation.keep-ticking-seconds` | `0` | Mantiene cada chunk generado cargado N segundos (0–600) |
| `generation.resume-on-restart` | `true` | Reanuda automáticamente las tareas guardadas al arrancar |
| `generation.autosave-seconds` | `30` | Cada cuánto se guarda el progreso en disco |
| `generation.pause-when-players-online` | `false` | Retiene la generación mientras haya jugadores conectados |
| `generation.radius-limit` | `0` | Radio máximo permitido (0 = sin límite), útil para hostings |
| `generation.check-disk-space` | `true` | Avisa si el espacio estimado supera el libre (se puede forzar con confirm) |
| `generation.worker-threads` | `2` | Hilos para comprobar si un chunk existe (requiere reiniciar para cambiarlo) |
| `progress.console-interval-seconds` | `10` | Frecuencia del progreso en consola |
| `progress.silent` | `false` | Oculta el progreso periódico (el inicio y el fin se siguen mostrando) |
| `progress.bossbar.color` / `overlay` | `GREEN` / `PROGRESS` | Aspecto de la BossBar |
| `trim.backup` | `true` | Copia cada región afectada antes de modificarla |
| `trim.backup-directory` | `backups/trim` | Carpeta de copias, relativa a `plugins/Pregen` |

**Mensajes:** están en `lang/es.yml` y `lang/en.yml`, en formato [MiniMessage](https://docs.advntr.dev/minimessage/format.html) (colores, degradados, hover, clic). Los marcadores como `<world>` o `<percent>` se rellenan solos. Si a tu archivo le faltan claves tras actualizar, se usa el texto incluido en el jar y se avisa en consola.

**Archivos de datos:**

| Archivo | Contenido |
|---|---|
| `tasks/<mundo>.json` | Progreso de cada tarea (escritura atómica) |
| `pending-trims.json` | Recortes programados |
| `csv/` | Listas de chunks para el patrón `csv` |
| `backups/trim/<id>/` | Copias previas a cada recorte |

## 7. Recorte (trim) seguro

El recorte borra chunks de los archivos `.mca` (bloques, entidades y POI). Modificar esos archivos con el mundo cargado puede corromperlo, así que Pregen lo hace en tres pasos:

1. **`/pregen trim outside`** (o `inside`) ejecuta una **simulación**: lee solo las cabeceras de las regiones y muestra cuántos chunks y regiones se borrarían y el espacio que se liberaría. No toca nada.
2. **`/pregen confirm`** **programa** el recorte en `pending-trims.json`. Mientras esté pendiente no se puede generar en ese mundo.
3. En el **siguiente arranque**, Pregen ejecuta el recorte **antes de que se carguen los mundos**. Para eso se carga en `STARTUP`. Primero copia las regiones afectadas en `backups/trim/<id>/` y después borra los chunks.

Detalles:

- **Filtro `inhabited <ticks>`:** solo borra chunks en los que los jugadores pasaron como máximo ese tiempo (20 ticks = 1 s). Lee `InhabitedTime` del NBT de cada chunk. Si un chunk no se puede leer (compresión LZ4 o datos dañados), **se conserva**.
- **Idempotente:** si el servidor se cae a mitad, el recorte se repite en el siguiente arranque sin daño, y las copias originales nunca se sobrescriben.
- **Cómo borra:** pone a cero la entrada del chunk en la cabecera, igual que el juego trata un chunk no generado. Una región que se queda vacía se borra entera.

## 8. API para otros plugins

```java
PregenApi api = Bukkit.getServicesManager().load(PregenApi.class);

api.start(new GenerationRequest("world", 0, 0, 3000, 3000, "circle", "region"))
   .thenAccept(outcome -> getLogger().info("Pregen: " + outcome));   // STARTED, ALREADY_RUNNING, ...

api.progress("world").ifPresent(p -> getLogger().info(p.percent() + "%"));
api.pause("world");
api.resume("world");   // CompletableFuture<Boolean>
api.cancel("world");   // CompletableFuture<Boolean>
```

Eventos Bukkit, todos **asíncronos**:

| Evento | Cuándo |
|---|---|
| `PregenTaskStartEvent` | Cancelable; justo antes de iniciar una tarea nueva |
| `PregenTaskProgressEvent` | En cada intervalo de progreso |
| `PregenTaskFinishEvent` | Al completar o cancelar una tarea |
| `PregenTrimScheduledEvent` | Cancelable; al confirmar un recorte |

Todo el paquete `dev.pregen.api` está documentado con Javadoc: hilos, nulabilidad y errores.

## 9. Decisiones técnicas

- **Un único jar de 1.21 a 26.x.** Se compila contra la API de Paper **1.21.1** con bytecode **Java 21**, la más antigua soportada. Una tarea de Gradle compila además el mismo código contra **Paper 26.3** (sin ningún aviso de API obsoleta), de modo que una API eliminada en el futuro se detecta en CI y no en producción.
- **Paper y Folia con el mismo código.** Se usan los schedulers unificados de Paper (global, de región y asíncrono) y `getChunkAtAsync`, que existen en ambos. No hay reflexión ni ramas `if (folia)`.
  - En Folia, lo que pertenece a la región global (borde del mundo, spawn) se lee mediante `GlobalRegionScheduler`.
  - Los tickets de chunk se añaden y quitan en el hilo de la región dueña del chunk.
- **Motor no bloqueante.** Cada tarea limita los chunks en vuelo y, al completarse cada uno, pide el siguiente. No hay ningún hilo parado en un semáforo (el original dedicaba un hilo por tarea).
  - El estado de cada tarea vive confinado en un `SerialExecutor`, sin `synchronized` en el camino caliente.
- **Throttle adaptativo AIMD.** Baja la concurrencia un 25 % cuando el tick supera el objetivo y la sube poco a poco cuando hay margen.
  - En Folia no existe un tiempo por tick global, así que se usa la concurrencia máxima y `/pregen doctor` lo indica.
- **Reanudación exacta.** Los patrones son deterministas. Se guarda el índice del chunk más antiguo aún en vuelo, de modo que al reanudar no se pierde ningún chunk; como mucho se repiten unos pocos, que se saltan rápido porque ya existen.
- **Persistencia:**
  - JSON con escritura atómica (archivo temporal y renombrado) y guardado periódico.
  - Las escrituras se serializan en orden, para que un autoguardado nunca resucite una tarea cancelada.
  - Al apagar se guarda de forma síncrona con un tiempo límite.
- **Hilos separados por tipo de trabajo:** coordinadores, comprobaciones (que pueden bloquear) y E/S. Así uno no puede dejar sin hilos a otro. El hilo principal nunca hace E/S.
- **Selección por remitente**, inmutable y sin bloqueos. Se libera al desconectarse el jugador.
- **Seguridad:**
  - Toda entrada se valida, con mensajes localizados.
  - Los nombres de archivo CSV no pueden salir de su carpeta.
  - Los textos de usuario se insertan sin interpretar MiniMessage.
  - Las acciones destructivas piden confirmación con caducidad.

**Mejoras respecto al original:**

| Original | Pregen |
|---|---|
| Una selección global compartida por todos los admins | Una selección por remitente |
| Reescribe `server.properties` sin preguntar | Solo lee el archivo y avisa |
| Trim sobre archivos de un mundo cargado | Trim en el arranque, con simulación y copias de seguridad |
| Ajustes ocultos en propiedades `-D` de la JVM | Todo en `config.yml` y validado |
| Concurrencia fija (50 por tarea) | Presupuesto compartido y adaptativo según el MSPT |
| Comandos analizados a mano | Brigadier, con autocompletado y permisos por subcomando |
| Métricas bStats activas | Sin telemetría |
| `chunky.chunky` no incluía trim | `pregen.admin` lo incluye todo; `pregen.operator` excluye lo destructivo |

## 10. Pruebas

### Pruebas automáticas

`./gradlew test` ejecuta 186 pruebas JUnit 5 que no necesitan servidor:

- **Formas:** contención de puntos, área comparada con Monte Carlo y estrella cóncava.
- **Patrones:** que recorran cada chunk exactamente una vez y que reanudar sea determinista.
- **Motor de generación** (con un acceso a chunks simulado):
  - nunca supera el límite de concurrencia;
  - el punto de guardado nunca salta un chunk sin terminar;
  - pausa tras 50 errores seguidos;
  - descuenta el tiempo en pausa.
- **Throttle, estimador de ritmo y parser de radios.**
- **Trim:** regiones Anvil sintéticas, filtro de `InhabitedTime`, copias de seguridad y ejecución doble sin daño.
- **Configuración, idiomas y persistencia:** paridad de claves y marcadores es/en, valores inválidos, JSON corrupto y nombres de mundo peligrosos.

### Prueba en servidores reales

Hecha durante el desarrollo:

| Servidor | Probado |
|---|---|
| Paper 26.3 + Java 25 | Ayuda, doctor, generar 3.228 chunks, pausar/reanudar, reiniciar a mitad y reanudar sola, trim programado (16 → 4 regiones con copia) y regeneración posterior |
| Paper 1.21.1 + Java 21 | Nether con la estructura antigua `DIM-1`, forma de estrella, trim `inside` con filtro `inhabited` leyendo NBT real |
| Folia 26.3 + Java 25 | `center spawn` y `worldborder` mediante el scheduler global, generación completa, detección de throttle no disponible, reanudación tras reinicio |

Ninguna excepción en los registros de ninguno de los tres.

### Pruebas manuales recomendadas en tu servidor

1. `/pregen doctor` y revisar los avisos.
2. `/pregen radius 1k` y `/pregen start`; vigilar el TPS con `/mspt` mientras corre. El estado debe pasar a "en espera" si el servidor se satura.
3. Entrar como jugador, usar `/pregen watch` y comprobar la BossBar y el idioma del cliente.
4. `/pregen pause`, luego `/pregen resume`; repetir con un `stop` del servidor a mitad.
5. Dos admins con selecciones distintas a la vez.
6. `/pregen radius abc`, `/pregen shape blob`, `/pregen pattern csv ../x`: deben dar errores claros.
7. Trim en un **mundo de prueba**: simular, confirmar, reiniciar, comprobar las copias y que el mundo carga.
8. `/pregen trim unschedule <mundo>` antes de reiniciar: no debe borrarse nada.

## 11. Casos límite y limitaciones conocidas

- **Selecciones enormes** (radio de más de unos 100.000 bloques): el total se estima por el área en vez de contarse (se muestra con `~`). Al reanudar, el patrón se recorre hasta el punto guardado, lo que puede tardar unos segundos en un hilo aparte (nunca en el principal).
- **Patrones `csv` y `world`:** si el CSV cambia o aparecen chunks nuevos entre reinicios, la posición de reanudación puede desplazarse. No cambies el CSV de una tarea en curso.
- **Las selecciones viven en memoria**; tras reiniciar vuelven a los valores por defecto. Las *tareas* sí se guardan.
- **El trim requiere reiniciar.** Es intencionado: es la única forma segura, también en Folia, que no permite descargar mundos.
- **`isChunkGenerated`** puede esperar a un hilo del servidor; por eso tiene su propio grupo de hilos.
- **`worker-threads`** solo cambia al reiniciar; el resto de la configuración admite `/pregen reload`.
- **Descargar un mundo** con una tarea activa (por ejemplo con Multiverse) pausa y guarda la tarea, que se puede reanudar después.
- **Varias tareas a la vez** comparten `max-concurrent-chunks`: cuantas más, más lenta va cada una, pero el servidor no se satura.

## 12. Riesgos según la versión de Minecraft

| Área | Riesgo |
|---|---|
| API de comandos Brigadier de Paper | Marcada como *experimental* en 1.21.x. Pregen solo usa lo básico (`literal`, `argument`, `register`), que no ha cambiado hasta 26.3. |
| Estructura de carpetas de mundos | Cambió en 26.x (`world/dimensions/minecraft/<dim>/region`). `StorageDirs` prueba ambas estructuras; probado en 1.21.1 y 26.3. |
| Formato de región / compresión | LZ4 (1.20.5+) y compresión personalizada no se descomprimen: con el filtro `inhabited` esos chunks se conservan. Sin el filtro, el trim funciona igual. |
| `pause-when-empty-seconds` | Solo existe desde 1.21.2. En 1.21.1 no aplica; en 26.3 el valor por defecto observado es `-1`. |
| Folia | Sin tiempo por tick global, así que no hay throttle adaptativo. Futuras versiones podrían añadir API por región. |
| Java | 1.21.x usa Java 21 y 26.x exige Java 25. El jar (bytecode 21) funciona en ambos. |

## 13. Mejoras futuras

- Throttle por región en Folia si Paper expone el TPS por región.
- Caché opcional de "chunk ya generado" por región para repetir tareas al instante.
- Integración con mapas (BlueMap, squaremap, Dynmap) para dibujar la selección.
- Placeholders de PlaceholderAPI (`%pregen_progress_<mundo>%`).
- Compactar las regiones recortadas para recuperar el espacio de los sectores huérfanos.
- Descompresión LZ4 propia para que el filtro `inhabited` cubra todos los chunks.
- Guardar las selecciones entre reinicios.
- Cola de tareas programadas, por ejemplo "generar de noche cuando no haya jugadores".
