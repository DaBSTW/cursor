import React from "react";
import { useCurrentFrame } from "remotion";
import { pop } from "../anim";
import { DISPLAY } from "../theme";

/** Titular que entra palabra por palabra desde abajo con un leve giro. */
export const Kinetic: React.FC<{
  words: { text: string; at: number; color?: string; bg?: string }[];
  size: number;
  color: string;
  lineHeight?: number;
  style?: React.CSSProperties;
}> = ({ words, size, color, lineHeight = 0.98, style }) => {
  const frame = useCurrentFrame();
  return (
    <div
      style={{
        fontFamily: DISPLAY,
        fontWeight: 900,
        fontSize: size,
        letterSpacing: -size * 0.055,
        lineHeight,
        color,
        display: "flex",
        flexWrap: "wrap",
        columnGap: size * 0.22,
        ...style,
      }}
    >
      {words.map((w, i) => {
        const p = pop(frame, w.at, 13);
        return (
          <span key={i} style={{ display: "inline-block", overflow: "visible" }}>
            <span
              style={{
                display: "inline-block",
                opacity: Math.min(1, p * 1.6),
                transform: `translateY(${(1 - p) * size * 0.7}px) rotate(${(1 - p) * 6}deg)`,
                color: w.color ?? color,
                background: w.bg,
                borderRadius: w.bg ? size * 0.2 : 0,
                padding: w.bg ? `0 ${size * 0.14}px ${size * 0.08}px` : 0,
              }}
            >
              {w.text}
            </span>
          </span>
        );
      })}
    </div>
  );
};
