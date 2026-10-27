import React from "react";
import { AbsoluteFill } from "remotion";
import { TransitionPresentation, TransitionSeries, springTiming } from "@remotion/transitions";
import { iris } from "@remotion/transitions/iris";
import { slide } from "@remotion/transitions/slide";
import { wipe } from "@remotion/transitions/wipe";
import { loadFonts } from "./fonts";
import { ChatScene, chatSceneFrames } from "./scenes/ChatScene";
import { CtaScene, ctaSceneFrames } from "./scenes/CtaScene";
import { EarningsScene, earningsSceneFrames } from "./scenes/EarningsScene";
import { PanelScene, panelSceneFrames } from "./scenes/PanelScene";
import { RequirementsScene, requirementsSceneFrames } from "./scenes/RequirementsScene";
import { TRANSITION } from "./timing";

loadFonts();

export const WIDTH = 1080;
export const HEIGHT = 1920;

const timing = springTiming({ config: { damping: 200 }, durationInFrames: TRANSITION });

const scenes = [
  { Comp: ChatScene, frames: chatSceneFrames },
  { Comp: RequirementsScene, frames: requirementsSceneFrames },
  { Comp: PanelScene, frames: panelSceneFrames },
  { Comp: EarningsScene, frames: earningsSceneFrames },
  { Comp: CtaScene, frames: ctaSceneFrames },
];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const transitions: TransitionPresentation<any>[] = [
  iris({ width: WIDTH, height: HEIGHT }),
  slide({ direction: "from-bottom" }),
  wipe({ direction: "from-right" }),
  iris({ width: WIDTH, height: HEIGHT }),
];

export const reelFrames = () =>
  scenes.reduce((sum, s) => sum + s.frames(), 0) - transitions.length * TRANSITION;

export const Reel: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: "#18191D" }}>
    <TransitionSeries>
      {scenes.map(({ Comp, frames }, i) => (
        <React.Fragment key={i}>
          <TransitionSeries.Sequence durationInFrames={frames()}>
            <Comp />
          </TransitionSeries.Sequence>
          {transitions[i] ? <TransitionSeries.Transition presentation={transitions[i]} timing={timing} /> : null}
        </React.Fragment>
      ))}
    </TransitionSeries>
  </AbsoluteFill>
);
