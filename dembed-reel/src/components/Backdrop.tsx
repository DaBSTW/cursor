import React from "react";
import { AbsoluteFill, useCurrentFrame } from "remotion";

/** Fondo plano con los dos arcos grandes de las piezas, girando lento. */
export const Backdrop: React.FC<{ bg: string; ring: string; ring2?: string; seed?: number }> = ({
  bg,
  ring,
  ring2,
  seed = 0,
}) => {
  const frame = useCurrentFrame() + seed;
  const a = frame * 0.12;
  return (
    <AbsoluteFill style={{ backgroundColor: bg, overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          width: 1500,
          height: 1500,
          left: 420,
          top: -560,
          borderRadius: "50%",
          border: `110px solid ${ring}`,
          transform: `rotate(${a}deg) translateX(${Math.sin(frame / 60) * 18}px)`,
        }}
      />
      <div
        style={{
          position: "absolute",
          width: 1300,
          height: 1300,
          left: -820,
          top: 1180,
          borderRadius: "50%",
          border: `110px solid ${ring2 ?? ring}`,
          transform: `translateY(${Math.cos(frame / 70) * 20}px)`,
        }}
      />
    </AbsoluteFill>
  );
};
