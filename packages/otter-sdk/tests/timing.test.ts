import { describe, expect, test } from "bun:test";
import { definePlugin } from "../src/definition.ts";
import { handlersOf } from "../src/runtime.ts";

describe("resolve timing", () => {
  test("adds the load time and leaves speed to the backend", async () => {
    const plugin = definePlugin({
      id: "timed",
      name: "Timed",
      version: "0.1.0",
      sources: { domains: ["host.test"], retrieve: async () => ({ url: "https://cdn.test/video.mp4", mime_type: "video/mp4" }) },
    });

    const result = await handlersOf(plugin).resolve!("https://host.test/e/1");

    expect(result.url).toBe("https://cdn.test/video.mp4");
    expect(result.load_time_ms).toBeGreaterThanOrEqual(0);
    expect(result.speed_bps).toBeUndefined();
  });
});
