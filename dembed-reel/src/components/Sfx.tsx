import React from "react";
import { Audio, Sequence, staticFile } from "remotion";

export const Sfx: React.FC<{ at: number; name: "pop" | "whoosh" | "ding"; volume?: number }> = ({
  at,
  name,
  volume = 0.5,
}) => (
  <Sequence from={Math.max(0, at)} durationInFrames={30} layout="none">
    <Audio src={staticFile(`sfx/${name}.wav`)} volume={volume} />
  </Sequence>
);
