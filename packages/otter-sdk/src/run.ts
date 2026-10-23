import { writeSync } from "fs";
import type { PluginDefinition } from "./definition.ts";
import { OtterError } from "./errors.ts";
import { handlersOf, isPluginDefinition, type PluginHandlers } from "./runtime.ts";
import type { PluginMeta, ResolveResult, StreamsArgs, StreamsResult } from "./types.ts";

export type { PluginHandlers };

interface Invocation {
  capability: string;
  args?: Record<string, unknown>;
}

function respond<Result>(result: Result): never {
  writeSync(1, JSON.stringify({ result }) + "\n");
  process.exit(0);
}

function fail(message: string, code?: string): never {
  writeSync(1, JSON.stringify({ error: message, code }) + "\n");
  process.exit(0);
}

async function readInvocation(): Promise<Invocation> {
  // The backend's embedded runtime provides Bun.stdin as well.
  const raw = await Bun.stdin.text();
  try {
    return JSON.parse(raw);
  } catch {
    fail("invalid JSON on stdin");
  }
}

async function dispatch(handlers: PluginHandlers, input: Invocation): Promise<never> {
  if (input.capability === "meta") respond<PluginMeta>(handlers.meta);

  if (input.capability === "streams") {
    if (!handlers.streams) fail("streams not implemented");
    respond<StreamsResult>(await handlers.streams(input.args as unknown as StreamsArgs));
  }

  if (input.capability === "resolve") {
    if (!handlers.resolve) fail("resolve not implemented");
    const url = input.args?.url;
    if (!url) fail("args.url required for resolve");
    respond<ResolveResult>(await handlers.resolve(String(url)));
  }

  fail(`unsupported capability: ${input.capability}`);
}

function failWith(error: unknown): never {
  if (error instanceof OtterError) fail(error.message, error.code);
  fail(error instanceof Error ? error.message : String(error));
}

/**
 * Entry point of every plugin: reads one request from stdin and answers on
 * stdout. Takes a `definePlugin` definition, or the older `{ meta, streams, resolve }` handlers.
 */
export async function runPlugin(plugin: PluginDefinition | PluginHandlers): Promise<never> {
  const input = await readInvocation();
  try {
    const handlers = isPluginDefinition(plugin) ? handlersOf(plugin) : plugin;
    return await dispatch(handlers, input);
  } catch (error) {
    failWith(error);
  }
}
