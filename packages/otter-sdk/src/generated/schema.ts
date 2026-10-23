// @ts-nocheck
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */

export type Scalars = {
    ID: string,
    Int: number,
    String: string,
    Float: number,
    Boolean: boolean,
}

export type MediaType = 'MOVIE' | 'SERIES' | 'EPISODE'


/** A movie or a series. */
export interface Title {
    id: Scalars['ID']
    mediaType: MediaType
    tmdbId: Scalars['Int']
    imdbId: (Scalars['String'] | null)
    title: Scalars['String']
    originalTitle: (Scalars['String'] | null)
    overview: (Scalars['String'] | null)
    tagline: (Scalars['String'] | null)
    /** Release date of a movie, first air date of a series. */
    releaseDate: (Scalars['String'] | null)
    year: (Scalars['Int'] | null)
    /** Minutes; movies only. */
    runtime: (Scalars['Int'] | null)
    status: (Scalars['String'] | null)
    voteAverage: Scalars['Float']
    voteCount: Scalars['Int']
    popularity: Scalars['Float']
    posterPath: (Scalars['String'] | null)
    backdropPath: (Scalars['String'] | null)
    logoPath: (Scalars['String'] | null)
    /** YouTube key of the trailer. */
    trailerKey: (Scalars['String'] | null)
    genres: Scalars['String'][]
    keywords: Scalars['String'][]
    /** False for titles only seen in lists; reading them through Query.title fetches the rest. */
    detailFetched: Scalars['Boolean']
    credits: Credit[]
    /** TMDB recommendations first, then other work of the director and leads. */
    related: Title[]
    /** Series only. */
    seasons: Season[]
    /** Series only: regular seasons, without specials. 0 until the detail was fetched. */
    seasonCount: Scalars['Int']
    /** Movies only. Stored links; Subscription.streams finds new ones. */
    streams: Stream[]
    progress: (WatchProgress | null)
    rating: (Scalars['Float'] | null)
    inWatchlist: Scalars['Boolean']
    __typename: 'Title'
}

export interface Season {
    id: Scalars['ID']
    seasonNumber: Scalars['Int']
    name: (Scalars['String'] | null)
    overview: (Scalars['String'] | null)
    airDate: (Scalars['String'] | null)
    posterPath: (Scalars['String'] | null)
    episodeCount: Scalars['Int']
    /** Fetched from TMDB on first read. */
    episodes: Episode[]
    __typename: 'Season'
}

export interface Episode {
    id: Scalars['ID']
    tmdbId: Scalars['Int']
    seasonNumber: Scalars['Int']
    episodeNumber: Scalars['Int']
    name: (Scalars['String'] | null)
    overview: (Scalars['String'] | null)
    airDate: (Scalars['String'] | null)
    runtime: (Scalars['Int'] | null)
    stillPath: (Scalars['String'] | null)
    voteAverage: Scalars['Float']
    series: Title
    streams: Stream[]
    progress: (WatchProgress | null)
    /** The episode after this one, crossing seasons. */
    next: (Episode | null)
    /** Parts viewers skip (intro, recap, credits, preview), from TheIntroDB; empty when nobody timed them. */
    skipSegments: SkipSegment[]
    __typename: 'Episode'
}


/** A skippable part of an episode. */
export interface SkipSegment {
    /** intro, recap, credits or preview */
    kind: Scalars['String']
    startSeconds: Scalars['Float']
    /** Null when it runs to the end. */
    endSeconds: (Scalars['Float'] | null)
    __typename: 'SkipSegment'
}

export interface Person {
    id: Scalars['ID']
    tmdbId: Scalars['Int']
    name: Scalars['String']
    profilePath: (Scalars['String'] | null)
    biography: (Scalars['String'] | null)
    birthday: (Scalars['String'] | null)
    deathday: (Scalars['String'] | null)
    placeOfBirth: (Scalars['String'] | null)
    knownForDepartment: (Scalars['String'] | null)
    /** Titles the person is credited on, newest first; job (e.g. "Director") keeps only those crew credits. */
    filmography: Title[]
    /** Actors who appear most often alongside this person in their best-known roles. */
    coStars: CoStar[]
    /** This person's titles that fit the profile's taste best, without disliked or finished ones. */
    recommendedFor: Title[]
    __typename: 'Person'
}

export interface CoStar {
    person: Person
    /** How many of this person's titles they share. */
    sharedTitles: Scalars['Int']
    __typename: 'CoStar'
}

export interface Credit {
    person: Person
    /** cast or crew */
    role: Scalars['String']
    job: (Scalars['String'] | null)
    character: (Scalars['String'] | null)
    order: Scalars['Int']
    __typename: 'Credit'
}

export interface Stream {
    id: Scalars['ID']
    plugin: Scalars['String']
    url: Scalars['String']
    label: (Scalars['String'] | null)
    quality: (Scalars['String'] | null)
    language: (Scalars['String'] | null)
    format: (Scalars['String'] | null)
    sizeBytes: (Scalars['Float'] | null)
    speedBps: (Scalars['Float'] | null)
    /** Null until someone tried to play it. */
    playable: (Scalars['Boolean'] | null)
    foundAt: Scalars['String']
    __typename: 'Stream'
}

export interface StreamUpdate {
    streams: Stream[]
    /** True on the last update of a lookup. */
    done: Scalars['Boolean']
    __typename: 'StreamUpdate'
}

export interface ResolvedStream {
    /** Playable URL, proxied through the backend when the host needs headers. */
    url: Scalars['String']
    mimeType: Scalars['String']
    quality: (Scalars['String'] | null)
    /** The stored link this was resolved from. */
    streamId: (Scalars['ID'] | null)
    __typename: 'ResolvedStream'
}

export interface WatchProgress {
    mediaId: Scalars['ID']
    progressSeconds: Scalars['Float']
    durationSeconds: Scalars['Float']
    completed: Scalars['Boolean']
    updatedAt: Scalars['String']
    __typename: 'WatchProgress'
}

export interface ContinueItem {
    title: Title
    /** For series: the episode to continue or the next one. */
    episode: (Episode | null)
    progress: (WatchProgress | null)
    __typename: 'ContinueItem'
}

export interface HistoryEntry {
    title: Title
    episode: (Episode | null)
    progress: WatchProgress
    __typename: 'HistoryEntry'
}

export interface Recommendation {
    title: Title
    /** Why, e.g. "Because you watched Heat". */
    reason: Scalars['String']
    source: Scalars['String']
    score: Scalars['Float']
    __typename: 'Recommendation'
}


/** Titles featuring the lead actor of a title the profile rated well. */
export interface MoreWithRow {
    person: Person
    becauseOf: Title
    items: Title[]
    __typename: 'MoreWithRow'
}

export interface RecommendationRow {
    key: Scalars['String']
    title: Scalars['String']
    items: Title[]
    __typename: 'RecommendationRow'
}

export interface Profile {
    id: Scalars['ID']
    name: Scalars['String']
    avatarUrl: (Scalars['String'] | null)
    hasPin: Scalars['Boolean']
    isChild: Scalars['Boolean']
    /** Audio languages, most preferred first (ISO 639-1, e.g. de, en). Links in these languages are tried first, dubs before subs. */
    languages: Scalars['String'][]
    /** Show captions by default */
    subtitlesEnabled: Scalars['Boolean']
    /** Preferred caption language (ISO 639-1); null = the profile's first language */
    subtitleLanguage: (Scalars['String'] | null)
    subtitleSize: SubtitleSize
    /** Boost dialogue over music and effects */
    voiceBoost: Scalars['Boolean']
    __typename: 'Profile'
}


/** An external subtitle file for a movie or episode, found by a subtitles plugin. */
export interface Subtitle {
    id: Scalars['ID']
    /** ISO 639-1, e.g. de, en */
    language: Scalars['String']
    /** Display label, e.g. 'Deutsch' or 'English (SDH)' */
    label: Scalars['String']
    /** Release name the file was made for, when the source says */
    release: (Scalars['String'] | null)
    hearingImpaired: Scalars['Boolean']
    /** Plugin that found it */
    source: Scalars['String']
    /** Path on the backend, e.g. /api/subtitles/<id>.vtt; clients prefix their backend origin */
    vttUrl: Scalars['String']
    /** Same file as SRT (for DLNA TVs), /api/subtitles/<id>.srt */
    srtUrl: Scalars['String']
    __typename: 'Subtitle'
}

export type SubtitleSize = 'SMALL' | 'MEDIUM' | 'LARGE'

export interface PinCheck {
    success: Scalars['Boolean']
    /** After a failure, seconds until the next attempt is allowed. */
    waitSeconds: Scalars['Int']
    __typename: 'PinCheck'
}

export type TmdbKeyStatus = 'OK' | 'MISSING' | 'INVALID'

export interface Settings {
    /** The user's own key; empty while the built-in one is used. */
    tmdbApiKey: Scalars['String']
    tmdbLanguage: Scalars['String']
    tmdbKeyStatus: TmdbKeyStatus
    usesDefaultTmdbKey: Scalars['Boolean']
    /** Whether the first-run TMDB key dialog was answered. */
    tmdbKeyPromptSeen: Scalars['Boolean']
    requireVpn: Scalars['Boolean']
    disabledPlugins: Scalars['String'][]
    __typename: 'Settings'
}

export interface PluginSettingField {
    key: Scalars['String']
    label: Scalars['String']
    description: (Scalars['String'] | null)
    type: Scalars['String']
    required: Scalars['Boolean']
    default: (Scalars['String'] | null)
    value: (Scalars['String'] | null)
    __typename: 'PluginSettingField'
}

export interface Plugin {
    name: Scalars['String']
    displayName: (Scalars['String'] | null)
    description: (Scalars['String'] | null)
    icon: (Scalars['String'] | null)
    version: Scalars['String']
    capabilities: Scalars['String'][]
    enabled: Scalars['Boolean']
    /** False when the plugin needs something missing here (e.g. FlareSolverr). */
    available: Scalars['Boolean']
    settings: PluginSettingField[]
    __typename: 'Plugin'
}

export interface Status {
    tmdbConfigured: Scalars['Boolean']
    vpnConnected: Scalars['Boolean']
    requireVpn: Scalars['Boolean']
    __typename: 'Status'
}

export type NotificationKind = 'AVAILABLE' | 'NEW_EPISODE' | 'COMING_SOON'

export interface Notification {
    id: Scalars['ID']
    kind: NotificationKind
    /** E.g. "Now available", "Coming 11 February". */
    heading: Scalars['String']
    body: (Scalars['String'] | null)
    createdAt: Scalars['String']
    read: Scalars['Boolean']
    /** The movie or series. */
    title: Title
    /** Set when the notification is about one episode. */
    episode: (Episode | null)
    __typename: 'Notification'
}

export type DownloadStatus = 'QUEUED' | 'DOWNLOADING' | 'DONE' | 'FAILED'


/** A movie or episode saved for offline playback. */
export interface Download {
    id: Scalars['ID']
    status: DownloadStatus
    /** 0..1 */
    progress: Scalars['Float']
    /** Bytes received so far (the full size once done). */
    sizeBytes: Scalars['Float']
    error: (Scalars['String'] | null)
    createdAt: Scalars['String']
    /** The movie, or the episode's series. */
    title: Title
    episode: (Episode | null)
    /** Local playable URL once done. */
    url: (Scalars['String'] | null)
    mimeType: (Scalars['String'] | null)
    __typename: 'Download'
}

export type TogetherStatus = 'WAITING' | 'CONNECTING' | 'CONNECTED' | 'CLOSED' | 'FAILED'


/** Someone in a Watch Together room. */
export interface TogetherPeer {
    id: Scalars['ID']
    name: Scalars['String']
    /** The profile's avatar (e.g. otter:classic). */
    avatar: (Scalars['String'] | null)
    __typename: 'TogetherPeer'
}


/** A Watch Together room: peers meet over Nostr relays, then sync peer-to-peer. */
export interface TogetherRoom {
    /** Eight characters; share it so others can join. */
    code: Scalars['String']
    hosting: Scalars['Boolean']
    status: TogetherStatus
    self: TogetherPeer
    /** Everyone connected, including self. */
    peers: TogetherPeer[]
    __typename: 'TogetherRoom'
}


/** An app payload (JSON) from another participant. */
export interface TogetherMessage {
    from: TogetherPeer
    payload: Scalars['String']
    __typename: 'TogetherMessage'
}

export interface TogetherUpdate {
    /** Null when there is no room. */
    room: (TogetherRoom | null)
    message: (TogetherMessage | null)
    __typename: 'TogetherUpdate'
}

export type CatalogSort = 'POPULAR' | 'TOP_RATED'


/** Curated discover rows. Blockbusters are movies only, bingeworthy is series only; the others work for both. */
export type CatalogTheme = 'HIDDEN_GEMS' | 'BLOCKBUSTERS' | 'FEEL_GOOD' | 'BINGEWORTHY'

export interface CatalogRow {
    genre: Scalars['String']
    items: Title[]
    __typename: 'CatalogRow'
}

export interface Query {
    /** A movie or series, fetched from TMDB when missing or stale. */
    title: (Title | null)
    episode: (Episode | null)
    /** A person with biography and filmography, fetched when missing or stale. */
    person: (Person | null)
    /** TMDB search; results are stored as titles. */
    search: Title[]
    trending: Title[]
    /** Upcoming releases (soonest first), then recent ones (newest first). Series: currently airing. */
    newAndComingSoon: Title[]
    /** Lead actors of the profile's most recently watched titles of one type, the most frequent first; empty before any history. */
    watchedActors: Person[]
    /** More titles with the lead actor of the profile's latest well-rated title; null before any good rating. */
    moreWith: (MoreWithRow | null)
    /** Stored titles of one type, most popular first. */
    browse: Title[]
    genres: Scalars['String'][]
    /** Popular or top-rated titles straight from TMDB's catalog (stored as titles), to fill Home before the profile has history. */
    catalog: Title[]
    /** One themed row of TMDB's catalog (stored as titles); empty when the theme doesn't fit the media type. */
    catalogTheme: Title[]
    /** Up to count genre rows of popular TMDB titles, skipping the genre names in exclude. */
    catalogRows: CatalogRow[]
    recommendations: Recommendation[]
    recommendationRows: RecommendationRow[]
    continueWatching: ContinueItem[]
    history: HistoryEntry[]
    watchlist: Title[]
    /** Titles the profile rated well (I like this, Love this), latest rating first. */
    likedTitles: Title[]
    /** Newest first. */
    notifications: Notification[]
    unreadNotificationCount: Scalars['Int']
    /** Newest first. */
    downloads: Download[]
    /** Folder finished downloads are saved to as named video files; null when they are not. */
    downloadsFolder: (Scalars['String'] | null)
    /** The open Watch Together room, if any. */
    togetherRoom: (TogetherRoom | null)
    /** Subtitle files for a movie or episode (same id as streams/findStreams), best first. Ranked by the profile's caption language, then its other languages; at most 3 per language. Asks subtitles plugins when nothing fresh is stored. */
    subtitles: Subtitle[]
    profiles: Profile[]
    settings: Settings
    /** Whether TMDB accepts the key; errors when TMDB can't be reached. */
    checkTmdbKey: Scalars['Boolean']
    plugins: Plugin[]
    status: Status
    __typename: 'Query'
}

export interface Mutation {
    /** Ask every streams plugin again for a movie or episode, in the background. */
    findStreams: Scalars['Boolean']
    /** Turn a stored stream into a playable URL; only succeeds when the link really delivers video. */
    resolveStream: ResolvedStream
    /** Resolves several links at once (most preferred first) and returns the fastest that really delivers video. Fails only when none plays. */
    resolveBestStream: ResolvedStream
    recordProgress: WatchProgress
    setRating: Scalars['Boolean']
    clearRating: Scalars['Boolean']
    addToWatchlist: Scalars['Boolean']
    removeFromWatchlist: Scalars['Boolean']
    markNotificationsRead: Scalars['Boolean']
    /** Queue a movie or episode for download; a failed one is retried. streamId picks the link, otherwise the best ones are tried. */
    startDownload: Download
    /** Remove a download (stops it when running). */
    deleteDownload: Scalars['Boolean']
    /** Open the downloads folder in the file manager of the machine the backend runs on. */
    openDownloadsFolder: Scalars['Boolean']
    /** Open a Watch Together room as the profile; replaces any open room. */
    hostTogether: TogetherRoom
    /** Join a room by its code as the profile; replaces any open room. */
    joinTogether: TogetherRoom
    leaveTogether: Scalars['Boolean']
    /** Store a link another Watch Together participant plays, so it can be resolved here. */
    adoptStream: Stream
    /** Send a JSON payload to everyone else in the room. */
    sendTogether: Scalars['Boolean']
    createProfile: Profile
    /** Changes the given fields; omitted fields stay. An empty pin removes the PIN; an empty subtitleLanguage clears it. */
    updateProfile: Profile
    deleteProfile: Scalars['Boolean']
    verifyPin: PinCheck
    updateSettings: Settings
    setPluginEnabled: Plugin
    setPluginSettings: Plugin
    __typename: 'Mutation'
}

export interface Subscription {
    /** Stream list of a movie or episode as plugins answer. Starts a lookup when the stored links are stale. */
    streams: StreamUpdate
    /** The Watch Together room as it changes, and messages from the others. */
    together: TogetherUpdate
    __typename: 'Subscription'
}


/** A movie or a series. */
export interface TitleGenqlSelection{
    id?: boolean | number
    mediaType?: boolean | number
    tmdbId?: boolean | number
    imdbId?: boolean | number
    title?: boolean | number
    originalTitle?: boolean | number
    overview?: boolean | number
    tagline?: boolean | number
    /** Release date of a movie, first air date of a series. */
    releaseDate?: boolean | number
    year?: boolean | number
    /** Minutes; movies only. */
    runtime?: boolean | number
    status?: boolean | number
    voteAverage?: boolean | number
    voteCount?: boolean | number
    popularity?: boolean | number
    posterPath?: boolean | number
    backdropPath?: boolean | number
    logoPath?: boolean | number
    /** YouTube key of the trailer. */
    trailerKey?: boolean | number
    genres?: boolean | number
    keywords?: boolean | number
    /** False for titles only seen in lists; reading them through Query.title fetches the rest. */
    detailFetched?: boolean | number
    credits?: CreditGenqlSelection
    /** TMDB recommendations first, then other work of the director and leads. */
    related?: (TitleGenqlSelection & { __args?: {limit?: (Scalars['Int'] | null)} })
    /** Series only. */
    seasons?: SeasonGenqlSelection
    /** Series only: regular seasons, without specials. 0 until the detail was fetched. */
    seasonCount?: boolean | number
    /** Movies only. Stored links; Subscription.streams finds new ones. */
    streams?: StreamGenqlSelection
    progress?: (WatchProgressGenqlSelection & { __args: {profileId: Scalars['ID']} })
    rating?: { __args: {profileId: Scalars['ID']} }
    inWatchlist?: { __args: {profileId: Scalars['ID']} }
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface SeasonGenqlSelection{
    id?: boolean | number
    seasonNumber?: boolean | number
    name?: boolean | number
    overview?: boolean | number
    airDate?: boolean | number
    posterPath?: boolean | number
    episodeCount?: boolean | number
    /** Fetched from TMDB on first read. */
    episodes?: EpisodeGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface EpisodeGenqlSelection{
    id?: boolean | number
    tmdbId?: boolean | number
    seasonNumber?: boolean | number
    episodeNumber?: boolean | number
    name?: boolean | number
    overview?: boolean | number
    airDate?: boolean | number
    runtime?: boolean | number
    stillPath?: boolean | number
    voteAverage?: boolean | number
    series?: TitleGenqlSelection
    streams?: StreamGenqlSelection
    progress?: (WatchProgressGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** The episode after this one, crossing seasons. */
    next?: EpisodeGenqlSelection
    /** Parts viewers skip (intro, recap, credits, preview), from TheIntroDB; empty when nobody timed them. */
    skipSegments?: SkipSegmentGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A skippable part of an episode. */
export interface SkipSegmentGenqlSelection{
    /** intro, recap, credits or preview */
    kind?: boolean | number
    startSeconds?: boolean | number
    /** Null when it runs to the end. */
    endSeconds?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface PersonGenqlSelection{
    id?: boolean | number
    tmdbId?: boolean | number
    name?: boolean | number
    profilePath?: boolean | number
    biography?: boolean | number
    birthday?: boolean | number
    deathday?: boolean | number
    placeOfBirth?: boolean | number
    knownForDepartment?: boolean | number
    /** Titles the person is credited on, newest first; job (e.g. "Director") keeps only those crew credits. */
    filmography?: (TitleGenqlSelection & { __args?: {job?: (Scalars['String'] | null)} })
    /** Actors who appear most often alongside this person in their best-known roles. */
    coStars?: (CoStarGenqlSelection & { __args?: {limit?: (Scalars['Int'] | null)} })
    /** This person's titles that fit the profile's taste best, without disliked or finished ones. */
    recommendedFor?: (TitleGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface CoStarGenqlSelection{
    person?: PersonGenqlSelection
    /** How many of this person's titles they share. */
    sharedTitles?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface CreditGenqlSelection{
    person?: PersonGenqlSelection
    /** cast or crew */
    role?: boolean | number
    job?: boolean | number
    character?: boolean | number
    order?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface StreamGenqlSelection{
    id?: boolean | number
    plugin?: boolean | number
    url?: boolean | number
    label?: boolean | number
    quality?: boolean | number
    language?: boolean | number
    format?: boolean | number
    sizeBytes?: boolean | number
    speedBps?: boolean | number
    /** Null until someone tried to play it. */
    playable?: boolean | number
    foundAt?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface StreamUpdateGenqlSelection{
    streams?: StreamGenqlSelection
    /** True on the last update of a lookup. */
    done?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface ResolvedStreamGenqlSelection{
    /** Playable URL, proxied through the backend when the host needs headers. */
    url?: boolean | number
    mimeType?: boolean | number
    quality?: boolean | number
    /** The stored link this was resolved from. */
    streamId?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface WatchProgressGenqlSelection{
    mediaId?: boolean | number
    progressSeconds?: boolean | number
    durationSeconds?: boolean | number
    completed?: boolean | number
    updatedAt?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface ContinueItemGenqlSelection{
    title?: TitleGenqlSelection
    /** For series: the episode to continue or the next one. */
    episode?: EpisodeGenqlSelection
    progress?: WatchProgressGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface HistoryEntryGenqlSelection{
    title?: TitleGenqlSelection
    episode?: EpisodeGenqlSelection
    progress?: WatchProgressGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface RecommendationGenqlSelection{
    title?: TitleGenqlSelection
    /** Why, e.g. "Because you watched Heat". */
    reason?: boolean | number
    source?: boolean | number
    score?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** Narrows search results; omitted fields don't filter. Runtime is in minutes. */
export interface TitleFilter {minRating?: (Scalars['Float'] | null),minRuntime?: (Scalars['Int'] | null),maxRuntime?: (Scalars['Int'] | null),minSeasons?: (Scalars['Int'] | null),maxSeasons?: (Scalars['Int'] | null)}


/** Titles featuring the lead actor of a title the profile rated well. */
export interface MoreWithRowGenqlSelection{
    person?: PersonGenqlSelection
    becauseOf?: TitleGenqlSelection
    items?: TitleGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface RecommendationRowGenqlSelection{
    key?: boolean | number
    title?: boolean | number
    items?: TitleGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface ProfileGenqlSelection{
    id?: boolean | number
    name?: boolean | number
    avatarUrl?: boolean | number
    hasPin?: boolean | number
    isChild?: boolean | number
    /** Audio languages, most preferred first (ISO 639-1, e.g. de, en). Links in these languages are tried first, dubs before subs. */
    languages?: boolean | number
    /** Show captions by default */
    subtitlesEnabled?: boolean | number
    /** Preferred caption language (ISO 639-1); null = the profile's first language */
    subtitleLanguage?: boolean | number
    subtitleSize?: boolean | number
    /** Boost dialogue over music and effects */
    voiceBoost?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** An external subtitle file for a movie or episode, found by a subtitles plugin. */
export interface SubtitleGenqlSelection{
    id?: boolean | number
    /** ISO 639-1, e.g. de, en */
    language?: boolean | number
    /** Display label, e.g. 'Deutsch' or 'English (SDH)' */
    label?: boolean | number
    /** Release name the file was made for, when the source says */
    release?: boolean | number
    hearingImpaired?: boolean | number
    /** Plugin that found it */
    source?: boolean | number
    /** Path on the backend, e.g. /api/subtitles/<id>.vtt; clients prefix their backend origin */
    vttUrl?: boolean | number
    /** Same file as SRT (for DLNA TVs), /api/subtitles/<id>.srt */
    srtUrl?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface PinCheckGenqlSelection{
    success?: boolean | number
    /** After a failure, seconds until the next attempt is allowed. */
    waitSeconds?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface SettingsGenqlSelection{
    /** The user's own key; empty while the built-in one is used. */
    tmdbApiKey?: boolean | number
    tmdbLanguage?: boolean | number
    tmdbKeyStatus?: boolean | number
    usesDefaultTmdbKey?: boolean | number
    /** Whether the first-run TMDB key dialog was answered. */
    tmdbKeyPromptSeen?: boolean | number
    requireVpn?: boolean | number
    disabledPlugins?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface SettingsInput {tmdbApiKey?: (Scalars['String'] | null),tmdbLanguage?: (Scalars['String'] | null),tmdbKeyPromptSeen?: (Scalars['Boolean'] | null),requireVpn?: (Scalars['Boolean'] | null)}

export interface PluginSettingFieldGenqlSelection{
    key?: boolean | number
    label?: boolean | number
    description?: boolean | number
    type?: boolean | number
    required?: boolean | number
    default?: boolean | number
    value?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface PluginSettingInput {key: Scalars['String'],value: Scalars['String']}

export interface PluginGenqlSelection{
    name?: boolean | number
    displayName?: boolean | number
    description?: boolean | number
    icon?: boolean | number
    version?: boolean | number
    capabilities?: boolean | number
    enabled?: boolean | number
    /** False when the plugin needs something missing here (e.g. FlareSolverr). */
    available?: boolean | number
    settings?: PluginSettingFieldGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface StatusGenqlSelection{
    tmdbConfigured?: boolean | number
    vpnConnected?: boolean | number
    requireVpn?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface NotificationGenqlSelection{
    id?: boolean | number
    kind?: boolean | number
    /** E.g. "Now available", "Coming 11 February". */
    heading?: boolean | number
    body?: boolean | number
    createdAt?: boolean | number
    read?: boolean | number
    /** The movie or series. */
    title?: TitleGenqlSelection
    /** Set when the notification is about one episode. */
    episode?: EpisodeGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A movie or episode saved for offline playback. */
export interface DownloadGenqlSelection{
    id?: boolean | number
    status?: boolean | number
    /** 0..1 */
    progress?: boolean | number
    /** Bytes received so far (the full size once done). */
    sizeBytes?: boolean | number
    error?: boolean | number
    createdAt?: boolean | number
    /** The movie, or the episode's series. */
    title?: TitleGenqlSelection
    episode?: EpisodeGenqlSelection
    /** Local playable URL once done. */
    url?: boolean | number
    mimeType?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** Someone in a Watch Together room. */
export interface TogetherPeerGenqlSelection{
    id?: boolean | number
    name?: boolean | number
    /** The profile's avatar (e.g. otter:classic). */
    avatar?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A Watch Together room: peers meet over Nostr relays, then sync peer-to-peer. */
export interface TogetherRoomGenqlSelection{
    /** Eight characters; share it so others can join. */
    code?: boolean | number
    hosting?: boolean | number
    status?: boolean | number
    self?: TogetherPeerGenqlSelection
    /** Everyone connected, including self. */
    peers?: TogetherPeerGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** An app payload (JSON) from another participant. */
export interface TogetherMessageGenqlSelection{
    from?: TogetherPeerGenqlSelection
    payload?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface TogetherUpdateGenqlSelection{
    /** Null when there is no room. */
    room?: TogetherRoomGenqlSelection
    message?: TogetherMessageGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface CatalogRowGenqlSelection{
    genre?: boolean | number
    items?: TitleGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface QueryGenqlSelection{
    /** A movie or series, fetched from TMDB when missing or stale. */
    title?: (TitleGenqlSelection & { __args: {id: Scalars['ID']} })
    episode?: (EpisodeGenqlSelection & { __args: {id: Scalars['ID']} })
    /** A person with biography and filmography, fetched when missing or stale. */
    person?: (PersonGenqlSelection & { __args: {id: Scalars['ID']} })
    /** TMDB search; results are stored as titles. */
    search?: (TitleGenqlSelection & { __args: {query: Scalars['String'], mediaType?: (MediaType | null), limit?: (Scalars['Int'] | null), filter?: (TitleFilter | null)} })
    trending?: (TitleGenqlSelection & { __args?: {mediaType?: (MediaType | null)} })
    /** Upcoming releases (soonest first), then recent ones (newest first). Series: currently airing. */
    newAndComingSoon?: (TitleGenqlSelection & { __args: {mediaType: MediaType} })
    /** Lead actors of the profile's most recently watched titles of one type, the most frequent first; empty before any history. */
    watchedActors?: (PersonGenqlSelection & { __args: {profileId: Scalars['ID'], mediaType: MediaType, limit?: (Scalars['Int'] | null)} })
    /** More titles with the lead actor of the profile's latest well-rated title; null before any good rating. */
    moreWith?: (MoreWithRowGenqlSelection & { __args: {profileId: Scalars['ID'], mediaType: MediaType} })
    /** Stored titles of one type, most popular first. */
    browse?: (TitleGenqlSelection & { __args: {mediaType: MediaType, genre?: (Scalars['String'] | null), limit?: (Scalars['Int'] | null), offset?: (Scalars['Int'] | null)} })
    genres?: boolean | number
    /** Popular or top-rated titles straight from TMDB's catalog (stored as titles), to fill Home before the profile has history. */
    catalog?: (TitleGenqlSelection & { __args: {mediaType: MediaType, sort?: (CatalogSort | null)} })
    /** One themed row of TMDB's catalog (stored as titles); empty when the theme doesn't fit the media type. */
    catalogTheme?: (TitleGenqlSelection & { __args: {mediaType: MediaType, theme: CatalogTheme} })
    /** Up to count genre rows of popular TMDB titles, skipping the genre names in exclude. */
    catalogRows?: (CatalogRowGenqlSelection & { __args: {mediaType: MediaType, count?: (Scalars['Int'] | null), exclude?: (Scalars['String'][] | null)} })
    recommendations?: (RecommendationGenqlSelection & { __args: {profileId: Scalars['ID'], mediaType?: (MediaType | null), limit?: (Scalars['Int'] | null), offset?: (Scalars['Int'] | null), refresh?: (Scalars['Boolean'] | null)} })
    recommendationRows?: (RecommendationRowGenqlSelection & { __args: {profileId: Scalars['ID'], rows?: (Scalars['Int'] | null), perRow?: (Scalars['Int'] | null)} })
    continueWatching?: (ContinueItemGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    history?: (HistoryEntryGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    watchlist?: (TitleGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** Titles the profile rated well (I like this, Love this), latest rating first. */
    likedTitles?: (TitleGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    /** Newest first. */
    notifications?: (NotificationGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    unreadNotificationCount?: { __args: {profileId: Scalars['ID']} }
    /** Newest first. */
    downloads?: (DownloadGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** Folder finished downloads are saved to as named video files; null when they are not. */
    downloadsFolder?: boolean | number
    /** The open Watch Together room, if any. */
    togetherRoom?: TogetherRoomGenqlSelection
    /** Subtitle files for a movie or episode (same id as streams/findStreams), best first. Ranked by the profile's caption language, then its other languages; at most 3 per language. Asks subtitles plugins when nothing fresh is stored. */
    subtitles?: (SubtitleGenqlSelection & { __args: {mediaId: Scalars['ID'], profileId?: (Scalars['ID'] | null)} })
    profiles?: ProfileGenqlSelection
    settings?: SettingsGenqlSelection
    /** Whether TMDB accepts the key; errors when TMDB can't be reached. */
    checkTmdbKey?: { __args: {key: Scalars['String']} }
    plugins?: PluginGenqlSelection
    status?: StatusGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface MutationGenqlSelection{
    /** Ask every streams plugin again for a movie or episode, in the background. */
    findStreams?: { __args: {id: Scalars['ID']} }
    /** Turn a stored stream into a playable URL; only succeeds when the link really delivers video. */
    resolveStream?: (ResolvedStreamGenqlSelection & { __args: {streamId: Scalars['ID']} })
    /** Resolves several links at once (most preferred first) and returns the fastest that really delivers video. Fails only when none plays. */
    resolveBestStream?: (ResolvedStreamGenqlSelection & { __args: {streamIds: Scalars['ID'][]} })
    recordProgress?: (WatchProgressGenqlSelection & { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], progressSeconds: Scalars['Float'], durationSeconds: Scalars['Float']} })
    setRating?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], rating: Scalars['Float']} }
    clearRating?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    addToWatchlist?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    removeFromWatchlist?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    markNotificationsRead?: { __args: {profileId: Scalars['ID']} }
    /** Queue a movie or episode for download; a failed one is retried. streamId picks the link, otherwise the best ones are tried. */
    startDownload?: (DownloadGenqlSelection & { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], streamId?: (Scalars['ID'] | null)} })
    /** Remove a download (stops it when running). */
    deleteDownload?: { __args: {id: Scalars['ID']} }
    /** Open the downloads folder in the file manager of the machine the backend runs on. */
    openDownloadsFolder?: boolean | number
    /** Open a Watch Together room as the profile; replaces any open room. */
    hostTogether?: (TogetherRoomGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** Join a room by its code as the profile; replaces any open room. */
    joinTogether?: (TogetherRoomGenqlSelection & { __args: {profileId: Scalars['ID'], code: Scalars['String']} })
    leaveTogether?: boolean | number
    /** Store a link another Watch Together participant plays, so it can be resolved here. */
    adoptStream?: (StreamGenqlSelection & { __args: {mediaId: Scalars['ID'], url: Scalars['String'], plugin: Scalars['String'], label?: (Scalars['String'] | null)} })
    /** Send a JSON payload to everyone else in the room. */
    sendTogether?: { __args: {payload: Scalars['String']} }
    createProfile?: (ProfileGenqlSelection & { __args: {name: Scalars['String'], avatarUrl?: (Scalars['String'] | null), pin?: (Scalars['String'] | null), isChild?: (Scalars['Boolean'] | null), languages?: (Scalars['String'][] | null)} })
    /** Changes the given fields; omitted fields stay. An empty pin removes the PIN; an empty subtitleLanguage clears it. */
    updateProfile?: (ProfileGenqlSelection & { __args: {id: Scalars['ID'], name?: (Scalars['String'] | null), avatarUrl?: (Scalars['String'] | null), isChild?: (Scalars['Boolean'] | null), pin?: (Scalars['String'] | null), languages?: (Scalars['String'][] | null), subtitlesEnabled?: (Scalars['Boolean'] | null), subtitleLanguage?: (Scalars['String'] | null), subtitleSize?: (SubtitleSize | null), voiceBoost?: (Scalars['Boolean'] | null)} })
    deleteProfile?: { __args: {id: Scalars['ID']} }
    verifyPin?: (PinCheckGenqlSelection & { __args: {profileId: Scalars['ID'], pin: Scalars['String']} })
    updateSettings?: (SettingsGenqlSelection & { __args: {input: SettingsInput} })
    setPluginEnabled?: (PluginGenqlSelection & { __args: {name: Scalars['String'], enabled: Scalars['Boolean']} })
    setPluginSettings?: (PluginGenqlSelection & { __args: {name: Scalars['String'], values: PluginSettingInput[]} })
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface SubscriptionGenqlSelection{
    /** Stream list of a movie or episode as plugins answer. Starts a lookup when the stored links are stale. */
    streams?: (StreamUpdateGenqlSelection & { __args: {id: Scalars['ID']} })
    /** The Watch Together room as it changes, and messages from the others. */
    together?: TogetherUpdateGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


    const Title_possibleTypes: string[] = ['Title']
    export const isTitle = (obj?: { __typename?: any } | null): obj is Title => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTitle"')
      return Title_possibleTypes.includes(obj.__typename)
    }
    


    const Season_possibleTypes: string[] = ['Season']
    export const isSeason = (obj?: { __typename?: any } | null): obj is Season => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isSeason"')
      return Season_possibleTypes.includes(obj.__typename)
    }
    


    const Episode_possibleTypes: string[] = ['Episode']
    export const isEpisode = (obj?: { __typename?: any } | null): obj is Episode => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isEpisode"')
      return Episode_possibleTypes.includes(obj.__typename)
    }
    


    const SkipSegment_possibleTypes: string[] = ['SkipSegment']
    export const isSkipSegment = (obj?: { __typename?: any } | null): obj is SkipSegment => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isSkipSegment"')
      return SkipSegment_possibleTypes.includes(obj.__typename)
    }
    


    const Person_possibleTypes: string[] = ['Person']
    export const isPerson = (obj?: { __typename?: any } | null): obj is Person => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPerson"')
      return Person_possibleTypes.includes(obj.__typename)
    }
    


    const CoStar_possibleTypes: string[] = ['CoStar']
    export const isCoStar = (obj?: { __typename?: any } | null): obj is CoStar => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isCoStar"')
      return CoStar_possibleTypes.includes(obj.__typename)
    }
    


    const Credit_possibleTypes: string[] = ['Credit']
    export const isCredit = (obj?: { __typename?: any } | null): obj is Credit => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isCredit"')
      return Credit_possibleTypes.includes(obj.__typename)
    }
    


    const Stream_possibleTypes: string[] = ['Stream']
    export const isStream = (obj?: { __typename?: any } | null): obj is Stream => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isStream"')
      return Stream_possibleTypes.includes(obj.__typename)
    }
    


    const StreamUpdate_possibleTypes: string[] = ['StreamUpdate']
    export const isStreamUpdate = (obj?: { __typename?: any } | null): obj is StreamUpdate => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isStreamUpdate"')
      return StreamUpdate_possibleTypes.includes(obj.__typename)
    }
    


    const ResolvedStream_possibleTypes: string[] = ['ResolvedStream']
    export const isResolvedStream = (obj?: { __typename?: any } | null): obj is ResolvedStream => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isResolvedStream"')
      return ResolvedStream_possibleTypes.includes(obj.__typename)
    }
    


    const WatchProgress_possibleTypes: string[] = ['WatchProgress']
    export const isWatchProgress = (obj?: { __typename?: any } | null): obj is WatchProgress => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isWatchProgress"')
      return WatchProgress_possibleTypes.includes(obj.__typename)
    }
    


    const ContinueItem_possibleTypes: string[] = ['ContinueItem']
    export const isContinueItem = (obj?: { __typename?: any } | null): obj is ContinueItem => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isContinueItem"')
      return ContinueItem_possibleTypes.includes(obj.__typename)
    }
    


    const HistoryEntry_possibleTypes: string[] = ['HistoryEntry']
    export const isHistoryEntry = (obj?: { __typename?: any } | null): obj is HistoryEntry => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isHistoryEntry"')
      return HistoryEntry_possibleTypes.includes(obj.__typename)
    }
    


    const Recommendation_possibleTypes: string[] = ['Recommendation']
    export const isRecommendation = (obj?: { __typename?: any } | null): obj is Recommendation => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isRecommendation"')
      return Recommendation_possibleTypes.includes(obj.__typename)
    }
    


    const MoreWithRow_possibleTypes: string[] = ['MoreWithRow']
    export const isMoreWithRow = (obj?: { __typename?: any } | null): obj is MoreWithRow => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isMoreWithRow"')
      return MoreWithRow_possibleTypes.includes(obj.__typename)
    }
    


    const RecommendationRow_possibleTypes: string[] = ['RecommendationRow']
    export const isRecommendationRow = (obj?: { __typename?: any } | null): obj is RecommendationRow => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isRecommendationRow"')
      return RecommendationRow_possibleTypes.includes(obj.__typename)
    }
    


    const Profile_possibleTypes: string[] = ['Profile']
    export const isProfile = (obj?: { __typename?: any } | null): obj is Profile => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isProfile"')
      return Profile_possibleTypes.includes(obj.__typename)
    }
    


    const Subtitle_possibleTypes: string[] = ['Subtitle']
    export const isSubtitle = (obj?: { __typename?: any } | null): obj is Subtitle => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isSubtitle"')
      return Subtitle_possibleTypes.includes(obj.__typename)
    }
    


    const PinCheck_possibleTypes: string[] = ['PinCheck']
    export const isPinCheck = (obj?: { __typename?: any } | null): obj is PinCheck => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPinCheck"')
      return PinCheck_possibleTypes.includes(obj.__typename)
    }
    


    const Settings_possibleTypes: string[] = ['Settings']
    export const isSettings = (obj?: { __typename?: any } | null): obj is Settings => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isSettings"')
      return Settings_possibleTypes.includes(obj.__typename)
    }
    


    const PluginSettingField_possibleTypes: string[] = ['PluginSettingField']
    export const isPluginSettingField = (obj?: { __typename?: any } | null): obj is PluginSettingField => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPluginSettingField"')
      return PluginSettingField_possibleTypes.includes(obj.__typename)
    }
    


    const Plugin_possibleTypes: string[] = ['Plugin']
    export const isPlugin = (obj?: { __typename?: any } | null): obj is Plugin => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPlugin"')
      return Plugin_possibleTypes.includes(obj.__typename)
    }
    


    const Status_possibleTypes: string[] = ['Status']
    export const isStatus = (obj?: { __typename?: any } | null): obj is Status => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isStatus"')
      return Status_possibleTypes.includes(obj.__typename)
    }
    


    const Notification_possibleTypes: string[] = ['Notification']
    export const isNotification = (obj?: { __typename?: any } | null): obj is Notification => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isNotification"')
      return Notification_possibleTypes.includes(obj.__typename)
    }
    


    const Download_possibleTypes: string[] = ['Download']
    export const isDownload = (obj?: { __typename?: any } | null): obj is Download => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isDownload"')
      return Download_possibleTypes.includes(obj.__typename)
    }
    


    const TogetherPeer_possibleTypes: string[] = ['TogetherPeer']
    export const isTogetherPeer = (obj?: { __typename?: any } | null): obj is TogetherPeer => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTogetherPeer"')
      return TogetherPeer_possibleTypes.includes(obj.__typename)
    }
    


    const TogetherRoom_possibleTypes: string[] = ['TogetherRoom']
    export const isTogetherRoom = (obj?: { __typename?: any } | null): obj is TogetherRoom => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTogetherRoom"')
      return TogetherRoom_possibleTypes.includes(obj.__typename)
    }
    


    const TogetherMessage_possibleTypes: string[] = ['TogetherMessage']
    export const isTogetherMessage = (obj?: { __typename?: any } | null): obj is TogetherMessage => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTogetherMessage"')
      return TogetherMessage_possibleTypes.includes(obj.__typename)
    }
    


    const TogetherUpdate_possibleTypes: string[] = ['TogetherUpdate']
    export const isTogetherUpdate = (obj?: { __typename?: any } | null): obj is TogetherUpdate => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTogetherUpdate"')
      return TogetherUpdate_possibleTypes.includes(obj.__typename)
    }
    


    const CatalogRow_possibleTypes: string[] = ['CatalogRow']
    export const isCatalogRow = (obj?: { __typename?: any } | null): obj is CatalogRow => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isCatalogRow"')
      return CatalogRow_possibleTypes.includes(obj.__typename)
    }
    


    const Query_possibleTypes: string[] = ['Query']
    export const isQuery = (obj?: { __typename?: any } | null): obj is Query => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isQuery"')
      return Query_possibleTypes.includes(obj.__typename)
    }
    


    const Mutation_possibleTypes: string[] = ['Mutation']
    export const isMutation = (obj?: { __typename?: any } | null): obj is Mutation => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isMutation"')
      return Mutation_possibleTypes.includes(obj.__typename)
    }
    


    const Subscription_possibleTypes: string[] = ['Subscription']
    export const isSubscription = (obj?: { __typename?: any } | null): obj is Subscription => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isSubscription"')
      return Subscription_possibleTypes.includes(obj.__typename)
    }
    

export const enumMediaType = {
   MOVIE: 'MOVIE' as const,
   SERIES: 'SERIES' as const,
   EPISODE: 'EPISODE' as const
}

export const enumSubtitleSize = {
   SMALL: 'SMALL' as const,
   MEDIUM: 'MEDIUM' as const,
   LARGE: 'LARGE' as const
}

export const enumTmdbKeyStatus = {
   OK: 'OK' as const,
   MISSING: 'MISSING' as const,
   INVALID: 'INVALID' as const
}

export const enumNotificationKind = {
   AVAILABLE: 'AVAILABLE' as const,
   NEW_EPISODE: 'NEW_EPISODE' as const,
   COMING_SOON: 'COMING_SOON' as const
}

export const enumDownloadStatus = {
   QUEUED: 'QUEUED' as const,
   DOWNLOADING: 'DOWNLOADING' as const,
   DONE: 'DONE' as const,
   FAILED: 'FAILED' as const
}

export const enumTogetherStatus = {
   WAITING: 'WAITING' as const,
   CONNECTING: 'CONNECTING' as const,
   CONNECTED: 'CONNECTED' as const,
   CLOSED: 'CLOSED' as const,
   FAILED: 'FAILED' as const
}

export const enumCatalogSort = {
   POPULAR: 'POPULAR' as const,
   TOP_RATED: 'TOP_RATED' as const
}

export const enumCatalogTheme = {
   HIDDEN_GEMS: 'HIDDEN_GEMS' as const,
   BLOCKBUSTERS: 'BLOCKBUSTERS' as const,
   FEEL_GOOD: 'FEEL_GOOD' as const,
   BINGEWORTHY: 'BINGEWORTHY' as const
}
