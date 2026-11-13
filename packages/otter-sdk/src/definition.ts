import type { HosterRegistry } from "./hosters/registry.ts";
import type { Extractor } from "./extract.ts";
import type { HttpClient } from "./http-client.ts";
import type { Matcher } from "./match.ts";
import type {
  List,
  MediaKind,
  SourceInput,
  SourceRetrieveParams,
  StreamInput,
  StreamListParams,
  SubtitleInput,
  SubtitleListParams,
} from "./model.ts";
import type { LoginArgs, LoginResult, PluginSettingField } from "./types.ts";

/** Everything a handler needs, so plugins don't carry their own copies of these helpers. */
export interface PluginContext {
  plugin: {
    id: string;
    name: string;
    version: string;
    /** The effective base URL: the user's override, else the declared one. */
    base_url?: string;
    languages: string[];
  };
  http: HttpClient;
  match: Matcher;
  extract: Extractor;
  hosters: HosterRegistry;
  /** Values of the settings the plugin declares (default applied), by key. */
  settings: Record<string, string>;
  /** The user's login, for plugins that declare `account`; undefined until they logged in. */
  account?: {
    username: string;
    /** Whatever the plugin's `login` returned, e.g. the site's cookies. */
    session: string;
  };
  /** Writes to stderr, prefixed with the plugin id. */
  log(message: string, data?: unknown): void;
  list<Item>(items: Item[]): List<Item>;
}

export interface StreamsCapability {
  /** Finds the links to a movie or episode on the site. Not finding it is an empty list, not an error. */
  list(params: StreamListParams, ctx: PluginContext): Promise<List<StreamInput>>;
}

export interface SubtitlesCapability {
  /** Finds caption files for a movie or episode. Finding none is an empty list, not an error. */
  list(params: SubtitleListParams, ctx: PluginContext): Promise<List<SubtitleInput>>;
}

export interface SourcesCapability {
  /** Hostnames of the links this plugin can turn into media URLs. */
  domains: string[];
  /** Also handle links on any other domain. */
  fallback?: boolean;
  retrieve(params: SourceRetrieveParams, ctx: PluginContext): Promise<SourceInput>;
}

export interface AccountCapability {
  /** Where users without an account can make one. */
  signup_url?: string;
  /**
   * Logs in with the user's credentials and returns a session string the
   * plugin can use later (ctx.account.session). Throw PluginLoginFailedError
   * when the site refuses them; other handlers throw PluginLoggedOutError
   * when the site no longer accepts the session.
   */
  login(credentials: LoginArgs, ctx: PluginContext): Promise<LoginResult>;
}

export interface PluginDefinition {
  /** Stable identifier; the settings key and the folder name. */
  id: string;
  /** Display name. */
  name: string;
  version: string;
  description?: string;
  /** Emoji or URL. */
  icon?: string;
  /** The site's address. The user can point it at a mirror in the plugin's settings. */
  base_url?: string;
  /** Languages of the site's content, most important first: "de", "en", ... */
  languages?: string[];
  media?: MediaKind[];
  settings?: PluginSettingField[];
  /** The site only works through FlareSolverr. */
  requires_solver?: boolean;
  streams?: StreamsCapability;
  sources?: SourcesCapability;
  subtitles?: SubtitlesCapability;
  /** The site only works with the user's login. */
  account?: AccountCapability;
}

/** Declares a plugin. Does nothing else: `runPlugin(plugin)` in the bundle entry runs it. */
export function definePlugin(definition: PluginDefinition): PluginDefinition {
  return definition;
}
