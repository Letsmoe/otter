import type { List, PluginContext, StreamInput, StreamListParams } from "@neoworks-dev/otter-sdk";
import { LABEL, LANGUAGE } from "./constants.ts";
import { lookupUrlOf, targetOf } from "./target.ts";

/** Emits the lookup link only; the stream itself is resolved at play time. */
export async function listStreams({ media }: StreamListParams, ctx: PluginContext): Promise<List<StreamInput>> {
  const target = targetOf(media);
  if (!target) return ctx.list<StreamInput>([]);
  return ctx.list<StreamInput>([{ url: lookupUrlOf(target, ctx), label: LABEL, language: LANGUAGE }]);
}
