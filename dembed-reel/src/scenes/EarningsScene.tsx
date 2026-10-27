import React from "react";
import { AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame } from "remotion";
import { ease, pop } from "../anim";
import { Backdrop } from "../components/Backdrop";
import { Logo } from "../components/Logo";
import { Sfx } from "../components/Sfx";
import { C, DISPLAY, FONT } from "../theme";
import { VOICE_LEAD, blockFrames, line, wordAt } from "../timing";

export const earningsSceneFrames = () => blockFrames("earnings") + 14;

const Notification: React.FC<{
  icon: React.ReactNode;
  text: React.ReactNode;
  when: string;
  at: number;
  tilt: number;
  frame: number;
}> = ({ icon, text, when, at, tilt, frame }) => {
  const p = pop(frame, at, 12, 0.8);
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 30,
        background: C.paper,
        borderRadius: 42,
        padding: "28px 34px",
        boxShadow: "0 22px 50px rgba(0,0,0,0.16)",
        transform: `translateY(${(1 - p) * -140}px) rotate(${tilt * p}deg) scale(${0.9 + p * 0.1})`,
        opacity: Math.min(1, p * 2),
      }}
    >
      <div
        style={{
          width: 100,
          height: 100,
          flex: "none",
          borderRadius: 26,
          background: C.ink,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {icon}
      </div>
      <div style={{ flex: 1, fontFamily: FONT }}>
        <div style={{ display: "flex", justifyContent: "space-between", color: "#5E636C", fontWeight: 700, fontSize: 28 }}>
          <span>DEMBED</span>
          <span style={{ fontWeight: 600 }}>{when}</span>
        </div>
        <div style={{ color: C.ink, fontWeight: 800, fontSize: 40, lineHeight: 1.18, letterSpacing: -0.8, marginTop: 4 }}>
          {text}
        </div>
      </div>
    </div>
  );
};

const svg = (d: string) => (
  <svg width="56" height="56" viewBox="0 0 24 24">
    <path d={d} fill="none" stroke={C.green} strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const EarningsScene: React.FC = () => {
  const frame = useCurrentFrame();
  const w = (x: string) => wordAt("earnings", x);
  const treinta = w("treinta");
  const pct = Math.round(interpolate(ease(frame, treinta - 6, 22), [0, 1], [0, 30]));
  const big = pop(frame, treinta - 6, 10, 0.9);
  const anuncios = w("anuncios");
  const paypal = w("PayPal");

  return (
    <AbsoluteFill>
      <Backdrop bg={C.green} ring={C.greenDeep} ring2={C.greenSoft} seed={600} />
      <Sequence from={VOICE_LEAD} layout="none">
        <Audio src={staticFile(line("earnings").file)} />
      </Sequence>
      <Sfx at={anuncios} name="pop" volume={0.35} />
      <Sfx at={paypal} name="ding" volume={0.4} />

      <div style={{ position: "absolute", top: 150, left: 72 }}>
        <Logo size={70} />
      </div>

      <div style={{ position: "absolute", top: 290, left: 72, right: 60, fontFamily: DISPLAY, color: C.ink }}>
        <div
          style={{
            fontWeight: 900,
            fontSize: 110,
            letterSpacing: -5,
            opacity: ease(frame, 2, 8),
            transform: `translateY(${(1 - ease(frame, 2, 12)) * 40}px)`,
          }}
        >
          Te llevas el
        </div>
        <div
          style={{
            fontWeight: 900,
            fontSize: 330,
            lineHeight: 0.92,
            letterSpacing: -16,
            transform: `scale(${0.5 + big * 0.5})`,
            transformOrigin: "left center",
            opacity: Math.min(1, big * 2),
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {pct} %
        </div>
        <div
          style={{
            display: "inline-block",
            fontWeight: 900,
            fontSize: 84,
            letterSpacing: -3.5,
            background: C.ink,
            color: C.green,
            borderRadius: 22,
            padding: "2px 26px 12px",
            transform: `scaleX(${ease(frame, anuncios - 4, 10)})`,
            transformOrigin: "left",
          }}
        >
          de los anuncios.
        </div>
      </div>

      <div style={{ position: "absolute", top: 900, left: 60, right: 60, display: "flex", flexDirection: "column", gap: 26 }}>
        <Notification
          frame={frame}
          at={8}
          tilt={-0.4}
          when="1 h"
          icon={svg("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")}
          text="Tu video «Receta de 15 minutos» ya suma 10 mil vistas 🎉"
        />
        <Notification
          frame={frame}
          at={anuncios + 4}
          tilt={0.6}
          when="ahora"
          icon={svg("M12 2v20M17 6H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6")}
          text="Las ganancias del mes ya están en tu saldo"
        />
        <Notification
          frame={frame}
          at={paypal - 2}
          tilt={-0.6}
          when="ahora"
          icon={svg("M4 12l5 5L20 6")}
          text="Tu pago por PayPal está en camino"
        />
      </div>
    </AbsoluteFill>
  );
};
