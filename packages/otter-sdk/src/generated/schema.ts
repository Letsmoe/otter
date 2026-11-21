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
    /** Whether TMDB tags the title with a scene during or after the credits (the duringcreditsstinger and aftercreditsstinger keywords). */
    hasPostCreditsScene: Scalars['Boolean']
    /** False for titles only seen in lists; reading them through Query.title fetches the rest. */
    detailFetched: Scalars['Boolean']
    /** Movies only: the franchise (TMDB collection) the film belongs to, for Query.collection. */
    collectionId: (Scalars['ID'] | null)
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
    /** Whether the profile gets news about this title: the Notify me bell's choice, else whether it is on My List. */
    notifying: Scalars['Boolean']
    /** The profile's latest rewatch of this movie or series; null when there never was one. */
    rewatch: (Rewatch | null)
    __typename: 'Title'
}


/**
 * A second run through a movie or series. While it is going, `progress` of the title and its
 * episodes is the rewatch's own, so Continue watching, history and recommendations stay as the
 * first watch left them.
 */
export interface Rewatch {
    startedAt: Scalars['String']
    /** True once the movie, or every aired episode, was watched again. */
    finished: Scalars['Boolean']
    __typename: 'Rewatch'
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
    /** Anime only: whether a filler list calls the episode filler, mixed (canon and filler) or canon; null when no list covers it. */
    filler: (FillerKind | null)
    __typename: 'Episode'
}


/** What a filler list says about an episode. */
export type FillerKind = 'FILLER' | 'MIXED' | 'CANON'


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
    /** What the app measured when it played; null until it did. */
    report: (LinkReport | null)
    __typename: 'Stream'
}


/** What a link really delivered when it played, as the app measured it. */
export interface LinkReport {
    /** Pixel height the site's label claimed; 0 when it named none */
    claimedHeight: Scalars['Int']
    /** Pixel height of the picture (wide films count by their width); 0 when unknown */
    measuredHeight: Scalars['Int']
    /** True when the picture had clearly fewer pixels than claimed */
    overclaimed: Scalars['Boolean']
    /** Video bitrate of the latest play in bits per second; 0 when unknown */
    bitrateBps: Scalars['Int']
    /** Times the latest play stopped to buffer after it had started, and for how long */
    stalls: Scalars['Int']
    stallMillis: Scalars['Int']
    /** Milliseconds from loading the link to its first picture, latest play */
    firstFrameMillis: Scalars['Int']
    /** Reported plays, and how many of them stalled a lot */
    plays: Scalars['Int']
    roughPlays: Scalars['Int']
    __typename: 'LinkReport'
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


/** A moment of a movie or episode the profile saved while watching. */
export interface Bookmark {
    id: Scalars['ID']
    /** The movie or episode. */
    mediaId: Scalars['ID']
    positionSeconds: Scalars['Float']
    /** The caption line spoken at that moment; null when there was none. */
    caption: (Scalars['String'] | null)
    createdAt: Scalars['String']
    __typename: 'Bookmark'
}

export interface ContinueItem {
    title: Title
    /** For series: the episode to continue or the next one. */
    episode: (Episode | null)
    progress: (WatchProgress | null)
    __typename: 'ContinueItem'
}


/** One suggestion for a time budget. */
export interface TimeSlotItem {
    title: Title
    /** For a series: the episode to play. */
    episode: (Episode | null)
    /** How long it still takes. */
    minutes: Scalars['Int']
    /** Why it is offered, e.g. "Next episode", "Pick up where you stopped", "From My List", "Recommended". */
    reason: Scalars['String']
    __typename: 'TimeSlotItem'
}


/** An episode that has not aired yet. */
export interface UpcomingEpisode {
    series: Title
    /** Its airDate (yyyy-MM-dd, TMDB's) is today or within the asked days. */
    episode: Episode
    __typename: 'UpcomingEpisode'
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


/** How far a profile got through one film of a franchise. */
export type WatchStatus = 'WATCHED' | 'IN_PROGRESS' | 'NOT_YET'


/** A franchise (TMDB collection) with its films in release order, and one profile's progress through them. */
export interface Collection {
    id: Scalars['ID']
    tmdbId: Scalars['Int']
    name: Scalars['String']
    overview: (Scalars['String'] | null)
    posterPath: (Scalars['String'] | null)
    backdropPath: (Scalars['String'] | null)
    parts: CollectionPart[]
    /** Films the profile finished. */
    watchedCount: Scalars['Int']
    /** The film to continue with: the first released one not finished yet. Null when everything out is watched. */
    next: (CollectionPart | null)
    __typename: 'Collection'
}

export interface CollectionPart {
    /** Place in the watch order, from 1. */
    number: Scalars['Int']
    title: Title
    status: WatchStatus
    /** Watched share (0..1) of a film in progress. */
    progress: Scalars['Float']
    /** False for films that are announced but not out yet. */
    released: Scalars['Boolean']
    __typename: 'CollectionPart'
}


/** A profile's own Home row: a named, ordered list of movies and series. */
export interface Shelf {
    id: Scalars['ID']
    name: Scalars['String']
    titles: Title[]
    __typename: 'Shelf'
}


/**
 * One profile's calendar year of watching, from its watch history. The history keeps one row per
 * movie or episode, dated by its latest update, so a title counts in the year of its last watch and
 * rewatches are estimated: a title started 30+ days before its last watch counts as watched again.
 */
export interface YearInReview {
    year: Scalars['Int']
    /** False when nothing was watched that year. */
    hasActivity: Scalars['Boolean']
    hoursWatched: Scalars['Float']
    moviesFinished: Scalars['Int']
    episodesFinished: Scalars['Int']
    /** Series with at least one episode watched. */
    seriesWatched: Scalars['Int']
    topSeries: (TopSeries | null)
    /** Up to five, the most watched first. */
    topGenres: GenreShare[]
    longestBinge: (Binge | null)
    mostRewatched: (Rewatched | null)
    firstTitle: (YearMoment | null)
    lastTitle: (YearMoment | null)
    __typename: 'YearInReview'
}

export interface TopSeries {
    title: Title
    hoursWatched: Scalars['Float']
    episodesWatched: Scalars['Int']
    __typename: 'TopSeries'
}

export interface GenreShare {
    genre: Scalars['String']
    hoursWatched: Scalars['Float']
    /** Share of the watch time of titles with genres, 0..1. */
    share: Scalars['Float']
    __typename: 'GenreShare'
}


/** Most episodes of one series in one night (a night runs from 5 am to 5 am). */
export interface Binge {
    title: Title
    episodes: Scalars['Int']
    /** The date the night began, YYYY-MM-DD. */
    date: Scalars['String']
    __typename: 'Binge'
}

export interface Rewatched {
    title: Title
    /** Episodes watched again (1 for a movie). */
    count: Scalars['Int']
    __typename: 'Rewatched'
}

export interface YearMoment {
    title: Title
    /** YYYY-MM-DD. */
    date: Scalars['String']
    __typename: 'YearMoment'
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
    /** How captions look on the phone and on TVs */
    subtitleStyle: SubtitleStyle
    /** Boost dialogue over music and effects */
    voiceBoost: Scalars['Boolean']
    /** Child profiles: the oldest age their titles may be rated for (FSK in Germany); 0 means no limit. Titles above it, or without a rating, are hidden and can't be played. */
    ageLimit: Scalars['Int']
    /** Child profiles: the fixed bedtime in minutes after midnight, after which every title stops after its episode; null for none. */
    bedtimeMinutes: (Scalars['Int'] | null)
    /** The Android app shows only Continue, My List and Search with big text and buttons */
    simpleMode: Scalars['Boolean']
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

export type SubtitleStyle = 'OUTLINE' | 'BOX' | 'YELLOW'

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
    /** Whether this build has a built-in key to fall back to (in use or not). */
    hasDefaultTmdbKey: Scalars['Boolean']
    /** Whether the first-run TMDB key dialog was answered. */
    tmdbKeyPromptSeen: Scalars['Boolean']
    requireVpn: Scalars['Boolean']
    /** Whether errors are sent to the developers (PostHog error tracking). */
    errorReporting: Scalars['Boolean']
    /** Whether every TV cast is logged in detail, not only failed ones (opt-in). */
    castLogs: Scalars['Boolean']
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
    /** Set when the plugin's site needs the user's account. */
    account: (PluginAccount | null)
    __typename: 'Plugin'
}


/** A plugin's login on its site. The password is never returned. */
export interface PluginAccount {
    /** Where users without an account can make one. */
    signupUrl: (Scalars['String'] | null)
    /** Null while no account is saved. */
    username: (Scalars['String'] | null)
    loggedIn: Scalars['Boolean']
    /** Why the last login failed. */
    error: (Scalars['String'] | null)
    __typename: 'PluginAccount'
}

export interface Status {
    tmdbConfigured: Scalars['Boolean']
    /** False while the last TMDB request got no answer (offline, or a VPN or DNS filter blocks it). */
    tmdbReachable: Scalars['Boolean']
    /** Whether the backend's traffic to the internet goes through a VPN. */
    vpnConnected: Scalars['Boolean']
    /** A VPN is up, but Otter's traffic skips it (e.g. Otter is excluded by the VPN's split tunnelling). */
    vpnBypassed: Scalars['Boolean']
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
    /** A franchise's watch-order guide for a profile; the film list is fetched from TMDB when missing or stale. */
    collection: (Collection | null)
    /** Franchises the library knows (two or more films): the ones the profile is in the middle of first, finished ones last. */
    franchises: Collection[]
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
    /** What fits into the given minutes: what the profile is in the middle of (next episodes, movies not finished), then short films from My List and recommendations. Items that use the time best come first within each group. */
    timeSlot: TimeSlotItem[]
    /** Episodes airing from today within days (default four weeks, at most 90), soonest first, of the series on the profile's My List or watched in the last months; air dates from TMDB. */
    upcomingEpisodes: UpcomingEpisode[]
    /** Rewatches still going, most recent first: the movie, or the episode of the series to continue or start next. */
    continueRewatching: ContinueItem[]
    history: HistoryEntry[]
    watchlist: Title[]
    /** The profile's bookmarks of a movie, or of all episodes of a series (in watching order), earliest moment first. */
    bookmarks: Bookmark[]
    /** The profile's pinned shelves in their order, with their titles in order. */
    shelves: Shelf[]
    /** The profile's year in review. utcOffsetMinutes is the phone's time zone, so days and nights fall where they were lived. */
    yearInReview: YearInReview
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
    /** Path of the profile's calendar feed (upcoming episodes of the series it watches or has on My List, next 8 weeks) on the backend, e.g. /api/calendar/<secret>.ics. Whoever has the full URL can read the feed. */
    calendarFeedPath: Scalars['String']
    settings: Settings
    /** Whether TMDB accepts the key; errors when TMDB can't be reached. */
    checkTmdbKey: Scalars['Boolean']
    plugins: Plugin[]
    status: Status
    /** The household's Neoworks account, which syncs profiles, history, lists, ratings and settings between devices. */
    neoworksAccount: NeoworksAccount
    /** Progress and result of the profile's latest Letterboxd import. */
    letterboxdImport: LetterboxdImport
    /** The Letterboxd account the profile follows; null when none. */
    letterboxdAccount: (LetterboxdAccount | null)
    __typename: 'Query'
}

export type LetterboxdImportState = 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED'


/** An import of a Letterboxd export (watched, ratings, diary, watchlist) into a profile. */
export interface LetterboxdImport {
    state: LetterboxdImportState
    /** Films in the export, and how many were looked up on TMDB so far. */
    total: Scalars['Int']
    processed: Scalars['Int']
    /** Films that found their TMDB movie. */
    matched: Scalars['Int']
    /** What the import added; what Otter already had is not counted. */
    watched: Scalars['Int']
    rated: Scalars['Int']
    watchlisted: Scalars['Int']
    /** Films TMDB had no match for; notMatched lists the first few hundred. */
    unmatchedCount: Scalars['Int']
    notMatched: LetterboxdFilm[]
    error: (Scalars['String'] | null)
    __typename: 'LetterboxdImport'
}

export interface LetterboxdFilm {
    name: Scalars['String']
    year: (Scalars['Int'] | null)
    uri: (Scalars['String'] | null)
    reason: Scalars['String']
    __typename: 'LetterboxdFilm'
}


/** A Letterboxd account whose public diary feed a profile follows. Otter only reads from Letterboxd; writing needs API access Letterboxd has not given. */
export interface LetterboxdAccount {
    username: Scalars['String']
    lastSyncedAt: (Scalars['String'] | null)
    /** Why the last sync failed; null after a good one. */
    lastError: (Scalars['String'] | null)
    /** Watches and ratings the last sync added. */
    lastWatched: Scalars['Int']
    lastRated: Scalars['Int']
    __typename: 'LetterboxdAccount'
}

export interface NeoworksAccount {
    signedIn: Scalars['Boolean']
    /** Null while signed out, and when Neoworks didn't share it. */
    email: (Scalars['String'] | null)
    name: (Scalars['String'] | null)
    /** The last sync since the backend started; null before the first. */
    lastSync: (NeoworksSync | null)
    __typename: 'NeoworksAccount'
}

export interface NeoworksSync {
    at: Scalars['String']
    /** Local records changed by other devices. */
    received: Scalars['Int']
    /** Rows written from this device. */
    sent: Scalars['Int']
    /** Why the sync stopped; null when it worked. */
    failure: (Scalars['String'] | null)
    __typename: 'NeoworksSync'
}

export type ChallengeKind = 'CLEARANCE' | 'NAVIGATION'


/** A site's bot check (Cloudflare, Turnstile) the phone app solves in a WebView, like FlareSolverr. */
export interface Challenge {
    id: Scalars['ID']
    kind: ChallengeKind
    /** The page to open; the check passes on it. */
    url: Scalars['String']
    domain: Scalars['String']
    /** Show the page to the user: the hidden attempt failed and they are waiting to play. */
    interactive: Scalars['Boolean']
    /** NAVIGATION: CSS selector of the element to click once the page has loaded, e.g. a link's button. */
    click: (Scalars['String'] | null)
    /** NAVIGATION: CSS selector of a form to submit once its captchas are solved; then the first page off the site in any frame after the submit counts. */
    submit: (Scalars['String'] | null)
    __typename: 'Challenge'
}

export interface Mutation {
    /** The app passed a bot check: its cookies for the site, the WebView's user agent they are bound to, and for a NAVIGATION the URL it led to. */
    completeChallenge: Scalars['Boolean']
    /** The app couldn't pass a bot check, e.g. hidden while it wants a click. linkGone: the page lacks the element to click, so the link is dead and nobody is asked to try. */
    failChallenge: Scalars['Boolean']
    /** Ask every streams plugin again for a movie or episode, in the background. */
    findStreams: Scalars['Boolean']
    /** Turn a stored stream into a playable URL; only succeeds when the link really delivers video. */
    resolveStream: ResolvedStream
    /** Resolves several links at once (most preferred first) and returns the fastest that really delivers video. Fails only when none plays. */
    resolveBestStream: ResolvedStream
    /** Records what a link delivered while it played (resolution, bitrate, stalls, time to first picture). Links that stall or claim more than they deliver are tried later. */
    reportLinkPlayback: Scalars['Boolean']
    recordProgress: WatchProgress
    /** Starts a rewatch of a movie or series from the beginning; an earlier rewatch's progress is dropped, the first watch's is kept. */
    startRewatch: Rewatch
    /** Ends the rewatch and forgets its progress; the first watch's progress is read again. */
    stopRewatch: Scalars['Boolean']
    setRating: Scalars['Boolean']
    clearRating: Scalars['Boolean']
    /** "Not for me" on a recommendation: the profile never gets this title suggested again, and titles like it rank lower. */
    dismissRecommendation: Scalars['Boolean']
    /** Undo dismissRecommendation. */
    restoreRecommendation: Scalars['Boolean']
    /** Saves the position of a movie or episode, with the caption line spoken then. */
    addBookmark: Bookmark
    removeBookmark: Scalars['Boolean']
    addToWatchlist: Scalars['Boolean']
    removeFromWatchlist: Scalars['Boolean']
    /** A new empty shelf after the profile's others. */
    createShelf: Shelf
    renameShelf: Shelf
    /** Deletes the shelf with its titles. */
    deleteShelf: Scalars['Boolean']
    /** Puts a movie or series at the end of a shelf; one already on it stays where it is. */
    addToShelf: Scalars['Boolean']
    removeFromShelf: Scalars['Boolean']
    /** Sets the order of the profile's shelves; shelves left out follow in their old order. */
    reorderShelves: Scalars['Boolean']
    /** Sets the order of a shelf's titles; titles left out follow in their old order. */
    reorderShelfTitles: Scalars['Boolean']
    /** The Notify me bell: news about a movie or series on or off, on My List or not. Adding to or removing from My List resets it. */
    setNotifying: Scalars['Boolean']
    markNotificationsRead: Scalars['Boolean']
    /** Check every title on any My List for news now instead of at the next sweep; returns when done. Used by the phone's background check. */
    checkNotifications: Scalars['Boolean']
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
    /** The first profile's top language also becomes the TMDB language. */
    createProfile: Profile
    /** Changes the given fields; omitted fields stay. An empty pin removes the PIN; an empty subtitleLanguage clears it; ageLimit 0 and a negative bedtimeMinutes remove those. */
    updateProfile: Profile
    deleteProfile: Scalars['Boolean']
    verifyPin: PinCheck
    /** Makes a new secret for the profile's calendar feed; the old URL stops working. Returns the new path. */
    resetCalendarFeed: Scalars['String']
    updateSettings: Settings
    /** Asks TMDB right now whether it answers; also updates Status.tmdbReachable. */
    checkTmdbConnection: Scalars['Boolean']
    setPluginEnabled: Plugin
    setPluginSettings: Plugin
    /** Logs in to a plugin's site; the account is saved only when the site accepts it. */
    loginPlugin: Plugin
    /** Forgets a plugin's account. */
    logoutPlugin: Plugin
    /** Check npm for newer plugin releases now and install them (local builds stay). Fails when PLUGIN_AUTO_UPDATE=false. */
    updatePlugins: PluginUpdateReport
    /** Starts signing in to Neoworks and returns the page to open in a browser. returnTo is the web app's origin (e.g. http://nas.local:3000); the Android app leaves it out. */
    startNeoworksSignIn: Scalars['String']
    /** Finishes the sign-in with the code and state otter.letsmoe.com/auth/callback received. */
    completeNeoworksSignIn: NeoworksAccount
    /** Signs this device out; the household's data stays in Neoworks for its other devices. */
    signOutOfNeoworks: NeoworksAccount
    /** Syncs with Neoworks now; returns when done, with the result in lastSync. */
    syncNeoworks: NeoworksAccount
    /** Starts a sync in the background and returns at once; the apps call it when they open. */
    syncNeoworksSoon: Scalars['Boolean']
    /** Starts importing a Letterboxd export ZIP (base64) into the profile in the background; follow it with Query.letterboxdImport. Refused when the file is no export or an import is running. */
    importLetterboxd: LetterboxdImport
    /** Makes the profile follow a Letterboxd account's public diary (a username or a profile address); the feed is read every few hours. */
    connectLetterboxd: LetterboxdAccount
    /** Stops following the account; what was imported stays. */
    disconnectLetterboxd: Scalars['Boolean']
    /** Reads the followed account's diary feed now; a failure is in the account's lastError. */
    syncLetterboxd: LetterboxdAccount
    __typename: 'Mutation'
}

export interface PluginUpdate {
    /** The plugin's name, as in Plugin.name */
    name: Scalars['String']
    fromVersion: Scalars['String']
    toVersion: Scalars['String']
    __typename: 'PluginUpdate'
}

export interface PluginUpdateReport {
    updated: PluginUpdate[]
    /** Plugin folders whose check, download or install failed */
    failed: Scalars['String'][]
    __typename: 'PluginUpdateReport'
}

export interface Subscription {
    /** Stream list of a movie or episode as plugins answer. Starts a lookup when the stored links are stale. */
    streams: StreamUpdate
    /** The Watch Together room as it changes, and messages from the others. */
    together: TogetherUpdate
    /** Bot checks to solve, for the phone app; while it listens, plugins that need solving are available. */
    challenges: Challenge
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
    /** Whether TMDB tags the title with a scene during or after the credits (the duringcreditsstinger and aftercreditsstinger keywords). */
    hasPostCreditsScene?: boolean | number
    /** False for titles only seen in lists; reading them through Query.title fetches the rest. */
    detailFetched?: boolean | number
    /** Movies only: the franchise (TMDB collection) the film belongs to, for Query.collection. */
    collectionId?: boolean | number
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
    /** Whether the profile gets news about this title: the Notify me bell's choice, else whether it is on My List. */
    notifying?: { __args: {profileId: Scalars['ID']} }
    /** The profile's latest rewatch of this movie or series; null when there never was one. */
    rewatch?: (RewatchGenqlSelection & { __args: {profileId: Scalars['ID']} })
    __typename?: boolean | number
    __scalar?: boolean | number
}


/**
 * A second run through a movie or series. While it is going, `progress` of the title and its
 * episodes is the rewatch's own, so Continue watching, history and recommendations stay as the
 * first watch left them.
 */
export interface RewatchGenqlSelection{
    startedAt?: boolean | number
    /** True once the movie, or every aired episode, was watched again. */
    finished?: boolean | number
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
    /** Anime only: whether a filler list calls the episode filler, mixed (canon and filler) or canon; null when no list covers it. */
    filler?: boolean | number
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
    /** What the app measured when it played; null until it did. */
    report?: LinkReportGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** What a link really delivered when it played, as the app measured it. */
export interface LinkReportGenqlSelection{
    /** Pixel height the site's label claimed; 0 when it named none */
    claimedHeight?: boolean | number
    /** Pixel height of the picture (wide films count by their width); 0 when unknown */
    measuredHeight?: boolean | number
    /** True when the picture had clearly fewer pixels than claimed */
    overclaimed?: boolean | number
    /** Video bitrate of the latest play in bits per second; 0 when unknown */
    bitrateBps?: boolean | number
    /** Times the latest play stopped to buffer after it had started, and for how long */
    stalls?: boolean | number
    stallMillis?: boolean | number
    /** Milliseconds from loading the link to its first picture, latest play */
    firstFrameMillis?: boolean | number
    /** Reported plays, and how many of them stalled a lot */
    plays?: boolean | number
    roughPlays?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** What the player measured during one play of a link. */
export interface LinkReportInput {streamId: Scalars['ID'],widthPixels: Scalars['Int'],heightPixels: Scalars['Int'],bitrateBps: Scalars['Int'],stalls: Scalars['Int'],stallMillis: Scalars['Int'],firstFrameMillis: Scalars['Int'],playedMillis: Scalars['Int']}

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


/** A moment of a movie or episode the profile saved while watching. */
export interface BookmarkGenqlSelection{
    id?: boolean | number
    /** The movie or episode. */
    mediaId?: boolean | number
    positionSeconds?: boolean | number
    /** The caption line spoken at that moment; null when there was none. */
    caption?: boolean | number
    createdAt?: boolean | number
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


/** One suggestion for a time budget. */
export interface TimeSlotItemGenqlSelection{
    title?: TitleGenqlSelection
    /** For a series: the episode to play. */
    episode?: EpisodeGenqlSelection
    /** How long it still takes. */
    minutes?: boolean | number
    /** Why it is offered, e.g. "Next episode", "Pick up where you stopped", "From My List", "Recommended". */
    reason?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** An episode that has not aired yet. */
export interface UpcomingEpisodeGenqlSelection{
    series?: TitleGenqlSelection
    /** Its airDate (yyyy-MM-dd, TMDB's) is today or within the asked days. */
    episode?: EpisodeGenqlSelection
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


/** A franchise (TMDB collection) with its films in release order, and one profile's progress through them. */
export interface CollectionGenqlSelection{
    id?: boolean | number
    tmdbId?: boolean | number
    name?: boolean | number
    overview?: boolean | number
    posterPath?: boolean | number
    backdropPath?: boolean | number
    parts?: CollectionPartGenqlSelection
    /** Films the profile finished. */
    watchedCount?: boolean | number
    /** The film to continue with: the first released one not finished yet. Null when everything out is watched. */
    next?: CollectionPartGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface CollectionPartGenqlSelection{
    /** Place in the watch order, from 1. */
    number?: boolean | number
    title?: TitleGenqlSelection
    status?: boolean | number
    /** Watched share (0..1) of a film in progress. */
    progress?: boolean | number
    /** False for films that are announced but not out yet. */
    released?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A profile's own Home row: a named, ordered list of movies and series. */
export interface ShelfGenqlSelection{
    id?: boolean | number
    name?: boolean | number
    titles?: TitleGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


/**
 * One profile's calendar year of watching, from its watch history. The history keeps one row per
 * movie or episode, dated by its latest update, so a title counts in the year of its last watch and
 * rewatches are estimated: a title started 30+ days before its last watch counts as watched again.
 */
export interface YearInReviewGenqlSelection{
    year?: boolean | number
    /** False when nothing was watched that year. */
    hasActivity?: boolean | number
    hoursWatched?: boolean | number
    moviesFinished?: boolean | number
    episodesFinished?: boolean | number
    /** Series with at least one episode watched. */
    seriesWatched?: boolean | number
    topSeries?: TopSeriesGenqlSelection
    /** Up to five, the most watched first. */
    topGenres?: GenreShareGenqlSelection
    longestBinge?: BingeGenqlSelection
    mostRewatched?: RewatchedGenqlSelection
    firstTitle?: YearMomentGenqlSelection
    lastTitle?: YearMomentGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface TopSeriesGenqlSelection{
    title?: TitleGenqlSelection
    hoursWatched?: boolean | number
    episodesWatched?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface GenreShareGenqlSelection{
    genre?: boolean | number
    hoursWatched?: boolean | number
    /** Share of the watch time of titles with genres, 0..1. */
    share?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** Most episodes of one series in one night (a night runs from 5 am to 5 am). */
export interface BingeGenqlSelection{
    title?: TitleGenqlSelection
    episodes?: boolean | number
    /** The date the night began, YYYY-MM-DD. */
    date?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface RewatchedGenqlSelection{
    title?: TitleGenqlSelection
    /** Episodes watched again (1 for a movie). */
    count?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface YearMomentGenqlSelection{
    title?: TitleGenqlSelection
    /** YYYY-MM-DD. */
    date?: boolean | number
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
    /** How captions look on the phone and on TVs */
    subtitleStyle?: boolean | number
    /** Boost dialogue over music and effects */
    voiceBoost?: boolean | number
    /** Child profiles: the oldest age their titles may be rated for (FSK in Germany); 0 means no limit. Titles above it, or without a rating, are hidden and can't be played. */
    ageLimit?: boolean | number
    /** Child profiles: the fixed bedtime in minutes after midnight, after which every title stops after its episode; null for none. */
    bedtimeMinutes?: boolean | number
    /** The Android app shows only Continue, My List and Search with big text and buttons */
    simpleMode?: boolean | number
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
    /** Whether this build has a built-in key to fall back to (in use or not). */
    hasDefaultTmdbKey?: boolean | number
    /** Whether the first-run TMDB key dialog was answered. */
    tmdbKeyPromptSeen?: boolean | number
    requireVpn?: boolean | number
    /** Whether errors are sent to the developers (PostHog error tracking). */
    errorReporting?: boolean | number
    /** Whether every TV cast is logged in detail, not only failed ones (opt-in). */
    castLogs?: boolean | number
    disabledPlugins?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface SettingsInput {tmdbApiKey?: (Scalars['String'] | null),tmdbLanguage?: (Scalars['String'] | null),tmdbKeyPromptSeen?: (Scalars['Boolean'] | null),requireVpn?: (Scalars['Boolean'] | null),errorReporting?: (Scalars['Boolean'] | null),castLogs?: (Scalars['Boolean'] | null)}

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
    /** Set when the plugin's site needs the user's account. */
    account?: PluginAccountGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A plugin's login on its site. The password is never returned. */
export interface PluginAccountGenqlSelection{
    /** Where users without an account can make one. */
    signupUrl?: boolean | number
    /** Null while no account is saved. */
    username?: boolean | number
    loggedIn?: boolean | number
    /** Why the last login failed. */
    error?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface StatusGenqlSelection{
    tmdbConfigured?: boolean | number
    /** False while the last TMDB request got no answer (offline, or a VPN or DNS filter blocks it). */
    tmdbReachable?: boolean | number
    /** Whether the backend's traffic to the internet goes through a VPN. */
    vpnConnected?: boolean | number
    /** A VPN is up, but Otter's traffic skips it (e.g. Otter is excluded by the VPN's split tunnelling). */
    vpnBypassed?: boolean | number
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
    /** A franchise's watch-order guide for a profile; the film list is fetched from TMDB when missing or stale. */
    collection?: (CollectionGenqlSelection & { __args: {id: Scalars['ID'], profileId: Scalars['ID']} })
    /** Franchises the library knows (two or more films): the ones the profile is in the middle of first, finished ones last. */
    franchises?: (CollectionGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
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
    /** What fits into the given minutes: what the profile is in the middle of (next episodes, movies not finished), then short films from My List and recommendations. Items that use the time best come first within each group. */
    timeSlot?: (TimeSlotItemGenqlSelection & { __args: {profileId: Scalars['ID'], minutes: Scalars['Int'], limit?: (Scalars['Int'] | null)} })
    /** Episodes airing from today within days (default four weeks, at most 90), soonest first, of the series on the profile's My List or watched in the last months; air dates from TMDB. */
    upcomingEpisodes?: (UpcomingEpisodeGenqlSelection & { __args: {profileId: Scalars['ID'], days?: (Scalars['Int'] | null)} })
    /** Rewatches still going, most recent first: the movie, or the episode of the series to continue or start next. */
    continueRewatching?: (ContinueItemGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    history?: (HistoryEntryGenqlSelection & { __args: {profileId: Scalars['ID'], limit?: (Scalars['Int'] | null)} })
    watchlist?: (TitleGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** The profile's bookmarks of a movie, or of all episodes of a series (in watching order), earliest moment first. */
    bookmarks?: (BookmarkGenqlSelection & { __args: {profileId: Scalars['ID'], titleId: Scalars['ID']} })
    /** The profile's pinned shelves in their order, with their titles in order. */
    shelves?: (ShelfGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** The profile's year in review. utcOffsetMinutes is the phone's time zone, so days and nights fall where they were lived. */
    yearInReview?: (YearInReviewGenqlSelection & { __args: {profileId: Scalars['ID'], year: Scalars['Int'], utcOffsetMinutes?: (Scalars['Int'] | null)} })
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
    /** Path of the profile's calendar feed (upcoming episodes of the series it watches or has on My List, next 8 weeks) on the backend, e.g. /api/calendar/<secret>.ics. Whoever has the full URL can read the feed. */
    calendarFeedPath?: { __args: {profileId: Scalars['ID']} }
    settings?: SettingsGenqlSelection
    /** Whether TMDB accepts the key; errors when TMDB can't be reached. */
    checkTmdbKey?: { __args: {key: Scalars['String']} }
    plugins?: PluginGenqlSelection
    status?: StatusGenqlSelection
    /** The household's Neoworks account, which syncs profiles, history, lists, ratings and settings between devices. */
    neoworksAccount?: NeoworksAccountGenqlSelection
    /** Progress and result of the profile's latest Letterboxd import. */
    letterboxdImport?: (LetterboxdImportGenqlSelection & { __args: {profileId: Scalars['ID']} })
    /** The Letterboxd account the profile follows; null when none. */
    letterboxdAccount?: (LetterboxdAccountGenqlSelection & { __args: {profileId: Scalars['ID']} })
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** An import of a Letterboxd export (watched, ratings, diary, watchlist) into a profile. */
export interface LetterboxdImportGenqlSelection{
    state?: boolean | number
    /** Films in the export, and how many were looked up on TMDB so far. */
    total?: boolean | number
    processed?: boolean | number
    /** Films that found their TMDB movie. */
    matched?: boolean | number
    /** What the import added; what Otter already had is not counted. */
    watched?: boolean | number
    rated?: boolean | number
    watchlisted?: boolean | number
    /** Films TMDB had no match for; notMatched lists the first few hundred. */
    unmatchedCount?: boolean | number
    notMatched?: LetterboxdFilmGenqlSelection
    error?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface LetterboxdFilmGenqlSelection{
    name?: boolean | number
    year?: boolean | number
    uri?: boolean | number
    reason?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A Letterboxd account whose public diary feed a profile follows. Otter only reads from Letterboxd; writing needs API access Letterboxd has not given. */
export interface LetterboxdAccountGenqlSelection{
    username?: boolean | number
    lastSyncedAt?: boolean | number
    /** Why the last sync failed; null after a good one. */
    lastError?: boolean | number
    /** Watches and ratings the last sync added. */
    lastWatched?: boolean | number
    lastRated?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface NeoworksAccountGenqlSelection{
    signedIn?: boolean | number
    /** Null while signed out, and when Neoworks didn't share it. */
    email?: boolean | number
    name?: boolean | number
    /** The last sync since the backend started; null before the first. */
    lastSync?: NeoworksSyncGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface NeoworksSyncGenqlSelection{
    at?: boolean | number
    /** Local records changed by other devices. */
    received?: boolean | number
    /** Rows written from this device. */
    sent?: boolean | number
    /** Why the sync stopped; null when it worked. */
    failure?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}


/** A site's bot check (Cloudflare, Turnstile) the phone app solves in a WebView, like FlareSolverr. */
export interface ChallengeGenqlSelection{
    id?: boolean | number
    kind?: boolean | number
    /** The page to open; the check passes on it. */
    url?: boolean | number
    domain?: boolean | number
    /** Show the page to the user: the hidden attempt failed and they are waiting to play. */
    interactive?: boolean | number
    /** NAVIGATION: CSS selector of the element to click once the page has loaded, e.g. a link's button. */
    click?: boolean | number
    /** NAVIGATION: CSS selector of a form to submit once its captchas are solved; then the first page off the site in any frame after the submit counts. */
    submit?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface MutationGenqlSelection{
    /** The app passed a bot check: its cookies for the site, the WebView's user agent they are bound to, and for a NAVIGATION the URL it led to. */
    completeChallenge?: { __args: {id: Scalars['ID'], cookies: Scalars['String'], userAgent: Scalars['String'], finalUrl?: (Scalars['String'] | null)} }
    /** The app couldn't pass a bot check, e.g. hidden while it wants a click. linkGone: the page lacks the element to click, so the link is dead and nobody is asked to try. */
    failChallenge?: { __args: {id: Scalars['ID'], reason: Scalars['String'], linkGone?: (Scalars['Boolean'] | null)} }
    /** Ask every streams plugin again for a movie or episode, in the background. */
    findStreams?: { __args: {id: Scalars['ID']} }
    /** Turn a stored stream into a playable URL; only succeeds when the link really delivers video. */
    resolveStream?: (ResolvedStreamGenqlSelection & { __args: {streamId: Scalars['ID']} })
    /** Resolves several links at once (most preferred first) and returns the fastest that really delivers video. Fails only when none plays. */
    resolveBestStream?: (ResolvedStreamGenqlSelection & { __args: {streamIds: Scalars['ID'][]} })
    /** Records what a link delivered while it played (resolution, bitrate, stalls, time to first picture). Links that stall or claim more than they deliver are tried later. */
    reportLinkPlayback?: { __args: {input: LinkReportInput} }
    recordProgress?: (WatchProgressGenqlSelection & { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], progressSeconds: Scalars['Float'], durationSeconds: Scalars['Float']} })
    /** Starts a rewatch of a movie or series from the beginning; an earlier rewatch's progress is dropped, the first watch's is kept. */
    startRewatch?: (RewatchGenqlSelection & { __args: {profileId: Scalars['ID'], titleId: Scalars['ID']} })
    /** Ends the rewatch and forgets its progress; the first watch's progress is read again. */
    stopRewatch?: { __args: {profileId: Scalars['ID'], titleId: Scalars['ID']} }
    setRating?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], rating: Scalars['Float']} }
    clearRating?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    /** "Not for me" on a recommendation: the profile never gets this title suggested again, and titles like it rank lower. */
    dismissRecommendation?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    /** Undo dismissRecommendation. */
    restoreRecommendation?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    /** Saves the position of a movie or episode, with the caption line spoken then. */
    addBookmark?: (BookmarkGenqlSelection & { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], positionSeconds: Scalars['Float'], caption?: (Scalars['String'] | null)} })
    removeBookmark?: { __args: {profileId: Scalars['ID'], bookmarkId: Scalars['ID']} }
    addToWatchlist?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    removeFromWatchlist?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID']} }
    /** A new empty shelf after the profile's others. */
    createShelf?: (ShelfGenqlSelection & { __args: {profileId: Scalars['ID'], name: Scalars['String']} })
    renameShelf?: (ShelfGenqlSelection & { __args: {profileId: Scalars['ID'], shelfId: Scalars['ID'], name: Scalars['String']} })
    /** Deletes the shelf with its titles. */
    deleteShelf?: { __args: {profileId: Scalars['ID'], shelfId: Scalars['ID']} }
    /** Puts a movie or series at the end of a shelf; one already on it stays where it is. */
    addToShelf?: { __args: {profileId: Scalars['ID'], shelfId: Scalars['ID'], mediaId: Scalars['ID']} }
    removeFromShelf?: { __args: {profileId: Scalars['ID'], shelfId: Scalars['ID'], mediaId: Scalars['ID']} }
    /** Sets the order of the profile's shelves; shelves left out follow in their old order. */
    reorderShelves?: { __args: {profileId: Scalars['ID'], shelfIds: Scalars['ID'][]} }
    /** Sets the order of a shelf's titles; titles left out follow in their old order. */
    reorderShelfTitles?: { __args: {profileId: Scalars['ID'], shelfId: Scalars['ID'], mediaIds: Scalars['ID'][]} }
    /** The Notify me bell: news about a movie or series on or off, on My List or not. Adding to or removing from My List resets it. */
    setNotifying?: { __args: {profileId: Scalars['ID'], mediaId: Scalars['ID'], notifying: Scalars['Boolean']} }
    markNotificationsRead?: { __args: {profileId: Scalars['ID']} }
    /** Check every title on any My List for news now instead of at the next sweep; returns when done. Used by the phone's background check. */
    checkNotifications?: boolean | number
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
    /** The first profile's top language also becomes the TMDB language. */
    createProfile?: (ProfileGenqlSelection & { __args: {name: Scalars['String'], avatarUrl?: (Scalars['String'] | null), pin?: (Scalars['String'] | null), isChild?: (Scalars['Boolean'] | null), languages?: (Scalars['String'][] | null)} })
    /** Changes the given fields; omitted fields stay. An empty pin removes the PIN; an empty subtitleLanguage clears it; ageLimit 0 and a negative bedtimeMinutes remove those. */
    updateProfile?: (ProfileGenqlSelection & { __args: {id: Scalars['ID'], name?: (Scalars['String'] | null), avatarUrl?: (Scalars['String'] | null), isChild?: (Scalars['Boolean'] | null), pin?: (Scalars['String'] | null), languages?: (Scalars['String'][] | null), subtitlesEnabled?: (Scalars['Boolean'] | null), subtitleLanguage?: (Scalars['String'] | null), subtitleSize?: (SubtitleSize | null), subtitleStyle?: (SubtitleStyle | null), voiceBoost?: (Scalars['Boolean'] | null), ageLimit?: (Scalars['Int'] | null), bedtimeMinutes?: (Scalars['Int'] | null), simpleMode?: (Scalars['Boolean'] | null)} })
    deleteProfile?: { __args: {id: Scalars['ID']} }
    verifyPin?: (PinCheckGenqlSelection & { __args: {profileId: Scalars['ID'], pin: Scalars['String']} })
    /** Makes a new secret for the profile's calendar feed; the old URL stops working. Returns the new path. */
    resetCalendarFeed?: { __args: {profileId: Scalars['ID']} }
    updateSettings?: (SettingsGenqlSelection & { __args: {input: SettingsInput} })
    /** Asks TMDB right now whether it answers; also updates Status.tmdbReachable. */
    checkTmdbConnection?: boolean | number
    setPluginEnabled?: (PluginGenqlSelection & { __args: {name: Scalars['String'], enabled: Scalars['Boolean']} })
    setPluginSettings?: (PluginGenqlSelection & { __args: {name: Scalars['String'], values: PluginSettingInput[]} })
    /** Logs in to a plugin's site; the account is saved only when the site accepts it. */
    loginPlugin?: (PluginGenqlSelection & { __args: {name: Scalars['String'], username: Scalars['String'], password: Scalars['String']} })
    /** Forgets a plugin's account. */
    logoutPlugin?: (PluginGenqlSelection & { __args: {name: Scalars['String']} })
    /** Check npm for newer plugin releases now and install them (local builds stay). Fails when PLUGIN_AUTO_UPDATE=false. */
    updatePlugins?: PluginUpdateReportGenqlSelection
    /** Starts signing in to Neoworks and returns the page to open in a browser. returnTo is the web app's origin (e.g. http://nas.local:3000); the Android app leaves it out. */
    startNeoworksSignIn?: { __args: {returnTo?: (Scalars['String'] | null)} } | boolean | number
    /** Finishes the sign-in with the code and state otter.letsmoe.com/auth/callback received. */
    completeNeoworksSignIn?: (NeoworksAccountGenqlSelection & { __args: {code: Scalars['String'], state: Scalars['String']} })
    /** Signs this device out; the household's data stays in Neoworks for its other devices. */
    signOutOfNeoworks?: NeoworksAccountGenqlSelection
    /** Syncs with Neoworks now; returns when done, with the result in lastSync. */
    syncNeoworks?: NeoworksAccountGenqlSelection
    /** Starts a sync in the background and returns at once; the apps call it when they open. */
    syncNeoworksSoon?: boolean | number
    /** Starts importing a Letterboxd export ZIP (base64) into the profile in the background; follow it with Query.letterboxdImport. Refused when the file is no export or an import is running. */
    importLetterboxd?: (LetterboxdImportGenqlSelection & { __args: {profileId: Scalars['ID'], zipBase64: Scalars['String']} })
    /** Makes the profile follow a Letterboxd account's public diary (a username or a profile address); the feed is read every few hours. */
    connectLetterboxd?: (LetterboxdAccountGenqlSelection & { __args: {profileId: Scalars['ID'], username: Scalars['String']} })
    /** Stops following the account; what was imported stays. */
    disconnectLetterboxd?: { __args: {profileId: Scalars['ID']} }
    /** Reads the followed account's diary feed now; a failure is in the account's lastError. */
    syncLetterboxd?: (LetterboxdAccountGenqlSelection & { __args: {profileId: Scalars['ID']} })
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface PluginUpdateGenqlSelection{
    /** The plugin's name, as in Plugin.name */
    name?: boolean | number
    fromVersion?: boolean | number
    toVersion?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface PluginUpdateReportGenqlSelection{
    updated?: PluginUpdateGenqlSelection
    /** Plugin folders whose check, download or install failed */
    failed?: boolean | number
    __typename?: boolean | number
    __scalar?: boolean | number
}

export interface SubscriptionGenqlSelection{
    /** Stream list of a movie or episode as plugins answer. Starts a lookup when the stored links are stale. */
    streams?: (StreamUpdateGenqlSelection & { __args: {id: Scalars['ID']} })
    /** The Watch Together room as it changes, and messages from the others. */
    together?: TogetherUpdateGenqlSelection
    /** Bot checks to solve, for the phone app; while it listens, plugins that need solving are available. */
    challenges?: ChallengeGenqlSelection
    __typename?: boolean | number
    __scalar?: boolean | number
}


    const Title_possibleTypes: string[] = ['Title']
    export const isTitle = (obj?: { __typename?: any } | null): obj is Title => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTitle"')
      return Title_possibleTypes.includes(obj.__typename)
    }
    


    const Rewatch_possibleTypes: string[] = ['Rewatch']
    export const isRewatch = (obj?: { __typename?: any } | null): obj is Rewatch => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isRewatch"')
      return Rewatch_possibleTypes.includes(obj.__typename)
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
    


    const LinkReport_possibleTypes: string[] = ['LinkReport']
    export const isLinkReport = (obj?: { __typename?: any } | null): obj is LinkReport => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isLinkReport"')
      return LinkReport_possibleTypes.includes(obj.__typename)
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
    


    const Bookmark_possibleTypes: string[] = ['Bookmark']
    export const isBookmark = (obj?: { __typename?: any } | null): obj is Bookmark => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isBookmark"')
      return Bookmark_possibleTypes.includes(obj.__typename)
    }
    


    const ContinueItem_possibleTypes: string[] = ['ContinueItem']
    export const isContinueItem = (obj?: { __typename?: any } | null): obj is ContinueItem => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isContinueItem"')
      return ContinueItem_possibleTypes.includes(obj.__typename)
    }
    


    const TimeSlotItem_possibleTypes: string[] = ['TimeSlotItem']
    export const isTimeSlotItem = (obj?: { __typename?: any } | null): obj is TimeSlotItem => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTimeSlotItem"')
      return TimeSlotItem_possibleTypes.includes(obj.__typename)
    }
    


    const UpcomingEpisode_possibleTypes: string[] = ['UpcomingEpisode']
    export const isUpcomingEpisode = (obj?: { __typename?: any } | null): obj is UpcomingEpisode => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isUpcomingEpisode"')
      return UpcomingEpisode_possibleTypes.includes(obj.__typename)
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
    


    const Collection_possibleTypes: string[] = ['Collection']
    export const isCollection = (obj?: { __typename?: any } | null): obj is Collection => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isCollection"')
      return Collection_possibleTypes.includes(obj.__typename)
    }
    


    const CollectionPart_possibleTypes: string[] = ['CollectionPart']
    export const isCollectionPart = (obj?: { __typename?: any } | null): obj is CollectionPart => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isCollectionPart"')
      return CollectionPart_possibleTypes.includes(obj.__typename)
    }
    


    const Shelf_possibleTypes: string[] = ['Shelf']
    export const isShelf = (obj?: { __typename?: any } | null): obj is Shelf => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isShelf"')
      return Shelf_possibleTypes.includes(obj.__typename)
    }
    


    const YearInReview_possibleTypes: string[] = ['YearInReview']
    export const isYearInReview = (obj?: { __typename?: any } | null): obj is YearInReview => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isYearInReview"')
      return YearInReview_possibleTypes.includes(obj.__typename)
    }
    


    const TopSeries_possibleTypes: string[] = ['TopSeries']
    export const isTopSeries = (obj?: { __typename?: any } | null): obj is TopSeries => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isTopSeries"')
      return TopSeries_possibleTypes.includes(obj.__typename)
    }
    


    const GenreShare_possibleTypes: string[] = ['GenreShare']
    export const isGenreShare = (obj?: { __typename?: any } | null): obj is GenreShare => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isGenreShare"')
      return GenreShare_possibleTypes.includes(obj.__typename)
    }
    


    const Binge_possibleTypes: string[] = ['Binge']
    export const isBinge = (obj?: { __typename?: any } | null): obj is Binge => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isBinge"')
      return Binge_possibleTypes.includes(obj.__typename)
    }
    


    const Rewatched_possibleTypes: string[] = ['Rewatched']
    export const isRewatched = (obj?: { __typename?: any } | null): obj is Rewatched => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isRewatched"')
      return Rewatched_possibleTypes.includes(obj.__typename)
    }
    


    const YearMoment_possibleTypes: string[] = ['YearMoment']
    export const isYearMoment = (obj?: { __typename?: any } | null): obj is YearMoment => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isYearMoment"')
      return YearMoment_possibleTypes.includes(obj.__typename)
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
    


    const PluginAccount_possibleTypes: string[] = ['PluginAccount']
    export const isPluginAccount = (obj?: { __typename?: any } | null): obj is PluginAccount => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPluginAccount"')
      return PluginAccount_possibleTypes.includes(obj.__typename)
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
    


    const LetterboxdImport_possibleTypes: string[] = ['LetterboxdImport']
    export const isLetterboxdImport = (obj?: { __typename?: any } | null): obj is LetterboxdImport => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isLetterboxdImport"')
      return LetterboxdImport_possibleTypes.includes(obj.__typename)
    }
    


    const LetterboxdFilm_possibleTypes: string[] = ['LetterboxdFilm']
    export const isLetterboxdFilm = (obj?: { __typename?: any } | null): obj is LetterboxdFilm => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isLetterboxdFilm"')
      return LetterboxdFilm_possibleTypes.includes(obj.__typename)
    }
    


    const LetterboxdAccount_possibleTypes: string[] = ['LetterboxdAccount']
    export const isLetterboxdAccount = (obj?: { __typename?: any } | null): obj is LetterboxdAccount => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isLetterboxdAccount"')
      return LetterboxdAccount_possibleTypes.includes(obj.__typename)
    }
    


    const NeoworksAccount_possibleTypes: string[] = ['NeoworksAccount']
    export const isNeoworksAccount = (obj?: { __typename?: any } | null): obj is NeoworksAccount => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isNeoworksAccount"')
      return NeoworksAccount_possibleTypes.includes(obj.__typename)
    }
    


    const NeoworksSync_possibleTypes: string[] = ['NeoworksSync']
    export const isNeoworksSync = (obj?: { __typename?: any } | null): obj is NeoworksSync => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isNeoworksSync"')
      return NeoworksSync_possibleTypes.includes(obj.__typename)
    }
    


    const Challenge_possibleTypes: string[] = ['Challenge']
    export const isChallenge = (obj?: { __typename?: any } | null): obj is Challenge => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isChallenge"')
      return Challenge_possibleTypes.includes(obj.__typename)
    }
    


    const Mutation_possibleTypes: string[] = ['Mutation']
    export const isMutation = (obj?: { __typename?: any } | null): obj is Mutation => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isMutation"')
      return Mutation_possibleTypes.includes(obj.__typename)
    }
    


    const PluginUpdate_possibleTypes: string[] = ['PluginUpdate']
    export const isPluginUpdate = (obj?: { __typename?: any } | null): obj is PluginUpdate => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPluginUpdate"')
      return PluginUpdate_possibleTypes.includes(obj.__typename)
    }
    


    const PluginUpdateReport_possibleTypes: string[] = ['PluginUpdateReport']
    export const isPluginUpdateReport = (obj?: { __typename?: any } | null): obj is PluginUpdateReport => {
      if (!obj?.__typename) throw new Error('__typename is missing in "isPluginUpdateReport"')
      return PluginUpdateReport_possibleTypes.includes(obj.__typename)
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

export const enumFillerKind = {
   FILLER: 'FILLER' as const,
   MIXED: 'MIXED' as const,
   CANON: 'CANON' as const
}

export const enumWatchStatus = {
   WATCHED: 'WATCHED' as const,
   IN_PROGRESS: 'IN_PROGRESS' as const,
   NOT_YET: 'NOT_YET' as const
}

export const enumSubtitleSize = {
   SMALL: 'SMALL' as const,
   MEDIUM: 'MEDIUM' as const,
   LARGE: 'LARGE' as const
}

export const enumSubtitleStyle = {
   OUTLINE: 'OUTLINE' as const,
   BOX: 'BOX' as const,
   YELLOW: 'YELLOW' as const
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

export const enumLetterboxdImportState = {
   IDLE: 'IDLE' as const,
   RUNNING: 'RUNNING' as const,
   DONE: 'DONE' as const,
   FAILED: 'FAILED' as const
}

export const enumChallengeKind = {
   CLEARANCE: 'CLEARANCE' as const,
   NAVIGATION: 'NAVIGATION' as const
}
