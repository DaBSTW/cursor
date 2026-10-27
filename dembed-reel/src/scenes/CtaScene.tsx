import React from "react";
import { AbsoluteFill, Audio, Sequence, staticFile, useCurrentFrame } from "remotion";
import { ease, pop } from "../anim";
import { Backdrop } from "../components/Backdrop";
import { CtaButton } from "../components/CtaButton";
import { Logo } from "../components/Logo";
import { Sfx } from "../components/Sfx";
import { C, DISPLAY, FONT } from "../theme";
import { VOICE_LEAD, blockFrames, line, wordAt } from "../timing";

/** El cierre se queda en pantalla un rato más tras la voz. */
export const ctaSceneFrames = () => blockFrames("cta") + 50;

const URL = "dembed.cc";

export const CtaScene: React.FC = () => {
  const frame = useCurrentFrame();
  const w = (x: string) => wordAt("cta", x);
  const logo = pop(frame, 0, 12);
  const typeStart = w("dembed") - 2;
  const typed = Math.max(0, Math.min(URL.length, Math.floor((frame - typeStart) / 2)));
  const url = pop(frame, w("Crea") - 4, 12);
  const btn = pop(frame, w("cuenta"), 12);
  const pulse = 1 + Math.max(0, Math.sin((frame - typeStart - URL.length * 2) / 7)) * 0.035 * (frame > typeStart + 20 ? 1 : 0);
  const caret = frame % 16 < 8;

  return (
    <AbsoluteFill>
      <Backdrop bg={C.ink} ring="#1B3326" ring2="#1B3326" seed={800} />
      <Sequence from={VOICE_LEAD} layout="none">
        <Audio src={staticFile(line("cta").file)} />
      </Sequence>
      <Sfx at={w("Crea")} name="whoosh" volume={0.4} />
      <Sfx at={typeStart + URL.length * 2} name="ding" volume={0.4} />

      <AbsoluteFill style={{ alignItems: "center", justifyContent: "center", paddingBottom: 200 }}>
        <div style={{ transform: `scale(${0.6 + logo * 0.4})`, opacity: Math.min(1, logo * 2) }}>
          <Logo size={150} color={C.green} />
        </div>
        <div
          style={{
            marginTop: 50,
            fontFamily: DISPLAY,
            fontWeight: 900,
            fontSize: 92,
            letterSpacing: -4,
            lineHeight: 1.02,
            color: C.white,
            textAlign: "center",
            opacity: ease(frame, 4, 10),
            transform: `translateY(${(1 - ease(frame, 4, 14)) * 30}px)`,
          }}
        >
          Sube gratis.
          <br />
          <span style={{ color: C.green }}>Cobra desde el primero.</span>
        </div>

        <div
          style={{
            marginTop: 90,
            display: "flex",
            alignItems: "center",
            gap: 22,
            width: 760,
            background: C.white,
            borderRadius: 60,
            padding: "28px 44px",
            fontFamily: FONT,
            fontWeight: 800,
            fontSize: 64,
            letterSpacing: -1.5,
            color: C.ink,
            transform: `scale(${0.7 + url * 0.3})`,
            opacity: Math.min(1, url * 2),
          }}
        >
          <svg width="48" height="48" viewBox="0 0 24 24">
            <path d="M6 11h12v10H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3" fill="none" stroke={C.greenDeep} strokeWidth="2.6" strokeLinejoin="round" />
          </svg>
          <span>
            {URL.slice(0, typed)}
            <span style={{ opacity: caret ? 1 : 0, fontWeight: 400, color: C.greenDeep }}>|</span>
          </span>
        </div>

        <div style={{ marginTop: 34, transform: `scale(${(0.7 + btn * 0.3) * pulse})`, opacity: Math.min(1, btn * 2) }}>
          <CtaButton bg={C.green} color={C.ink} size={58} label="Crea tu cuenta gratis" />
        </div>
      </AbsoluteFill>

      <div
        style={{
          position: "absolute",
          bottom: 420,
          left: 90,
          right: 90,
          textAlign: "center",
          fontFamily: FONT,
          fontWeight: 500,
          fontSize: 27,
          lineHeight: 1.4,
          color: "#8D919B",
          opacity: ease(frame, w("cuenta"), 12),
        }}
      >
        Hasta 20 GB por video. El 30 % es del RPM real por país que reporta la red publicitaria; pago desde $25. Las
        ganancias varían y no están garantizadas.
      </div>
    </AbsoluteFill>
  );
};
