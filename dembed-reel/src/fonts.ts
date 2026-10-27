import { continueRender, delayRender, staticFile } from "remotion";

const faces: [string, string, string][] = [
  ["Inter", "Inter-Medium.otf", "500"],
  ["Inter", "Inter-SemiBold.otf", "600"],
  ["Inter", "Inter-Bold.otf", "700"],
  ["Inter", "Inter-ExtraBold.otf", "800"],
  ["Inter Display", "InterDisplay-ExtraBold.otf", "800"],
  ["Inter Display", "InterDisplay-Black.otf", "900"],
];

let loaded = false;

export const loadFonts = () => {
  if (loaded || typeof document === "undefined") return;
  loaded = true;
  const handle = delayRender("Cargando fuentes Inter");
  Promise.all(
    faces.map(async ([family, file, weight]) => {
      const face = new FontFace(family, `url(${staticFile(`fonts/${file}`)})`, { weight });
      await face.load();
      document.fonts.add(face);
    }),
  ).then(() => continueRender(handle));
};
