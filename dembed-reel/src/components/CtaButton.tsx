import React from "react";
import { FONT } from "../theme";

export const CtaButton: React.FC<{
  label: React.ReactNode;
  bg: string;
  color: string;
  size?: number;
  style?: React.CSSProperties;
}> = ({ label, bg, color, size = 50, style }) => (
  <div
    style={{
      display: "inline-flex",
      alignItems: "center",
      gap: size * 0.6,
      background: bg,
      color,
      fontFamily: FONT,
      fontWeight: 800,
      fontSize: size,
      letterSpacing: -size * 0.02,
      padding: `${size * 0.62}px ${size}px`,
      borderRadius: size * 0.42,
      ...style,
    }}
  >
    {label}
    <svg width={size * 0.8} height={size * 0.8} viewBox="0 0 24 24">
      <path d="M4 12h15M13 5l7 7-7 7" fill="none" stroke={color} strokeWidth="2.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  </div>
);
