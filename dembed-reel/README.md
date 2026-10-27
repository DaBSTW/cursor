# Reel de dembed.cc (Remotion)

Video vertical 1080×1920 a 30 fps (~36 s) para TikTok e Instagram Reels que presenta
[dembed.cc](https://dembed.cc), con locución en español, motion graphics y efectos de sonido.
El render final está en [`dembed-reel.mp4`](dembed-reel.mp4).

| # | Escena | Locución |
|---|--------|----------|
| 1 | Chat «¿Y dónde subo mis videos?» | ¿Dónde subes tus videos gratis… y encima cobras por ellos? / En Dembed… |
| 2 | 1.000 suscriptores y 4.000 horas tachados → contador a **0 requisitos** | ¿Mil suscriptores? ¿Cuatro mil horas…? Olvídalo. |
| 3 | Panel en 3D con carpetas, estadísticas, enlaces e iframe | Todo en tu propio panel… |
| 4 | **30 %** de los anuncios + notificaciones (vistas, saldo, PayPal) | Te llevas el treinta por ciento… |
| 5 | Cierre: logo, `dembed.cc` tipeándose y botón | Crea tu cuenta gratis en dembed punto c c. |

Las animaciones están sincronizadas palabra por palabra con la voz (`src/narration.json`).

## Uso

```bash
npm install
npm run studio     # previsualizar y editar
npm run render     # genera out/dembed-reel.mp4
```

## Cambiar la voz o el guion

Los textos están en `scripts/narrate.py`. La voz se genera con
[edge-tts](https://github.com/rany2/edge-tts), que guarda los mp3 en `public/voice/` y los
tiempos de cada palabra en `src/narration.json`; las escenas se ajustan solas a la nueva duración.

```bash
pip install edge-tts
python scripts/narrate.py                          # voz por defecto: es-MX-JorgeNeural
VOICE=es-AR-TomasNeural python scripts/narrate.py  # acento rioplatense
VOICE=es-MX-DaliaNeural python scripts/narrate.py  # voz femenina
```

Si cambias una palabra a la que está atada una animación (por ejemplo «Olvídalo» o «PayPal»),
actualiza también el `wordAt(...)` correspondiente en `src/scenes/`.

## Notas

- Fuentes: Inter / Inter Display (licencia OFL), incluidas en `public/fonts/`.
- Sin música de fondo, para que puedas poner un audio en tendencia desde la propia app.
- Zona segura: el contenido importante queda entre y≈150 y y≈1550 para no quedar tapado por la
  interfaz de TikTok/Instagram.
