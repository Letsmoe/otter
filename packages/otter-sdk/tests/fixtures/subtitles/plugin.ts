import { definePlugin } from "../../../src/plugin.ts";

export default definePlugin({
  id: "subtitles-sample",
  name: "Subtitles sample",
  version: "0.0.1",
  subtitles: {
    async list(params, ctx) {
      const wanted = params.languages;
      const files = [
        { url: "https://subs.example/de.srt", language: "de", release: "Dark.S01E02" },
        { url: "https://subs.example/en.vtt", language: "en", format: "vtt" as const, hearing_impaired: true },
      ];
      return ctx.list(files.filter((file) => wanted.length === 0 || wanted.includes(file.language)));
    },
  },
});
