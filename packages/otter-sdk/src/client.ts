// Typed GraphQL client over the Otter API (genql, generated from the
// backend schema by `bun run codegen`).
//
//   const otter = createOtterClient({ url: "http://localhost:8080/graphql" });
//   const { title } = await otter.query({ title: { __args: { id: "movie:603" }, title: true } });

import { createClient, type Client } from "./generated/index";

export interface OtterClientOptions {
  /** GraphQL endpoint, e.g. http://localhost:8080/graphql */
  url: string;
  headers?: Record<string, string>;
  /** Custom fetch (defaults to global fetch). */
  fetch?: typeof fetch;
}

export type OtterClient = Client;

export function createOtterClient(options: OtterClientOptions): OtterClient {
  return createClient({ url: options.url, headers: options.headers, fetch: options.fetch });
}

export * from "./generated/index";
