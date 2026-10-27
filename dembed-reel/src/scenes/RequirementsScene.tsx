import React from "react";
import { AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame } from "remotion";
import { ease, pop } from "../anim";
import { Backdrop } from "../components/Backdrop";
import { Logo } from "../components/Logo";
import { Sfx } from "../components/Sfx";
import { C, DISPLAY, FONT } from "../theme";
import { VOICE_LEAD, blockFrames, line, wordAt } from "../timing";

export const requirementsSceneFrames = () => blockFrames("requirements") + 16;

const Struck: React.FC<{ text: string; at: number; strikeAt: number; frame: number }> = ({
  text,
  at,
  strikeAt,
  frame,
}) => {
  const p = pop(frame, at, 12);
  const strike = ease(frame, strikeAt, 9);
  const shake = frame >= strikeAt && frame < strikeAt + 10 ? Math.sin(frame * 2.4) * 9 * (1 - (frame - strikeAt) / 10) : 0;
  return (
    <div
      style={{
        position: "relative",
        alignSelf: "flex-start",
        fontFamily: DISPLAY,
        fontWeight: 900,
        fontSize: 68,
        letterSpacing: -2.5,
        whiteSpace: "nowrap",
        lineHeight: 1.15,
        color: strike > 0.5 ? "#6E727B" : C.white,
        opacity: Math.min(1, p * 1.5),
        transform: `translateX(${(1 - p) * -80 + shake}px)`,
      }}
    >
      {text}
      <div
        style={{
          position: "absolute",
          left: -6,
          top: "54%",
          height: 10,
          borderRadius: 5,
          width: `calc(${strike * 100}% + 12px)`,
          background: C.red,
        }}
      />
    </div>
  );
};

const Chip: React.FC<{ text: string; at: number; frame: number }> = ({ text, at, frame }) => {
  const p = pop(frame, at, 11);
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 14,
        padding: "20px 34px",
        borderRadius: 40,
        background: "rgba(31,216,108,0.12)",
        color: C.green,
        fontFamily: FONT,
        fontWeight: 800,
        fontSize: 44,
        transform: `scale(${p})`,
        opacity: Math.min(1, p * 2),
      }}
    >
      <span style={{ fontSize: 36 }}>✓</span>
      {text}
    </div>
  );
};

export const RequirementsScene: React.FC = () => {
  const frame = useCurrentFrame();
  const w = (x: string) => wordAt("requirements", x);
  const strikeAt = w("Olvídalo");
  const zeroAt = w("Aquí");
  const cero = w("cero");
  const requisitos = w("requisitos");

  // Contador 4.000 → 0 entre "Aquí" y "cero"; el número aterriza con rebote.
  const count = Math.round(interpolate(ease(frame, zeroAt, cero - zeroAt + 2), [0, 1], [4000, 0]));
  const land = pop(frame, cero, 9, 0.8);
  const zIn = pop(frame, zeroAt, 14);
  const listUp = ease(frame, zeroAt - 6, 14);

  return (
    <AbsoluteFill>
      <Backdrop bg={C.ink} ring="#1B3326" ring2="#1B3326" seed={200} />
      <Sequence from={VOICE_LEAD} layout="none">
        <Audio src={staticFile(line("requirements").file)} />
      </Sequence>
      <Sfx at={strikeAt} name="whoosh" volume={0.6} />
      <Sfx at={cero} name="ding" volume={0.35} />

      <div style={{ position: "absolute", top: 150, left: 72 }}>
        <Logo size={70} color={C.green} />
      </div>

      <div
        style={{
          position: "absolute",
          top: 400,
          left: 72,
          right: 60,
          display: "flex",
          flexDirection: "column",
          gap: 18,
          transform: `translateY(${listUp * -40}px) scale(${1 - listUp * 0.12})`,
          transformOrigin: "top left",
          opacity: 1 - listUp * 0.45,
        }}
      >
        <div
          style={{
            fontFamily: FONT,
            fontWeight: 800,
            fontSize: 50,
            lineHeight: 1.2,
            color: "#A3A7B0",
            marginBottom: 16,
            opacity: ease(frame, 0, 10),
          }}
        >
          Para cobrar por tus videos, otras plataformas te piden:
        </div>
        <Struck text="1.000 suscriptores" at={w("Mil") - 2} strikeAt={strikeAt} frame={frame} />
        <Struck text="4.000 horas de reproducción" at={w("Cuatro") - 2} strikeAt={strikeAt + 4} frame={frame} />
      </div>

      <div
        style={{
          position: "absolute",
          top: 900,
          left: 60,
          right: 40,
          display: "flex",
          alignItems: "center",
          gap: 30,
          opacity: Math.min(1, zIn * 2),
        }}
      >
        <div
          style={{
            fontFamily: DISPLAY,
            fontWeight: 900,
            fontSize: count === 0 ? 460 : 250,
            lineHeight: 1,
            letterSpacing: -12,
            color: C.green,
            minWidth: 380,
            textAlign: "center",
            transform: `scale(${count === 0 ? 1.5 - land * 0.5 : 0.7 + zIn * 0.3})`,
            fontVariantNumeric: "tabular-nums",
            textShadow: "0 0 60px rgba(31,216,108,0.35)",
          }}
        >
          {count === 0 ? "0" : count.toLocaleString("es-ES")}
        </div>
        <div style={{ opacity: ease(frame, requisitos - 4, 8), transform: `translateX(${(1 - ease(frame, requisitos - 4, 12)) * 40}px)` }}>
          <div style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 92, letterSpacing: -4, color: C.white, lineHeight: 1.02 }}>
            requisitos en Dembed.
          </div>
          <div style={{ fontFamily: FONT, fontWeight: 700, fontSize: 46, color: "#A3A7B0", marginTop: 14 }}>
            Ganas desde tu primer video.
          </div>
        </div>
      </div>

      <div style={{ position: "absolute", top: 1420, left: 72, right: 72, display: "flex", flexWrap: "wrap", gap: 20 }}>
        <Chip text="Alojamiento gratis" at={requisitos + 8} frame={frame} />
        <Chip text="Tu logo en el reproductor" at={requisitos + 13} frame={frame} />
        <Chip text="30 % de los anuncios" at={requisitos + 18} frame={frame} />
      </div>
    </AbsoluteFill>
  );
};
