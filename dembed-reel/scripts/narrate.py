"""Genera la locución de cada escena y escribe src/narration.json.

Fish Audio (por defecto):
    pip install faster-whisper librosa numpy
    FISH_API_KEY=... python scripts/narrate.py
    FISH_VOICE=<id de fish.audio/m/<id>> FISH_API_KEY=... python scripts/narrate.py

Edge TTS (gratis, sin clave):
    pip install edge-tts
    TTS=edge VOICE=es-AR-TomasNeural python scripts/narrate.py

Fish Audio no devuelve los tiempos de cada palabra, así que se sacan transcribiendo el audio
con Whisper y alineando la transcripción con el guion.
"""
import asyncio
import difflib
import json
import os
import re
import ssl
import subprocess
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TTS = os.environ.get("TTS", "fish")

# Voz "voz de locutor k" de la biblioteca de Fish Audio: masculina, grave, de locutor.
FISH_VOICE = os.environ.get("FISH_VOICE", "3f45a7fd7a614655a61eb7027b955783")
FISH_MODEL = os.environ.get("FISH_MODEL", "s1")
FISH_SPEED = float(os.environ.get("FISH_SPEED", "1.1"))
# Tono medio máximo (Hz) de una toma: por encima suena a voz femenina y se descarta.
MAX_PITCH = float(os.environ.get("MAX_PITCH", "165"))

EDGE_VOICE = os.environ.get("VOICE", "es-MX-JorgeNeural")
EDGE_RATE = os.environ.get("RATE", "+10%")

# id de escena -> texto locutado (el orden es el orden del video)
LINES = {
    "hook": "¿Dónde subes tus videos gratis... y encima cobras por ellos?",
    "chat": "En Dembed. Subes gratis y ganas con tus vistas, desde el primer video.",
    "requirements": "¿Mil suscriptores? ¿Cuatro mil horas de reproducción? Olvídalo. Aquí hay cero requisitos.",
    "panel": "Todo en tu propio panel: carpetas, estadísticas, enlaces, e iframe para tu web.",
    "earnings": "Te llevas el treinta por ciento de los anuncios, y cobras por PayPal.",
    "cta": "Crea tu cuenta gratis en dembed punto ce ce.",
}

# Cómo debe sonar cada palabra difícil (una palabra del guion -> una palabra hablada).
SPOKEN = {"Dembed": "Démbed", "dembed": "démbed", "iframe": "áifreim", "PayPal": "Péipal"}
# Tomas por escena: se queda con la que Whisper entiende mejor.
TAKES = int(os.environ.get("TAKES", "5"))


def ssl_context() -> ssl.SSLContext:
    # Detrás de un proxy con CA propia: SSL_CERT_FILE=/ruta/ca.pem
    return ssl.create_default_context(cafile=os.environ.get("SSL_CERT_FILE"))


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


def fish_tts(text: str, path: Path) -> None:
    key = os.environ.get("FISH_API_KEY")
    if not key:
        raise SystemExit("Falta FISH_API_KEY (o usa TTS=edge)")
    body = {"text": text, "reference_id": FISH_VOICE, "format": "mp3", "normalize": True, "prosody": {"speed": FISH_SPEED}}
    req = urllib.request.Request(
        "https://api.fish.audio/v1/tts",
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json", "model": FISH_MODEL},
    )
    with urllib.request.urlopen(req, context=ssl_context()) as res:
        path.write_bytes(res.read())


async def edge_tts_words(text: str, path: Path) -> list[dict]:
    import edge_tts
    import edge_tts.communicate

    edge_tts.communicate._SSL_CTX = ssl_context()
    words = []
    communicate = edge_tts.Communicate(text, EDGE_VOICE, rate=EDGE_RATE, boundary="WordBoundary")
    with path.open("wb") as audio:
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio.write(chunk["data"])
            elif chunk["type"] == "WordBoundary":
                # offset/duration vienen en unidades de 100 ns
                start = chunk["offset"] / 1e7
                words.append({"text": chunk["text"], "start": round(start, 3), "end": round(start + chunk["duration"] / 1e7, 3)})
    return words


def pitch(path: Path) -> float:
    """Mediana del tono fundamental de la toma, en Hz."""
    import librosa
    import numpy as np

    raw = subprocess.check_output(["ffmpeg", "-loglevel", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "16000", "-"])
    audio = np.frombuffer(raw, np.int16).astype(np.float32) / 32768
    f0, voiced, _ = librosa.pyin(audio, fmin=60, fmax=450, sr=16000)
    return float(np.nanmedian(f0[voiced]))


def spoken(text: str) -> str:
    """El texto que se manda al TTS: mismas palabras y puntuación, con la pronunciación forzada."""
    out = []
    for word in text.split():
        bare = re.sub(r"\W", "", word)
        out.append(word.replace(bare, SPOKEN[bare]) if bare in SPOKEN else word)
    return " ".join(out)


def norm(word: str) -> str:
    word = unicodedata.normalize("NFD", word.lower())
    return re.sub(r"[^a-z0-9]", "", word)


_whisper = None


def aligned_words(text: str, path: Path) -> tuple[list[dict], float]:
    """Tiempos de cada palabra del guion, transcribiendo el audio con Whisper."""
    global _whisper
    import numpy as np
    from faster_whisper import WhisperModel

    if _whisper is None:
        _whisper = WhisperModel(os.environ.get("WHISPER_MODEL", "small"), device="cpu", compute_type="int8")
    raw = subprocess.check_output(["ffmpeg", "-loglevel", "error", "-i", str(path), "-f", "s16le", "-ac", "1", "-ar", "16000", "-"])
    audio = np.frombuffer(raw, np.int16).astype(np.float32) / 32768
    # Los tiempos salen de una pasada guiada por el guion; el puntaje, de una sin guiar
    # (si no, Whisper "copia" el guion y toda toma parece perfecta).
    segments, _ = _whisper.transcribe(audio, language="es", word_timestamps=True, initial_prompt=text)
    heard = [w for s in segments for w in s.words if norm(w.word)]
    blind = " ".join(norm(w) for s in _whisper.transcribe(audio, language="es")[0] for w in s.text.split() if norm(w))

    script = [w for w in text.split() if norm(w)]
    said = [norm(w) for w in spoken(text).split() if norm(w)]
    times: list[tuple[float, float] | None] = [None] * len(script)
    heard_norm = [norm(w.word) for w in heard]
    matcher = difflib.SequenceMatcher(a=said, b=heard_norm, autojunk=False)
    # Puntaje de la toma: qué tan parecido es lo que se oye al guion (letra a letra)
    score = difflib.SequenceMatcher(a=" ".join(norm(w) for w in script), b=blind, autojunk=False).ratio()
    for tag, a0, a1, b0, b1 in matcher.get_opcodes():
        if tag == "equal" or (tag == "replace" and a1 - a0 == b1 - b0):
            for i, j in zip(range(a0, a1), range(b0, b1)):
                times[i] = (heard[j].start, heard[j].end)
        elif tag == "replace":
            # p. ej. "iframe" -> "i frame": reparte el tramo oído entre las palabras del guion
            start, end = heard[b0].start, heard[b1 - 1].end
            step = (end - start) / (a1 - a0)
            for k, i in enumerate(range(a0, a1)):
                times[i] = (start + k * step, start + (k + 1) * step)

    # Palabras que Whisper no oyó: interpolar entre vecinas
    total = duration(path)
    for i, t in enumerate(times):
        if t is None:
            prev = next((times[k][1] for k in range(i - 1, -1, -1) if times[k]), 0.0)
            nxt = next((times[k][0] for k in range(i + 1, len(times)) if times[k]), total)
            times[i] = (prev, max(prev, nxt))
    return [{"text": w, "start": round(s, 3), "end": round(e, 3)} for w, (s, e) in zip(script, times)], score


async def main() -> None:
    out_dir = ROOT / "public" / "voice"
    out_dir.mkdir(parents=True, exist_ok=True)
    voice = f"fish:{FISH_VOICE}" if TTS == "fish" else f"edge:{EDGE_VOICE}"
    meta = {"voice": voice, "scenes": []}
    for scene_id, text in LINES.items():
        path = out_dir / f"{scene_id}.mp3"
        if TTS == "fish":
            best = None
            for take in range(TAKES):
                candidate = path.with_suffix(f".take{take}.mp3")
                fish_tts(spoken(text), candidate)
                normalize(candidate)
                hz = pitch(candidate)
                if hz > MAX_PITCH:
                    print(f"  {scene_id} toma {take + 1}: descartada, tono {hz:.0f} Hz")
                    continue
                words, score = aligned_words(text, candidate)
                print(f"  {scene_id} toma {take + 1}: {score:.2f} ({hz:.0f} Hz)")
                if best is None or score > best[0]:
                    best = (score, candidate, words)
                if score > 0.98:
                    break
            if best is None:
                raise SystemExit(f"Ninguna toma de '{scene_id}' sonó con voz masculina; prueba otra FISH_VOICE")
            _, chosen, words = best
            chosen.replace(path)
            for leftover in out_dir.glob(f"{scene_id}.take*.mp3"):
                leftover.unlink()
        else:
            words = await edge_tts_words(text, path)
            normalize(path)
        meta["scenes"].append({"id": scene_id, "text": text, "file": f"voice/{scene_id}.mp3", "seconds": round(duration(path), 3), "words": words})
        print(f"{scene_id:13} {meta['scenes'][-1]['seconds']:.2f}s  {text}")
    (ROOT / "src" / "narration.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n")


asyncio.run(main())
