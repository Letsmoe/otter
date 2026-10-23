import type { PluginContext, PluginDefinition } from "./definition.ts";
import { extract } from "./extract.ts";
import { hosters } from "./hosters/index.ts";
import { createHttpClient } from "./http-client.ts";
import { match } from "./match.ts";
import type { List } from "./model.ts";

/** Env var through which the backend passes the user's override of base_url. */
export const BASE_URL_ENV_KEY = "OTTER_BASE_URL";

export interface ContextOptions {
  /** Replaces the global fetch, for tests. */
  fetch?: typeof fetch;
  /** Replaces process.env, for tests. */
  env?: Record<string, string | undefined>;
}

/** The user's override of base_url if set, else the declared one; without trailing slash. */
export function effectiveBaseUrl(
  definition: PluginDefinition,
  env: Record<string, string | undefined>,
): string | undefined {
  const override = (env[BASE_URL_ENV_KEY] || "").trim();
  const chosen = override || definition.base_url;
  if (!chosen) return undefined;
  return chosen.replace(/\/+$/, "");
}

function settingsOf(
  definition: PluginDefinition,
  env: Record<string, string | undefined>,
): Record<string, string> {
  const settings: Record<string, string> = {};
  for (const field of definition.settings || []) {
    const value = env[field.key] || field.default;
    if (value !== undefined) settings[field.key] = value;
  }
  return settings;
}

function describe(data: unknown): string {
  if (data === undefined) return "";
  try {
    return " " + JSON.stringify(data);
  } catch {
    return " " + String(data);
  }
}

export function createContext(definition: PluginDefinition, options: ContextOptions = {}): PluginContext {
  const env = options.env || process.env;
  const baseUrl = effectiveBaseUrl(definition, env);
  const languages = definition.languages || [];
  return {
    plugin: {
      id: definition.id,
      name: definition.name,
      version: definition.version,
      base_url: baseUrl,
      languages,
    },
    http: createHttpClient({ baseUrl, languages, fetch: options.fetch }),
    match,
    extract,
    hosters,
    settings: settingsOf(definition, env),
    log(message: string, data?: unknown): void {
      console.error(`[${definition.id}] ${message}${describe(data)}`);
    },
    list<Item>(items: Item[]): List<Item> {
      return { object: "list", data: items };
    },
  };
}
