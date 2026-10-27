import { Easing, interpolate, spring } from "remotion";
import { FPS } from "./theme";

/** Spring de entrada 0→1 a partir de `delay`. */
export const pop = (frame: number, delay = 0, damping = 14, mass = 0.7) =>
  spring({ frame: frame - delay, fps: FPS, config: { damping, mass, stiffness: 170 } });

/** Rampa 0→1 con ease-out entre `start` y `start + dur`. */
export const ease = (frame: number, start: number, dur: number, easing = Easing.bezier(0.22, 1, 0.36, 1)) =>
  interpolate(frame, [start, start + dur], [0, 1], { extrapolateLeft: "clamp", extrapolateRight: "clamp", easing });

export const lerp = (t: number, a: number, b: number) => a + (b - a) * t;
