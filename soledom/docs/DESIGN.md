# Soledom — Fase 0: supuestos, decisiones y riesgos

Documento de diseño del MVP. Soledom es una implementación original (clean-room) de la categoría funcional
"modelos custom con display entities + animación server-side + generador de resource pack". No contiene código,
assets, nombres ni APIs de ningún otro plugin del sector.

## Supuestos verificados

| Tema | Valor | Cómo se verificó |
|---|---|---|
| Última versión estable de Paper | **26.3** (build 205, canal STABLE) | API `fill.papermc.io/v3` y `maven-metadata.xml` de `repo.papermc.io` |
| Esquema de versiones | `1.21.x` → año (`26.1`, `26.2`, `26.3`) | Mismo origen; `ServerVersion` soporta ambos |
| Java requerido por Paper 26.x | Java 25 (el plugin está compilado para Java 21 y corre en 25) | Arranque real de Paper 26.3 con Temurin 25 |
| Pack formats (resource pack) | 34 (1.21–1.21.1), 42, 46 (1.21.4), 55, 63, 64, 69 (1.21.9–1.21.10), 75 (1.21.11), 84 (26.1), 88 (26.2), **97.1 (26.3)** | minecraft.wiki/w/Pack_format |
| Reglas de `pack.mcmeta` desde 1.21.9 | `min_format`/`max_format` obligatorios; `pack_format` + `supported_formats` obligatorios **solo** si el rango baja de 65 | minecraft.wiki/w/Pack.mcmeta |
| Custom model data desde 1.21.4 | Componente con listas (`floats`, `flags`, `strings`, `colors`); los item definitions leen `floats[0]` con `range_dispatch` | Firma de `CustomModelDataComponent` en paper-api 1.21.4 |

## Decisiones

1. **Compilar contra paper-api 1.21.4** (Java 21). Es el piso "moderno" que expone el componente de custom model
   data e `item_model`. Las APIs que no existen en 1.21.0–1.21.3 viven en `compat/ComponentItemBridge`, una clase
   que la JVM solo carga si `FeatureFlags` detectó la capacidad. `api-version: '1.21'` permite cargar en todo 1.21+.
2. **Detección de capacidades en vez de tablas de versiones.** `FeatureFlags.detect()` hace unas pocas
   comprobaciones puntuales (`Class.forName`, existencia de un método). Solo lo que no se puede sondear desde el
   servidor (formato de pack del cliente, item definitions) se deriva de la versión. Una versión no reconocida se
   trata como moderna.
3. **Un único punto sensible a versiones:** `VersionAdapter`/`ModernVersionAdapter`. Si Paper cambia algo, se toca
   una clase. Cadena de fallback para custom model data: componente → entero legado → aviso único en log.
4. **Sin NMS.** Todo con Paper API estable: `ItemDisplay`, `BlockDisplay`, `TextDisplay`, `Interaction`,
   `Marker`, `Transformation`, interpolación de display entities, PDC, schedulers regionales.
5. **Persistencia:** solo la entidad raíz se guarda en el mundo (PDC con `soledom:model_id`). Las partes son
   `setPersistent(false)` y se reconstruyen desde la definición actual cuando la raíz carga. Ventajas: nunca quedan
   display entities huérfanas tras un crash/reinicio, y editar un modelo actualiza las instancias ya colocadas.
6. **Animación:** un task repetitivo por modelo animado, en el scheduler de su entidad raíz (hilo principal en
   Paper, región en Folia), cada `animation.tick-rate` ticks. Cada frame manda una `Transformation` con
   `interpolation_duration = tick-rate`; el cliente interpola entre frames, así 2–3 ticks se ven fluidos con menos
   paquetes. Prioridad por canal (`translation`/`rotation`/`scale`) y por parte.
7. **Folia:** `FoliaSchedulerAdapter` usa `EntityScheduler`/`RegionScheduler`/`GlobalRegionScheduler`. Las
   operaciones públicas saltan solas al hilo dueño (`ModelEntityController#runOnOwner`).
8. **Resource pack:** generación en memoria (pura Java, testeable), ZIP con timestamps fijos (mismo contenido ⇒
   mismo SHA-1), escritura atómica. Se escriben **ambos** formatos de override: `models/item/*.json` con
   `overrides` (clientes < 1.21.4) e `items/*.json` con `range_dispatch` (1.21.4+). Texturas placeholder generadas
   por código; nunca se incluyen assets de terceros.
9. **IO fuera del hilo principal** en `/soledom reload` y `/soledom pack export`. La carga inicial es síncrona a
   propósito: ocurre antes de que el servidor empiece a tickear y los modelos persistidos en chunks ya cargados
   necesitan sus definiciones.

## Riesgos técnicos y mitigación

| Riesgo | Mitigación |
|---|---|
| Paper elimina `ItemMeta#setCustomModelData(Integer)` o el componente | Cadena de fallback + `catch (LinkageError)`; el modelo se muestra sin custom model y se avisa una vez |
| Cambia el formato del `pack.mcmeta` otra vez | `min-format`/`max-format`/`pack-format` configurables; `PackMeta` aislado y testeado |
| Desde 1.21.9 no hay spawn chunks permanentes | `ModelManager.spawn` carga el chunk con `getChunkAtAsync` antes de spawnear |
| Clientes sin el resource pack | Las partes `BLOCK_DISPLAY`/`TEXT_DISPLAY` funcionan sin pack; los `ITEM_DISPLAY` muestran el ítem base |
| `Bukkit.getEntity`/iteración global no es segura en Folia | Solo se usan búsquedas locales (`getNearbyEntities`) desde el hilo de la región |
| Rotaciones grandes por frame se interpolan por el camino corto | Interpolación lineal en euler en el servidor y frames pequeños (tick-rate bajo) |

## Importación de Blockbench

- Cada hueso con cubos → un modelo de ítem propio y una parte `ITEM_DISPLAY`; los huesos sin cubos → `GROUP`.
  El árbol de huesos se conserva como jerarquía de partes, resuelta en el servidor por `PoseSolver`
  (`mundo(hijo) = mundo(padre) · local(hijo)`); cada display recibe su matriz con `setTransformationMatrix`, que el
  servidor descompone (SVD), así que rotaciones y escalas anidadas son exactas.
- Verificado en el cliente 26.3 (no ofuscado): `DisplayRenderer$ItemDisplayRenderer` rota el ítem 180° en Y y el
  modelo se centra en (8,8,8). Los modelos generados usan `display.fixed.rotation = [0,180,0]` para anularlo, y la
  geometría se coloca alrededor del pivote del hueso.
- Ejes de animación: Blockbench aplica los keyframes restando X/Y de rotación y X de posición (en píxeles); el
  conversor hace `rotación (-x,-y,z)` y `posición (-x,y,z)/16`. La rotación animada se **suma** a la de reposo en
  espacio Euler, igual que Blockbench.
- Proyectos `java_block` (0..16) se desplazan para que (8,0,8) sea el origen; el formato Blockbench 5 (grupos en
  una lista `groups` aparte) también se lee.
- Medido en servidor real (1.21.4 y 26.3) con el robot de ejemplo: las traslaciones de reposo coinciden con los
  pivotes de Blockbench y los hijos heredan la animación del padre.

## LIMITACIONES (Vanilla/Paper)

- **Sin hitbox con raíz `MARKER`**: un `Marker` no existe en el cliente, no se puede clicar. Usa `INTERACTION`.
- **Hitbox única por modelo**: Interaction es un prisma alineado a ejes; no rota ni sigue partes animadas.
- **Escala no uniforme + rotación** a nivel de modelo no compone exactamente (limitación de una sola
  `Transformation` por display); se recomienda escala uniforme en `scale` de modelo.
- **Modelos adjuntos** (`attachModelToEntity`) siguen al objetivo por teletransporte suavizado
  (`teleport_duration`), no montados: puede verse un retardo de `tick-rate` ticks.
- **Hosting del pack**: el plugin genera el ZIP pero no lo sirve por HTTP; se configura una URL externa.
- **Overrides legacy (< 1.21.4)** necesitan el modelo base vanilla del ítem. Se deduce (`item/generated`,
  `item/handheld`, `block/<id>`) o se fija con `legacy_base_model`. En 1.21.4+ no hace falta.
- **Ítems con propiedades especiales** (brújula, reloj, arco…) pierden su comportamiento visual si se usan como
  base de custom model data. Usa ítems simples (`stick`, `paper`, `leather_horse_armor`).

## Mejoras futuras

- Curvas de easing (bezier, catmull-rom) y evaluación de expresiones Molang en keyframes.
- Rotación libre de cubos usando la rotación euler de elementos de los clientes más nuevos (26.x), activable
  cuando el servidor no necesite soportar 1.21.x.
- Hitboxes múltiples por parte y "seats" montables.
- Servidor HTTP embebido opcional para servir el pack.
- Estados/máquina de animaciones (idle → walk → attack) ligados a la IA de la entidad adjunta.
- Visibilidad por jugador persistente y LOD por distancia.
