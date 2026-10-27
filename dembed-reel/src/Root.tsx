import React from "react";
import { Composition } from "remotion";
import { HEIGHT, Reel, WIDTH, reelFrames } from "./Reel";
import { FPS } from "./theme";

export const Root: React.FC = () => (
  <Composition id="DembedReel" component={Reel} durationInFrames={reelFrames()} fps={FPS} width={WIDTH} height={HEIGHT} />
);
