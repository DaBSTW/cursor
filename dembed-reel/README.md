# Reel de dembed.cc (Remotion)

Video vertical 1080×1920 a 30 fps (~31 s) para TikTok e Instagram Reels que presenta
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

## Voz (Fish Audio)

La locución usa solo voz masculina: **«voz de locutor k»** de la biblioteca de
[Fish Audio](https://fish.audio/m/3f45a7fd7a614655a61eb7027b955783) (grave, de locutor), a velocidad 1.1.
Cada toma se mide y se descarta si su tono medio supera los 165 Hz (`MAX_PITCH`), así nunca se
cuela una toma que suene femenina. Los textos y la pronunciación de palabras difíciles
(`Dembed`, `iframe`, `PayPal`) están en `scripts/narrate.py`.

El script genera hasta 5 tomas por escena, las transcribe con Whisper y se queda con la más
clara; de esa transcripción salen también los tiempos de cada palabra (`src/narration.json`),
así que las animaciones y la duración de las escenas se reajustan solas.

```bash
pip install faster-whisper librosa numpy
export FISH_API_KEY=...                       # tu clave de fish.audio (no la subas al repo)
python scripts/narrate.py                     # voz por defecto
FISH_VOICE=<id> python scripts/narrate.py     # otra voz: el id de fish.audio/m/<id>
FISH_SPEED=1.1 python scripts/narrate.py      # más rápido
TTS=edge VOICE=es-AR-TomasNeural python scripts/narrate.py   # alternativa gratis con edge-tts
npm run render
```

Si cambias una palabra a la que está atada una animación (por ejemplo «Olvídalo» o «PayPal»),
actualiza también el `wordAt(...)` correspondiente en `src/scenes/`.

## Notas

- Fuentes: Inter / Inter Display (licencia OFL), incluidas en `public/fonts/`.
- Sin música de fondo, para que puedas poner un audio en tendencia desde la propia app.
- Zona segura: el contenido importante queda entre y≈150 y y≈1550 para no quedar tapado por la
  interfaz de TikTok/Instagram.
