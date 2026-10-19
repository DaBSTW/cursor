# Soledom

**Modelos custom para Paper con display entities, animación por keyframes en el servidor y generador de
resource pack.** Sin NMS, sin dependencias externas, compatible con Folia.

| | |
|---|---|
| Servidor | Paper 1.21.x → 26.3 (y futuras, por detección de capacidades). Folia soportado |
| Java | Compilado para Java 21. Paper 26.x necesita Java 25 para el servidor; el plugin funciona igual |
| Licencia | MIT |
| Paquete | `dev.example.soledom` · comando `/soledom` (alias `/sd`) |

Probado arrancando servidores reales: **Paper 1.21.4** (Java 21) y **Paper 26.3** (Java 25): spawn, animaciones,
pausa/stop, export del pack, reload en caliente, persistencia tras reinicio y limpieza al matar la raíz.

---

## Qué hace

- **Modelos en JSON o YAML** en `plugins/Soledom/models/`, validados con errores claros (`parts[1].scale: expected
  an array of 3 numbers`), recarga en caliente con `/soledom reload`.
- **Partes visuales** con `ITEM_DISPLAY` (custom model data / `item_model`), `BLOCK_DISPLAY` y `TEXT_DISPLAY`
  (MiniMessage).
- **Raíz invisible**: `Interaction` (ancla + hitbox clicable) o `Marker` (sin hitbox, la más barata).
- **Animación server-side**: canales `translation`, `rotation`, `scale`; interpolación lineal o `step`; loop;
  prioridades; pausa/reanudar; culling por distancia; eventos de inicio/fin/loop.
- **Resource pack**: `/soledom pack export` genera `plugins/Soledom/export/SoledomPack.zip` con `pack.mcmeta`
  válido de 1.21 a 26.3, overrides legacy (< 1.21.4) **y** item definitions modernas (1.21.4+), modelos y
  texturas placeholder propias, SHA-1 y escritura atómica.
- **Persistencia** con `PersistentDataContainer`; las partes se reconstruyen desde la definición actual al cargar.
- **API pública** (`Soledom.api()` o `ServicesManager`) y eventos Bukkit.

## Instalación

1. Compila (ver abajo) o descarga `Soledom-<versión>.jar` del artefacto de CI.
2. Cópialo a `plugins/` y arranca el servidor. En el primer arranque se crean:

```
plugins/Soledom/
├── config.yml
├── messages.yml
├── models/example_golem.json
├── animations/example_idle.json
├── animations/example_rotate.json
├── pack/assets/soledom/models/item/     ← tus modelos (Blockbench → Java Block/Item)
├── pack/assets/soledom/textures/item/   ← tus texturas
└── export/                              ← SoledomPack.zip + SoledomPack.zip.sha1
```

3. `/soledom spawn soledom:example_golem` y `/soledom animate looking rotate`.

## Compilar

```bash
cd soledom
./gradlew build          # compila, ejecuta tests y genera build/libs/Soledom-0.1.0.jar
./gradlew test           # solo tests
```

## Probar en local

**Con run-paper** (descarga Paper automáticamente y arranca un servidor de prueba en `soledom/run/`):

```bash
./gradlew runServer                               # Paper 1.21.4 (Java 21)
./gradlew runServer -PtestMinecraftVersion=26.3 \
  -Porg.gradle.java.installations.paths=/ruta/a/jdk-25   # Paper 26.3 necesita un JDK 25 local
```

Acepta la EULA en `run/eula.txt` la primera vez y entra con un cliente de la misma versión a `localhost`.
`run/` está en `.gitignore`.

**Servidor propio**: copia `build/libs/Soledom-0.1.0.jar` a `plugins/` y reinicia (no uses `/reload` de Bukkit;
usa `/soledom reload`).

## Comandos y permisos

| Comando | Permiso | Descripción |
|---|---|---|
| `/soledom help` | `soledom.use` | Ayuda (solo muestra lo que puedes usar) |
| `/soledom list` | `soledom.use` | Modelos registrados, partes y animaciones |
| `/soledom spawn <modelo>` | `soledom.spawn` | Spawnea en tu posición. Consola: `spawn <modelo> <mundo> <x> <y> <z> [yaw]` |
| `/soledom remove [objetivo]` | `soledom.spawn` | Borra el modelo que miras (o el más cercano) |
| `/soledom animate <objetivo> <animación> [velocidad]` | `soledom.animate` | Reproduce una animación |
| `/soledom stopanimation [objetivo] [animación]` | `soledom.animate` | Para una o todas (vuelve a la pose de reposo) |
| `/soledom pause [objetivo]` · `/soledom resume [objetivo]` | `soledom.animate` | Pausa / reanuda |
| `/soledom pack export\|send [jugador]\|info` | `soledom.pack` | Genera, envía o muestra el pack |
| `/soledom reload` | `soledom.admin` | Recarga config, mensajes, modelos y animaciones (IO asíncrono) |
| `/soledom debug [info\|toggle\|model [obj]\|cleanup [radio]]` | `soledom.admin` | Diagnóstico y limpieza |

`soledom.admin` incluye todos los demás. **Objetivo** puede ser `looking` (hitbox bajo la mira), `nearest`, el
UUID de la instancia o un id de modelo (la instancia más cercana de ese modelo). Todo tiene tab completion.

## Formato de modelo

```json
{
  "format_version": 1,
  "id": "soledom:example_golem",
  "display_name": "<gray>Example Golem",
  "root": { "type": "INTERACTION", "width": 1.0, "height": 1.8, "responsive": true },
  "offset": [0, 0, 0], "rotation": [0, 0, 0], "scale": 1.0,
  "parts": [
    {
      "id": "body", "type": "ITEM_DISPLAY",
      "material": "minecraft:stick", "custom_model_data": 1000,
      "model": "soledom:item/example_golem/body",
      "display_transform": "FIXED", "billboard": "FIXED",
      "position": [0.0, 0.7, 0.0], "rotation": [0, 0, 0], "scale": [1.4, 1.4, 1.4]
    },
    { "id": "head", "type": "BLOCK_DISPLAY", "block": "minecraft:carved_pumpkin[facing=south]",
      "position": [-0.3, 1.25, -0.3], "scale": 0.6 },
    { "id": "label", "type": "TEXT_DISPLAY", "text": "<gold>Golem", "billboard": "CENTER",
      "position": [0, 2.1, 0], "text_style": { "background": "#40000000", "shadowed": true } }
  ],
  "animations": { "idle": "animations/example_idle.json", "rotate": "soledom:example_rotate" },
  "default_animation": "idle",
  "tags": ["example"],
  "metadata": { "author": "you" },
  "options": { "persist": true, "visible_by_default": true, "animate_only_near_players": true }
}
```

**Campos de modelo**

| Campo | Tipo | Por defecto | Notas |
|---|---|---|---|
| `id` | `namespace:path` | obligatorio | Sin namespace se usa `soledom:` |
| `display_name` | MiniMessage | el id | |
| `root.type` | `INTERACTION` \| `MARKER` | `INTERACTION` | `hitbox` es alias de `root`; `"marker": true` fuerza `MARKER` |
| `root.width` / `root.height` | número (0, 64] | 1.0 | Tamaño del hitbox |
| `root.responsive` | bool | true | Animación de brazo al clicar |
| `offset` / `rotation` / `scale` | `[x,y,z]` (scale admite número) | 0 / 0 / 1 | Transformación de todo el modelo |
| `animations` | objeto nombre → id o ruta | `{}` | Rutas relativas a `plugins/Soledom/` |
| `default_animation` | nombre | — | Se reproduce al spawnear/cargar |
| `options.persist` | bool | true | Sobrevive a descargas de chunk y reinicios |
| `options.visible_by_default` | bool | true | |
| `options.animate_only_near_players` | bool | true | Culling por `animation.max-distance` |

**Campos de parte**

| Campo | Aplica a | Notas |
|---|---|---|
| `id` | todas | `[a-z0-9_-]+`, único en el modelo |
| `type` | todas | `ITEM_DISPLAY`, `BLOCK_DISPLAY`, `TEXT_DISPLAY` |
| `material` | ITEM | Id de ítem, p. ej. `minecraft:stick` |
| `custom_model_data` | ITEM | 0 – 16 777 216 (se guarda como float desde 1.21.4) |
| `model` | ITEM | Modelo del pack ligado al CMD. Por defecto `<ns>:item/<modelo>/<parte>` |
| `item_model` | ITEM | Valor del componente `minecraft:item_model` (clientes 1.21.4+) |
| `legacy_base_model` | ITEM | Modelo vanilla base para el override legacy (< 1.21.4) |
| `block` | BLOCK | Block state, p. ej. `minecraft:oak_stairs[facing=north]` |
| `text`, `text_style` | TEXT | MiniMessage; `background` `#AARRGGBB`, `line_width`, `shadowed`, `see_through`, `alignment` |
| `display_transform` | ITEM | `FIXED`, `HEAD`, `GUI`, `GROUND`, `NONE`, `THIRDPERSON_*`, `FIRSTPERSON_*` |
| `billboard` | todas | `FIXED`, `VERTICAL`, `HORIZONTAL`, `CENTER` |
| `position` / `rotation` / `scale` | todas | Bloques / grados (X→Y→Z, como Blockbench) / factor |
| `glow`, `glow_color` | todas | `glow_color` `#RRGGBB` |
| `brightness` | todas | `{ "block": 15, "sky": 15 }` |
| `view_range`, `shadow_radius`, `shadow_strength` | todas | |

Campos desconocidos generan un aviso (para detectar typos); visibles con `debug: true`.

## Formato de animación

```json
{
  "id": "soledom:example_rotate",
  "duration": 2.0,
  "loop": true,
  "priority": 10,
  "reset_on_end": true,
  "tracks": [
    { "part": "body", "channel": "rotation", "interpolation": "linear",
      "keyframes": [
        { "time": 0.0, "value": [0, 0, 0] },
        { "time": 1.0, "value": [0, 180, 0] },
        { "time": 2.0, "value": [0, 360, 0] } ] },
    { "part": "head", "channel": "rotation",
      "keyframes": [
        { "time": 0.0, "value": [0, 0, 0] },
        { "duration": 0.5, "value": [0, 0, 10] },
        { "duration": 1.0, "value": [0, 0, -10] } ] }
  ]
}
```

- `channel`: `translation` (alias `position`), `rotation` (grados), `scale` (admite número).
- Cada keyframe usa `time` absoluto **o** `duration` (segundos desde el keyframe anterior).
- `duration` de la animación por defecto = último keyframe. `priority` decide quién gana si dos animaciones
  mueven el mismo canal de la misma parte; canales distintos se combinan (p. ej. `idle` sube/baja y `rotate` gira).
- Los valores son **deltas sobre la pose de reposo** de la parte.

## Resource pack

1. Exporta tus modelos de Blockbench como *Java Block/Item* a `plugins/Soledom/pack/assets/soledom/models/item/...`
   y sus texturas a `pack/assets/soledom/textures/...`. La ruta del archivo debe coincidir con el `model` de la
   parte (`soledom:item/example_golem/body` → `pack/assets/soledom/models/item/example_golem/body.json`).
2. `/soledom pack export`. Lo que no encuentre se genera como cubo placeholder (te avisa).
3. Sube el ZIP a un host con enlace directo y pon `resource-pack.url` (y `send-on-join: true` si quieres
   enviarlo al entrar). Si `sha1` está vacío se usa el hash del último export.

Contenido generado:

```
pack.mcmeta                                    pack_format + supported_formats (<1.21.9) y min/max_format (1.21.9+)
pack.png                                       icono generado
assets/minecraft/models/item/<ítem>.json       overrides por custom_model_data (clientes < 1.21.4)
assets/minecraft/items/<ítem>.json             range_dispatch sobre custom_model_data (1.21.4+)
assets/<ns>/items/<id>.json                    destinos de item_model (1.21.4+)
assets/<ns>/models/...                         tus modelos o placeholders
```

Formatos (`config.yml → resource-pack`): `pack-format: 34`, `min-format: 34`, `max-format: 97` cubren de 1.21
a 26.3. Sube `max-format` cuando salga una versión nueva.

## Configuración

Ver [`config.yml`](src/main/resources/config.yml) (comentado): `animation.tick-rate`, `animation.max-distance`,
`animation.only-near-players`, `models.persist-across-restart`, `models.auto-cleanup`, `models.max-per-chunk`,
`resource-pack.*`, `debug`. Todos los mensajes están en [`messages.yml`](src/main/resources/messages.yml)
(MiniMessage).

## API para desarrolladores

Añade Soledom como `compileOnly` y `depend: [Soledom]` en tu `plugin.yml`.

```java
SoledomAPI soledom = Soledom.api();

soledom.spawnModel("soledom:example_golem", location).thenAccept(model -> {
    model.playAnimation("rotate");
});

soledom.attachModelToEntity("soledom:example_golem", zombie);

// Modelos por código (sobreviven a /soledom reload)
ModelDefinition def = soledom.parseModel(json);
soledom.registerModel(def, Map.of());
```

| Método | |
|---|---|
| `registerModel`, `unregisterModel`, `parseModel`, `getModel`, `getModels`, `getAnimations` | Registro |
| `spawnModel`, `attachModelToEntity`, `removeModel` | Instancias (`CompletableFuture`, seguros desde cualquier hilo) |
| `playAnimation`, `stopAnimation` | Animación (también en `ModelController`: `pauseAnimations`, `resumeAnimations`) |
| `isModelEntity`, `getModelId`, `getController`, `getActiveModels` | Consulta |
| `setModelVisibility(model, visible)`, `setModelVisibility(model, player, visible)` | Visibilidad |

Eventos (`dev.example.soledom.api.events`): `SoledomInteractEvent` (clic izq./der. en el hitbox, cancelable),
`SoledomModelSpawnEvent` (cancelable), `SoledomModelRemoveEvent`, `SoledomAnimationStartEvent` (cancelable),
`SoledomAnimationLoopEvent`, `SoledomAnimationEndEvent` (con motivo).

```java
@EventHandler
public void onClick(SoledomInteractEvent event) {
    if (event.getAction() == SoledomInteractEvent.Action.RIGHT_CLICK) {
        event.getModel().playAnimation("rotate");
    }
}
```

## Compatibilidad de versiones

Al arrancar se registra la versión y las capacidades detectadas:

```
[Soledom] Server 26.3 | capabilities [DISPLAY_ENTITIES, INTERACTION_ENTITIES, ITEM_COMPONENTS,
          RESOURCE_PACK_INFO_API, NEW_PACK_FORMAT, CUSTOM_MODEL_DATA_COMPONENT, ITEM_MODEL_COMPONENT,
          ITEM_MODEL_DEFINITIONS]
```

| Capacidad | Uso | Fallback |
|---|---|---|
| `DISPLAY_ENTITIES` | Partes visuales | Ninguno: el plugin se desactiva (requiere 1.19.4+) |
| `INTERACTION_ENTITIES` | Raíz con hitbox | Raíz `Marker` sin hitbox |
| `CUSTOM_MODEL_DATA_COMPONENT` | CMD como float (1.21.4+) | `setCustomModelData(int)` |
| `ITEM_MODEL_COMPONENT` | `item_model` | Se ignora |
| `RESOURCE_PACK_INFO_API` | `sendResourcePacks` de Adventure | `setResourcePack` |
| `FOLIA_SCHEDULER` | Schedulers regionales | Bukkit scheduler |
| `NEW_PACK_FORMAT` / `ITEM_MODEL_DEFINITIONS` | Informativas (el pack incluye ambos formatos) | — |

Una versión más nueva que 26.3 muestra un aviso y sigue funcionando por capacidades.

## Arquitectura

```
dev.example.soledom
├── SoledomPlugin            ciclo de vida y cableado
├── api                      SoledomAPI, ModelController, definiciones inmutables, eventos
├── core                     comandos, listeners, config/mensajes, utilidades, implementación de la API
├── registry                 ModelRegistry (snapshot inmutable, lecturas sin locks)
├── loader                   ModelLoader / AnimationLoader / ContentLoader (Java puro, testeable, async)
├── runtime                  ModelManager, ModelEntityController, DisplayPart, HitboxController, PDC keys
├── animation                AnimationEngine, AnimationPlayer, tracks, keyframes, TransformSnapshot
├── resourcepack             PackMeta, PackGenerator, ModelJsonWriter, PackExporter, PackService
├── compat                   ServerVersion, Capability, FeatureFlags, VersionAdapter
└── scheduler                SchedulerAdapter (Bukkit / Folia)
```

Decisiones, riesgos y limitaciones: [`docs/DESIGN.md`](docs/DESIGN.md).

## Checklist de QA

- [ ] Arranque limpio: log de versión/capacidades y "Loaded 1 model(s) and 2 animation(s)".
- [ ] `/soledom spawn soledom:example_golem` → aparecen cuerpo, cabeza de calabaza y etiqueta; `idle` en marcha.
- [ ] Clic derecho/izquierdo en el hitbox dispara `SoledomInteractEvent` (y no daña nada).
- [ ] `/soledom animate looking rotate 2` gira el cuerpo mientras `idle` sigue; `stopanimation looking rotate`
      vuelve a la pose de reposo suavemente.
- [ ] `pause` / `resume` congelan y reanudan.
- [ ] Editar `example_golem.json` (p. ej. `scale`) + `/soledom reload` → la instancia colocada se actualiza.
- [ ] JSON roto → `/soledom reload` lista el error con archivo y ruta; los demás modelos siguen cargados.
- [ ] Reiniciar el servidor → el modelo reaparece con su animación (sin display entities duplicadas).
- [ ] `/kill @e[type=interaction]` → desaparecen también las partes.
- [ ] `models.max-per-chunk: 1` → el segundo spawn en el mismo chunk se rechaza.
- [ ] `/soledom pack export` → ZIP en `export/`, SHA-1 en consola y chat; exportar dos veces da el mismo SHA-1.
- [ ] Cliente con el pack: el cuerpo muestra el modelo del pack (o el cubo placeholder).
- [ ] `/soledom debug` y `/soledom debug model looking` muestran estado coherente.
- [ ] Folia: mismos pasos sin errores de "thread check".
