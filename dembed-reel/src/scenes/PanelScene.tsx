import React from "react";
import { AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame } from "remotion";
import { ease, pop } from "../anim";
import { Backdrop } from "../components/Backdrop";
import { Kinetic } from "../components/Kinetic";
import { Logo } from "../components/Logo";
import { Sfx } from "../components/Sfx";
import { C, DISPLAY, FONT } from "../theme";
import { VOICE_LEAD, blockFrames, line, wordAt } from "../timing";

export const panelSceneFrames = () => blockFrames("panel") + 6;

const D = { bg: "#1E2026", panel: "#272A31", line: "#33363E", text: "#E9EBEF", mute: "#8D919B" };

const videos = [
  { tag: "Q&A", title: "Q&A: respondo vuestras pr…", dur: "27:58", views: 3410, grad: "linear-gradient(120deg,#E0435F,#D9466C 45%,#6C4BF0)" },
  { tag: "VLOG", title: "Vlog: así trabajo desde casa", dur: "16:52", views: 7640, grad: "linear-gradient(120deg,#5B3C8F,#3C4C9A)" },
  { tag: "DALGONA\n60 s", title: "Café dalgona en 60 segund…", dur: "1:00", views: 28800, grad: "linear-gradient(120deg,#E2C49C,#B98F6A)" },
];

const Sidebar: React.FC<{ active: number }> = ({ active }) => {
  const items = ["Resumen", "Monetización", "Reproductor", "Premium"];
  return (
    <div style={{ width: 250, padding: "26px 20px", display: "flex", flexDirection: "column", gap: 10 }}>
      <Logo size={28} color={D.text} accent={C.green} />
      <div
        style={{
          marginTop: 20,
          background: C.green,
          color: C.ink,
          borderRadius: 12,
          padding: "14px 0",
          textAlign: "center",
          fontWeight: 800,
          fontSize: 19,
        }}
      >
        ⇪ Subir videos
      </div>
      <div style={{ marginTop: 10, background: D.panel, borderRadius: 14, padding: 10 }}>
        {items.map((it) => (
          <div key={it} style={{ color: D.text, fontSize: 18, fontWeight: 600, padding: "10px 12px" }}>
            {it}
          </div>
        ))}
      </div>
      <div style={{ color: D.mute, fontSize: 14, fontWeight: 700, margin: "12px 12px 0" }}>BIBLIOTECA</div>
      {["Todos los videos", "Shorts", "Tutoriales"].map((it, i) => (
        <div
          key={it}
          style={{
            display: "flex",
            justifyContent: "space-between",
            padding: "10px 12px",
            borderRadius: 10,
            fontSize: 18,
            fontWeight: 600,
            color: i === active ? C.green : D.text,
            background: i === active ? "rgba(31,216,108,0.14)" : "transparent",
          }}
        >
          <span>📁 {it}</span>
          <span style={{ color: D.mute }}>{[17, 6, 4][i]}</span>
        </div>
      ))}
    </div>
  );
};

const VideoCard: React.FC<{ v: (typeof videos)[number]; frame: number; at: number; statsAt: number }> = ({
  v,
  frame,
  at,
  statsAt,
}) => {
  const p = pop(frame, at, 13);
  const n = Math.round(v.views * ease(frame, statsAt, 26));
  return (
    <div
      style={{
        width: 300,
        background: D.panel,
        borderRadius: 18,
        overflow: "hidden",
        transform: `translateY(${(1 - p) * 60}px) scale(${0.85 + p * 0.15})`,
        opacity: Math.min(1, p * 2),
      }}
    >
      <div style={{ height: 170, background: v.grad, position: "relative", padding: 20 }}>
        <div style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 32, color: "white", whiteSpace: "pre", lineHeight: 1 }}>
          {v.tag}
        </div>
        <div
          style={{
            position: "absolute",
            right: 12,
            bottom: 12,
            background: "rgba(0,0,0,0.7)",
            color: "white",
            fontSize: 14,
            fontWeight: 700,
            padding: "3px 8px",
            borderRadius: 6,
          }}
        >
          {v.dur}
        </div>
      </div>
      <div style={{ padding: "16px 18px" }}>
        <div style={{ color: D.text, fontWeight: 700, fontSize: 18, whiteSpace: "nowrap" }}>{v.title}</div>
        <div style={{ color: D.mute, fontWeight: 600, fontSize: 15, marginTop: 8, fontVariantNumeric: "tabular-nums" }}>
          👁 {n.toLocaleString("es-ES")} &nbsp; ⤓ {Math.round(n / 22)}
        </div>
      </div>
    </div>
  );
};

const Icon: React.FC<{ d: string }> = ({ d }) => (
  <svg width="44" height="44" viewBox="0 0 24 24">
    <path d={d} fill="none" stroke={C.green} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

const icons = {
  folder: "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
  chart: "M4 20V10M10 20V4M16 20v-7M22 20H2",
  link: "M10 14a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1M14 10a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1",
  code: "M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16",
};

const Feature: React.FC<{ label: string; icon: string; at: number; frame: number }> = ({ label, icon, at, frame }) => {
  const p = pop(frame, at, 11);
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 18,
        background: C.ink,
        color: C.white,
        borderRadius: 28,
        padding: "22px 30px",
        fontFamily: FONT,
        fontWeight: 800,
        fontSize: 40,
        transform: `scale(${p}) rotate(${(1 - p) * -8}deg)`,
        opacity: Math.min(1, p * 2),
        boxShadow: "0 20px 40px rgba(0,0,0,0.18)",
      }}
    >
      <Icon d={icon} />
      {label}
    </div>
  );
};

export const PanelScene: React.FC = () => {
  const frame = useCurrentFrame();
  const w = (x: string) => wordAt("panel", x);
  const enter = pop(frame, 4, 16, 1);
  const tilt = interpolate(frame, [0, 200], [-16, -9]);
  const features = [
    { label: "Carpetas", icon: icons.folder, at: w("carpetas") },
    { label: "Estadísticas", icon: icons.chart, at: w("estadísticas") },
    { label: "Enlaces", icon: icons.link, at: w("enlaces") },
    { label: "Iframe para tu web", icon: icons.code, at: w("iframe") },
  ];
  const active = frame >= w("carpetas") + 6 && frame < w("estadísticas") ? 1 : 0;

  return (
    <AbsoluteFill>
      <Backdrop bg={C.green} ring={C.greenDeep} ring2={C.greenSoft} seed={400} />
      <Sequence from={VOICE_LEAD} layout="none">
        <Audio src={staticFile(line("panel").file)} />
      </Sequence>
      {features.map((f) => (
        <Sfx key={f.label} at={f.at} name="pop" volume={0.3} />
      ))}

      <div style={{ position: "absolute", top: 150, left: 72 }}>
        <Logo size={70} />
      </div>

      <Kinetic
        size={132}
        color={C.ink}
        style={{ position: "absolute", top: 280, left: 72, right: 40 }}
        words={[
          { text: "Todo,", at: w("Todo") - 2 },
          { text: "en", at: w("en") - 2 },
          { text: "tu propio panel.", at: w("tu") - 1, color: C.green, bg: C.ink },
        ]}
      />

      <div style={{ position: "absolute", top: 700, left: 60, perspective: 1800, transform: "scale(1.12)", transformOrigin: "top left" }}>
        <div
          style={{
            width: 1260,
            height: 620,
            background: D.bg,
            borderRadius: 30,
            overflow: "hidden",
            fontFamily: FONT,
            boxShadow: "0 50px 90px rgba(0,0,0,0.28)",
            transform: `translateX(${(1 - enter) * 900}px) rotateY(${tilt}deg) rotateX(6deg) rotateZ(-2deg)`,
            transformOrigin: "left center",
          }}
        >
          <div style={{ display: "flex", gap: 10, padding: "18px 22px", borderBottom: `1px solid ${D.line}` }}>
            {[0, 1, 2].map((i) => (
              <div key={i} style={{ width: 14, height: 14, borderRadius: 7, background: "#4A4D55" }} />
            ))}
          </div>
          <div style={{ display: "flex" }}>
            <Sidebar active={active} />
            <div style={{ flex: 1, padding: "26px 10px" }}>
              <div style={{ color: D.text, fontSize: 32, fontWeight: 800 }}>Todos los videos</div>
              <div style={{ color: D.mute, fontSize: 16, fontWeight: 600, marginTop: 4 }}>17 videos</div>
              <div
                style={{
                  marginTop: 16,
                  width: 900,
                  background: D.panel,
                  borderRadius: 12,
                  padding: "12px 18px",
                  color: D.mute,
                  fontSize: 16,
                }}
              >
                🔍 Buscar videos…
              </div>
              <div style={{ display: "flex", gap: 22, marginTop: 22 }}>
                {videos.map((v, i) => (
                  <VideoCard key={v.tag} v={v} frame={frame} at={10 + i * 5} statsAt={w("estadísticas")} />
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div
        style={{
          position: "absolute",
          top: 1420,
          left: 72,
          right: 72,
          display: "flex",
          flexWrap: "wrap",
          gap: 20,
        }}
      >
        {features.map((f) => (
          <Feature key={f.label} {...f} frame={frame} />
        ))}
      </div>
    </AbsoluteFill>
  );
};
