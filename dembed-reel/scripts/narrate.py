"""Genera la locución de cada escena con edge-tts y escribe src/narration.json.

Uso:  python scripts/narrate.py            (requiere `pip install edge-tts`)
      VOICE=es-AR-TomasNeural python scripts/narrate.py
"""
import asyncio
import json
import os
import ssl
import subprocess
from pathlib import Path

import edge_tts
import edge_tts.communicate

# Detrás de un proxy con CA propia: SSL_CERT_FILE=/ruta/ca.pem python scripts/narrate.py
if os.environ.get("SSL_CERT_FILE"):
    edge_tts.communicate._SSL_CTX = ssl.create_default_context(cafile=os.environ["SSL_CERT_FILE"])

ROOT = Path(__file__).resolve().parent.parent
VOICE = os.environ.get("VOICE", "es-MX-JorgeNeural")
RATE = os.environ.get("RATE", "+10%")
PITCH = os.environ.get("PITCH", "+0Hz")

# id de escena -> texto locutado (el orden es el orden del video)
LINES = {
    "hook": "¿Dónde subes tus videos gratis... y encima cobras por ellos?",
    "chat": "En Dembed. Subes gratis y ganas con tus vistas, desde el primer video.",
    "requirements": "¿Mil suscriptores? ¿Cuatro mil horas de reproducción? Olvídalo. Aquí hay cero requisitos.",
    "panel": "Todo en tu propio panel: carpetas, estadísticas, enlaces, e iframe para tu web.",
    "earnings": "Te llevas el treinta por ciento de los anuncios, y cobras por PayPal.",
    "cta": "Crea tu cuenta gratis en dembed punto c c.",
}


def duration(path: Path) -> float:
    out = subprocess.check_output(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)]
    )
    return float(out)


def normalize(path: Path) -> None:
    """Sube la voz a ~-14 LUFS, el volumen de referencia de TikTok/Instagram."""
    tmp = path.with_suffix(".tmp.mp3")
    subprocess.check_call(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", str(path), "-af", "loudnorm=I=-14:TP=-1.5:LRA=7", "-ar", "48000", "-b:a", "192k", str(tmp)]
    )
    tmp.replace(path)


async def main() -> None:
    out_dir = ROOT / "public" / "voice"
    out_dir.mkdir(parents=True, exist_ok=True)
    meta = {"voice": VOICE, "scenes": []}
    for scene_id, text in LINES.items():
        path = out_dir / f"{scene_id}.mp3"
        words = []
        communicate = edge_tts.Communicate(text, VOICE, rate=RATE, pitch=PITCH, boundary="WordBoundary")
        with path.open("wb") as audio:
            async for chunk in communicate.stream():
                if chunk["type"] == "audio":
                    audio.write(chunk["data"])
                elif chunk["type"] == "WordBoundary":
                    # offset/duration vienen en unidades de 100 ns
                    words.append({"text": chunk["text"], "start": round(chunk["offset"] / 1e7, 3), "end": round((chunk["offset"] + chunk["duration"]) / 1e7, 3)})
        normalize(path)
        meta["scenes"].append({"id": scene_id, "text": text, "file": f"voice/{scene_id}.mp3", "seconds": round(duration(path), 3), "words": words})
        print(f"{scene_id:13} {meta['scenes'][-1]['seconds']:.2f}s  {text}")
    (ROOT / "src" / "narration.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n")


asyncio.run(main())
