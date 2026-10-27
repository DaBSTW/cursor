import React from "react";
import { C, FONT } from "../theme";

/** Marca "dembed": la panza de la "d" es un círculo con un play calado. */
export const Logo: React.FC<{ size?: number; color?: string; accent?: string }> = ({
  size = 72,
  color = C.ink,
  accent,
}) => {
  const id = React.useId().replace(/:/g, "");
  const fill = accent ?? color;
  return (
    <div style={{ display: "flex", alignItems: "flex-end", lineHeight: 1 }}>
      <svg width={size * 0.8} height={size * 1.04} viewBox="0 0 80 104" style={{ marginBottom: size * 0.035 }}>
        <defs>
          <mask id={id}>
            <rect width="80" height="104" fill="white" />
            <path d="M30 51 C30 47 34 45 37 47 L50 55.5 C53 57.5 53 61.5 50 63.5 L37 72 C34 74 30 72 30 68 Z" fill="black" />
          </mask>
        </defs>
        <g mask={`url(#${id})`} fill={fill}>
          <circle cx="38" cy="62" r="38" />
          <rect x="58" y="0" width="22" height="100" rx="5" />
        </g>
      </svg>
      <span
        style={{
          fontFamily: FONT,
          fontWeight: 800,
          fontSize: size * 0.98,
          letterSpacing: -size * 0.045,
          color,
          marginLeft: size * 0.01,
        }}
      >
        embed
      </span>
    </div>
  );
};
