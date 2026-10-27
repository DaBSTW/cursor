import narration from "./narration.json";
import { FPS } from "./theme";

export type SceneId = "hook" | "chat" | "requirements" | "panel" | "earnings" | "cta";

type Line = (typeof narration.scenes)[number];

/** Frames de silencio antes de que arranque la voz dentro de su bloque. */
export const VOICE_LEAD = 6;
/** Frames de aire después de que termina la voz. */
export const VOICE_TAIL = 14;
export const TRANSITION = 14;

export const line = (id: SceneId): Line => {
  const found = narration.scenes.find((s) => s.id === id);
  if (!found) throw new Error(`Falta la locución de "${id}" en narration.json`);
  return found;
};

export const voiceFrames = (id: SceneId) => Math.ceil(line(id).seconds * FPS);

/** Duración del bloque de una locución, con aire antes y después. */
export const blockFrames = (id: SceneId) => VOICE_LEAD + voiceFrames(id) + VOICE_TAIL;

/** Frame (relativo al bloque) en el que se dice una palabra; `nth` desempata repeticiones. */
export const wordAt = (id: SceneId, word: string, nth = 0): number => {
  const norm = (w: string) => w.toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
  const hits = line(id).words.filter((w) => norm(w.text) === norm(word));
  const hit = hits[nth];
  if (!hit) throw new Error(`"${word}" no aparece en la locución de "${id}"`);
  return VOICE_LEAD + Math.round(hit.start * FPS);
};
