import { PluginSourceError } from "../errors.ts";
import type { PluginContext } from "../definition.ts";
import type { SourceInput } from "../model.ts";
import { originOf } from "../url.ts";
import { throwForApiFailure } from "./dead-link.ts";
import { defineHoster } from "./registry.ts";

interface StreamResponse {
  streaming_url?: string;
}

function filecodeOf(url: string): string {
  const segments = new URL(url).pathname.split("/").filter(Boolean);
  const filecode = segments[segments.length - 1];
  if (!filecode) {
    throw new PluginSourceError("extraction_failed", `Vidara: no file code in ${url}`);
  }
  return filecode;
}

async function retrieveVidara(url: string, ctx: PluginContext): Promise<SourceInput> {
  const filecode = filecodeOf(url);
  const origin = originOf(url);
  const playerUrl = `${origin}/e/${filecode}`;

  const response = await ctx.http.post(`${origin}/api/stream`, {
    json: { filecode, device: "web" },
    headers: { Referer: playerUrl, Origin: origin },
    allow_error_status: true,
  });
  const body = await response.text();
  if (response.status >= 400) throwForApiFailure(response.url || `${origin}/api/stream`, response.status, body);
  const data = JSON.parse(body) as StreamResponse;
  if (!data.streaming_url) {
    throw new PluginSourceError("extraction_failed", `Vidara: no streaming_url for ${url}`);
  }
  return {
    url: data.streaming_url,
    mime_type: ctx.extract.mimeType(data.streaming_url, "application/x-mpegURL"),
    headers: { Referer: playerUrl, Origin: origin },
  };
}

export const vidara = defineHoster({
  name: "Vidara",
  domains: ["vidara.to", "vidara.so", "vidaraa.cc", "odysseusa.cc"],
  retrieve: retrieveVidara,
});
