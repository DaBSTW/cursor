import React from "react";
import { AbsoluteFill, Audio, Sequence, interpolate, staticFile, useCurrentFrame } from "remotion";
import { ease, pop } from "../anim";
import { Backdrop } from "../components/Backdrop";
import { Kinetic } from "../components/Kinetic";
import { Logo } from "../components/Logo";
import { Sfx } from "../components/Sfx";
import { C, FONT } from "../theme";
import { VOICE_LEAD, blockFrames, line, wordAt } from "../timing";

export const chatSceneFrames = () => blockFrames("hook") + blockFrames("chat");

type Msg = { from: "them" | "me"; at: number; time: string; content: React.ReactNode };

const Typing: React.FC<{ frame: number; me: boolean }> = ({ frame, me }) => (
  <div style={{ display: "flex", gap: 14, padding: "42px 48px" }}>
    {[0, 1, 2].map((i) => (
      <div
        key={i}
        style={{
          width: 18,
          height: 18,
          borderRadius: 9,
          background: me ? C.green : C.gray,
          transform: `translateY(${Math.sin((frame - i * 5) / 4) * -7}px)`,
        }}
      />
    ))}
  </div>
);

const Bubble: React.FC<{ msg: Msg; frame: number }> = ({ msg, frame }) => {
  const me = msg.from === "me";
  const typingFrom = msg.at - 16;
  if (frame < typingFrom) return null;
  const typing = frame < msg.at;
  const p = pop(frame, typingFrom, 13);
  const reveal = ease(frame, msg.at, 8);
  return (
    <div
      style={{
        alignSelf: me ? "flex-end" : "flex-start",
        maxWidth: me ? 900 : 880,
        background: me ? C.ink : C.white,
        color: me ? C.white : C.ink,
        borderRadius: 40,
        borderBottomLeftRadius: me ? 40 : 10,
        borderBottomRightRadius: me ? 10 : 40,
        boxShadow: "0 18px 40px rgba(0,0,0,0.12)",
        transform: `scale(${0.6 + p * 0.4}) translateY(${(1 - p) * 40}px)`,
        transformOrigin: me ? "bottom right" : "bottom left",
        opacity: Math.min(1, p * 2),
      }}
    >
      {typing ? (
        <Typing frame={frame} me={me} />
      ) : (
        <div style={{ padding: "32px 42px 22px", opacity: reveal }}>
          <div style={{ fontFamily: FONT, fontWeight: 700, fontSize: 54, lineHeight: 1.2, letterSpacing: -0.6 }}>
            {msg.content}
          </div>
          <div
            style={{
              fontFamily: FONT,
              fontWeight: 600,
              fontSize: 28,
              textAlign: "right",
              marginTop: 6,
              color: me ? C.green : C.gray,
            }}
          >
            {msg.time} {me ? "✓✓" : ""}
          </div>
        </div>
      )}
    </div>
  );
};

const G: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <span style={{ color: C.green }}>{children}</span>
);

export const ChatScene: React.FC = () => {
  const frame = useCurrentFrame();
  const chatStart = blockFrames("hook");
  const hook = (w: string) => wordAt("hook", w);
  const chat = (w: string) => chatStart + wordAt("chat", w);

  const msgs: Msg[] = [
    { from: "them", at: hook("Dónde"), time: "18:02", content: <>Oye, ¿dónde puedo subir mis videos gratis? 🤔</> },
    {
      from: "me",
      at: chat("En"),
      time: "18:02",
      content: (
        <>
          En <G>Dembed</G>. Es gratis y encima ganas con tus vistas 💸
        </>
      ),
    },
    { from: "them", at: chat("vistas") - 4, time: "18:03", content: <>¿Sin tener 1.000 suscriptores? 😳</> },
    {
      from: "me",
      at: chat("desde"),
      time: "18:03",
      content: (
        <>
          Desde el primer video. Te llevas el <G>30 %</G> de los anuncios ✅
        </>
      ),
    },
    { from: "them", at: chat("video") + 14, time: "18:03", content: <>Pásame el link YA 👀🔥</> },
  ];

  // Cuando la conversación crece, sube la pila y el titular se va.
  const scroll =
    interpolate(ease(frame, chat("vistas") - 20, 16), [0, 1], [0, 200]) +
    interpolate(ease(frame, chat("desde") - 18, 16), [0, 1], [0, 160]) +
    interpolate(ease(frame, chat("video") - 2, 14), [0, 1], [0, 100]);
  const headlineOut = ease(frame, chat("vistas") - 20, 14);

  return (
    <AbsoluteFill>
      <Backdrop bg={C.green} ring={C.greenDeep} ring2={C.greenSoft} />
      <Sequence from={VOICE_LEAD} layout="none">
        <Audio src={staticFile(line("hook").file)} />
      </Sequence>
      <Sequence from={chatStart + VOICE_LEAD} layout="none">
        <Audio src={staticFile(line("chat").file)} />
      </Sequence>
      {msgs.map((m, i) => (
        <Sfx key={i} at={m.at} name="pop" volume={0.35} />
      ))}

      <div
        style={{
          position: "absolute",
          top: 150,
          left: 72,
          right: 72,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          opacity: ease(frame, 0, 10),
        }}
      >
        <Logo size={70} />
        <div style={{ fontFamily: FONT, fontWeight: 800, fontSize: 32, color: "#127A3E" }}>
          La conversación de todo creador
        </div>
      </div>

      <div style={{ position: "absolute", top: 1, left: 0, right: 0, transform: `translateY(${-scroll}px)` }}>
        <Kinetic
          size={128}
          color={C.ink}
          style={{
            position: "absolute",
            top: 330,
            left: 72,
            right: 30,
            opacity: 1 - headlineOut,
            filter: `blur(${headlineOut * 8}px)`,
          }}
          words={["«¿Y", "dónde", "subo", "mis", "videos?»"].map((text, i) => ({ text, at: 2 + i * 3 }))}
        />
        <div
          style={{
            position: "absolute",
            top: 760,
            left: 60,
            right: 60,
            display: "flex",
            flexDirection: "column",
            gap: 30,
          }}
        >
          {msgs.map((m, i) => (
            <Bubble key={i} msg={m} frame={frame} />
          ))}
        </div>
      </div>
    </AbsoluteFill>
  );
};
