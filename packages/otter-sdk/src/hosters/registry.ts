import type { PluginContext } from "../definition.ts";
import { domainOf } from "../http.ts";
import type { SourceInput } from "../model.ts";

export interface HosterDefinition {
  /** Shown as the label of streams that point at this hoster. */
  name: string;
  domains: string[];
  /** Turns a link on one of the domains into a playable media URL. */
  retrieve(url: string, ctx: PluginContext): Promise<SourceInput>;
}

export function defineHoster(hoster: HosterDefinition): HosterDefinition {
  return hoster;
}

function servesHost(hoster: HosterDefinition, host: string): boolean {
  return hoster.domains.some((domain) => host === domain || host.endsWith("." + domain));
}

/** The hosters a plugin can turn links into media URLs with. */
export class HosterRegistry {
  private readonly hosters: HosterDefinition[];

  /** `fallback` handles links no registered hoster serves. */
  constructor(
    hosters: HosterDefinition[] = [],
    private readonly fallback?: HosterDefinition,
  ) {
    this.hosters = [...hosters];
  }

  register(hoster: HosterDefinition): void {
    this.hosters.push(hoster);
  }

  find(url: string): HosterDefinition | undefined {
    const host = domainOf(url);
    if (host === "") return undefined;
    return this.hosters.find((hoster) => servesHost(hoster, host));
  }

  supports(url: string): boolean {
    return this.find(url) !== undefined;
  }

  nameOf(url: string): string | undefined {
    const hoster = this.find(url);
    if (!hoster) return undefined;
    return hoster.name;
  }

  /** Every domain a registered hoster serves. */
  domains(): string[] {
    return this.hosters.flatMap((hoster) => hoster.domains);
  }

  /**
   * Retrieves the source with the hoster serving the link. Site links like
   * aniworld.to/redirect/<id> redirect to a hoster, so an unknown link is
   * followed first; only then does the fallback get it.
   */
  async retrieve(url: string, ctx: PluginContext): Promise<SourceInput> {
    const hoster = this.find(url);
    if (hoster) return hoster.retrieve(url, ctx);

    const destination = await ctx.http.finalUrl(url);
    const destinationHoster = this.find(destination);
    if (destinationHoster) return destinationHoster.retrieve(destination, ctx);

    if (!this.fallback) throw new Error(`no hoster serves ${url}`);
    return this.fallback.retrieve(destination, ctx);
  }
}
