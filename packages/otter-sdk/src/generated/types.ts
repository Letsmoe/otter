export default {
    "scalars": [
        0,
        2,
        3,
        4,
        5,
        6,
        26,
        27,
        29,
        37,
        39,
        41,
        46,
        47,
        52
    ],
    "types": {
        "MediaType": {},
        "Title": {
            "id": [
                2
            ],
            "mediaType": [
                0
            ],
            "tmdbId": [
                3
            ],
            "imdbId": [
                4
            ],
            "title": [
                4
            ],
            "originalTitle": [
                4
            ],
            "overview": [
                4
            ],
            "tagline": [
                4
            ],
            "releaseDate": [
                4
            ],
            "year": [
                3
            ],
            "runtime": [
                3
            ],
            "status": [
                4
            ],
            "voteAverage": [
                5
            ],
            "voteCount": [
                3
            ],
            "popularity": [
                5
            ],
            "posterPath": [
                4
            ],
            "backdropPath": [
                4
            ],
            "logoPath": [
                4
            ],
            "trailerKey": [
                4
            ],
            "genres": [
                4
            ],
            "keywords": [
                4
            ],
            "detailFetched": [
                6
            ],
            "credits": [
                13
            ],
            "related": [
                1,
                {
                    "limit": [
                        3
                    ]
                }
            ],
            "seasons": [
                8
            ],
            "seasonCount": [
                3
            ],
            "streams": [
                14
            ],
            "progress": [
                17,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "rating": [
                5,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "inWatchlist": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "notifying": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "rewatch": [
                7,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "__typename": [
                4
            ]
        },
        "ID": {},
        "Int": {},
        "String": {},
        "Float": {},
        "Boolean": {},
        "Rewatch": {
            "startedAt": [
                4
            ],
            "finished": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "Season": {
            "id": [
                2
            ],
            "seasonNumber": [
                3
            ],
            "name": [
                4
            ],
            "overview": [
                4
            ],
            "airDate": [
                4
            ],
            "posterPath": [
                4
            ],
            "episodeCount": [
                3
            ],
            "episodes": [
                9
            ],
            "__typename": [
                4
            ]
        },
        "Episode": {
            "id": [
                2
            ],
            "tmdbId": [
                3
            ],
            "seasonNumber": [
                3
            ],
            "episodeNumber": [
                3
            ],
            "name": [
                4
            ],
            "overview": [
                4
            ],
            "airDate": [
                4
            ],
            "runtime": [
                3
            ],
            "stillPath": [
                4
            ],
            "voteAverage": [
                5
            ],
            "series": [
                1
            ],
            "streams": [
                14
            ],
            "progress": [
                17,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "next": [
                9
            ],
            "skipSegments": [
                10
            ],
            "__typename": [
                4
            ]
        },
        "SkipSegment": {
            "kind": [
                4
            ],
            "startSeconds": [
                5
            ],
            "endSeconds": [
                5
            ],
            "__typename": [
                4
            ]
        },
        "Person": {
            "id": [
                2
            ],
            "tmdbId": [
                3
            ],
            "name": [
                4
            ],
            "profilePath": [
                4
            ],
            "biography": [
                4
            ],
            "birthday": [
                4
            ],
            "deathday": [
                4
            ],
            "placeOfBirth": [
                4
            ],
            "knownForDepartment": [
                4
            ],
            "filmography": [
                1,
                {
                    "job": [
                        4
                    ]
                }
            ],
            "coStars": [
                12,
                {
                    "limit": [
                        3
                    ]
                }
            ],
            "recommendedFor": [
                1,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "__typename": [
                4
            ]
        },
        "CoStar": {
            "person": [
                11
            ],
            "sharedTitles": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "Credit": {
            "person": [
                11
            ],
            "role": [
                4
            ],
            "job": [
                4
            ],
            "character": [
                4
            ],
            "order": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "Stream": {
            "id": [
                2
            ],
            "plugin": [
                4
            ],
            "url": [
                4
            ],
            "label": [
                4
            ],
            "quality": [
                4
            ],
            "language": [
                4
            ],
            "format": [
                4
            ],
            "sizeBytes": [
                5
            ],
            "speedBps": [
                5
            ],
            "playable": [
                6
            ],
            "foundAt": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "StreamUpdate": {
            "streams": [
                14
            ],
            "done": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "ResolvedStream": {
            "url": [
                4
            ],
            "mimeType": [
                4
            ],
            "quality": [
                4
            ],
            "streamId": [
                2
            ],
            "__typename": [
                4
            ]
        },
        "WatchProgress": {
            "mediaId": [
                2
            ],
            "progressSeconds": [
                5
            ],
            "durationSeconds": [
                5
            ],
            "completed": [
                6
            ],
            "updatedAt": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "ContinueItem": {
            "title": [
                1
            ],
            "episode": [
                9
            ],
            "progress": [
                17
            ],
            "__typename": [
                4
            ]
        },
        "HistoryEntry": {
            "title": [
                1
            ],
            "episode": [
                9
            ],
            "progress": [
                17
            ],
            "__typename": [
                4
            ]
        },
        "Recommendation": {
            "title": [
                1
            ],
            "reason": [
                4
            ],
            "source": [
                4
            ],
            "score": [
                5
            ],
            "__typename": [
                4
            ]
        },
        "TitleFilter": {
            "minRating": [
                5
            ],
            "minRuntime": [
                3
            ],
            "maxRuntime": [
                3
            ],
            "minSeasons": [
                3
            ],
            "maxSeasons": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "MoreWithRow": {
            "person": [
                11
            ],
            "becauseOf": [
                1
            ],
            "items": [
                1
            ],
            "__typename": [
                4
            ]
        },
        "RecommendationRow": {
            "key": [
                4
            ],
            "title": [
                4
            ],
            "items": [
                1
            ],
            "__typename": [
                4
            ]
        },
        "Profile": {
            "id": [
                2
            ],
            "name": [
                4
            ],
            "avatarUrl": [
                4
            ],
            "hasPin": [
                6
            ],
            "isChild": [
                6
            ],
            "languages": [
                4
            ],
            "subtitlesEnabled": [
                6
            ],
            "subtitleLanguage": [
                4
            ],
            "subtitleSize": [
                26
            ],
            "subtitleStyle": [
                27
            ],
            "voiceBoost": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "Subtitle": {
            "id": [
                2
            ],
            "language": [
                4
            ],
            "label": [
                4
            ],
            "release": [
                4
            ],
            "hearingImpaired": [
                6
            ],
            "source": [
                4
            ],
            "vttUrl": [
                4
            ],
            "srtUrl": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "SubtitleSize": {},
        "SubtitleStyle": {},
        "PinCheck": {
            "success": [
                6
            ],
            "waitSeconds": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "TmdbKeyStatus": {},
        "Settings": {
            "tmdbApiKey": [
                4
            ],
            "tmdbLanguage": [
                4
            ],
            "tmdbKeyStatus": [
                29
            ],
            "usesDefaultTmdbKey": [
                6
            ],
            "hasDefaultTmdbKey": [
                6
            ],
            "tmdbKeyPromptSeen": [
                6
            ],
            "requireVpn": [
                6
            ],
            "errorReporting": [
                6
            ],
            "castLogs": [
                6
            ],
            "disabledPlugins": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "SettingsInput": {
            "tmdbApiKey": [
                4
            ],
            "tmdbLanguage": [
                4
            ],
            "tmdbKeyPromptSeen": [
                6
            ],
            "requireVpn": [
                6
            ],
            "errorReporting": [
                6
            ],
            "castLogs": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "PluginSettingField": {
            "key": [
                4
            ],
            "label": [
                4
            ],
            "description": [
                4
            ],
            "type": [
                4
            ],
            "required": [
                6
            ],
            "default": [
                4
            ],
            "value": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "PluginSettingInput": {
            "key": [
                4
            ],
            "value": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "Plugin": {
            "name": [
                4
            ],
            "displayName": [
                4
            ],
            "description": [
                4
            ],
            "icon": [
                4
            ],
            "version": [
                4
            ],
            "capabilities": [
                4
            ],
            "enabled": [
                6
            ],
            "available": [
                6
            ],
            "settings": [
                32
            ],
            "account": [
                35
            ],
            "__typename": [
                4
            ]
        },
        "PluginAccount": {
            "signupUrl": [
                4
            ],
            "username": [
                4
            ],
            "loggedIn": [
                6
            ],
            "error": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "Status": {
            "tmdbConfigured": [
                6
            ],
            "tmdbReachable": [
                6
            ],
            "vpnConnected": [
                6
            ],
            "vpnBypassed": [
                6
            ],
            "requireVpn": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "NotificationKind": {},
        "Notification": {
            "id": [
                2
            ],
            "kind": [
                37
            ],
            "heading": [
                4
            ],
            "body": [
                4
            ],
            "createdAt": [
                4
            ],
            "read": [
                6
            ],
            "title": [
                1
            ],
            "episode": [
                9
            ],
            "__typename": [
                4
            ]
        },
        "DownloadStatus": {},
        "Download": {
            "id": [
                2
            ],
            "status": [
                39
            ],
            "progress": [
                5
            ],
            "sizeBytes": [
                5
            ],
            "error": [
                4
            ],
            "createdAt": [
                4
            ],
            "title": [
                1
            ],
            "episode": [
                9
            ],
            "url": [
                4
            ],
            "mimeType": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "TogetherStatus": {},
        "TogetherPeer": {
            "id": [
                2
            ],
            "name": [
                4
            ],
            "avatar": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "TogetherRoom": {
            "code": [
                4
            ],
            "hosting": [
                6
            ],
            "status": [
                41
            ],
            "self": [
                42
            ],
            "peers": [
                42
            ],
            "__typename": [
                4
            ]
        },
        "TogetherMessage": {
            "from": [
                42
            ],
            "payload": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "TogetherUpdate": {
            "room": [
                43
            ],
            "message": [
                44
            ],
            "__typename": [
                4
            ]
        },
        "CatalogSort": {},
        "CatalogTheme": {},
        "CatalogRow": {
            "genre": [
                4
            ],
            "items": [
                1
            ],
            "__typename": [
                4
            ]
        },
        "Query": {
            "title": [
                1,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "episode": [
                9,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "person": [
                11,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "search": [
                1,
                {
                    "query": [
                        4,
                        "String!"
                    ],
                    "mediaType": [
                        0
                    ],
                    "limit": [
                        3
                    ],
                    "filter": [
                        21
                    ]
                }
            ],
            "trending": [
                1,
                {
                    "mediaType": [
                        0
                    ]
                }
            ],
            "newAndComingSoon": [
                1,
                {
                    "mediaType": [
                        0,
                        "MediaType!"
                    ]
                }
            ],
            "watchedActors": [
                11,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaType": [
                        0,
                        "MediaType!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "moreWith": [
                22,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaType": [
                        0,
                        "MediaType!"
                    ]
                }
            ],
            "browse": [
                1,
                {
                    "mediaType": [
                        0,
                        "MediaType!"
                    ],
                    "genre": [
                        4
                    ],
                    "limit": [
                        3
                    ],
                    "offset": [
                        3
                    ]
                }
            ],
            "genres": [
                4
            ],
            "catalog": [
                1,
                {
                    "mediaType": [
                        0,
                        "MediaType!"
                    ],
                    "sort": [
                        46
                    ]
                }
            ],
            "catalogTheme": [
                1,
                {
                    "mediaType": [
                        0,
                        "MediaType!"
                    ],
                    "theme": [
                        47,
                        "CatalogTheme!"
                    ]
                }
            ],
            "catalogRows": [
                48,
                {
                    "mediaType": [
                        0,
                        "MediaType!"
                    ],
                    "count": [
                        3
                    ],
                    "exclude": [
                        4,
                        "[String!]"
                    ]
                }
            ],
            "recommendations": [
                20,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaType": [
                        0
                    ],
                    "limit": [
                        3
                    ],
                    "offset": [
                        3
                    ],
                    "refresh": [
                        6
                    ]
                }
            ],
            "recommendationRows": [
                23,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "rows": [
                        3
                    ],
                    "perRow": [
                        3
                    ]
                }
            ],
            "continueWatching": [
                18,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "continueRewatching": [
                18,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "history": [
                19,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "watchlist": [
                1,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "likedTitles": [
                1,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "notifications": [
                38,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "unreadNotificationCount": [
                3,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "downloads": [
                40,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "downloadsFolder": [
                4
            ],
            "togetherRoom": [
                43
            ],
            "subtitles": [
                25,
                {
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "profileId": [
                        2
                    ]
                }
            ],
            "profiles": [
                24
            ],
            "settings": [
                30
            ],
            "checkTmdbKey": [
                6,
                {
                    "key": [
                        4,
                        "String!"
                    ]
                }
            ],
            "plugins": [
                34
            ],
            "status": [
                36
            ],
            "neoworksAccount": [
                50
            ],
            "__typename": [
                4
            ]
        },
        "NeoworksAccount": {
            "signedIn": [
                6
            ],
            "email": [
                4
            ],
            "name": [
                4
            ],
            "lastSync": [
                51
            ],
            "__typename": [
                4
            ]
        },
        "NeoworksSync": {
            "at": [
                4
            ],
            "received": [
                3
            ],
            "sent": [
                3
            ],
            "failure": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "ChallengeKind": {},
        "Challenge": {
            "id": [
                2
            ],
            "kind": [
                52
            ],
            "url": [
                4
            ],
            "domain": [
                4
            ],
            "interactive": [
                6
            ],
            "click": [
                4
            ],
            "submit": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "Mutation": {
            "completeChallenge": [
                6,
                {
                    "id": [
                        2,
                        "ID!"
                    ],
                    "cookies": [
                        4,
                        "String!"
                    ],
                    "userAgent": [
                        4,
                        "String!"
                    ],
                    "finalUrl": [
                        4
                    ]
                }
            ],
            "failChallenge": [
                6,
                {
                    "id": [
                        2,
                        "ID!"
                    ],
                    "reason": [
                        4,
                        "String!"
                    ],
                    "linkGone": [
                        6
                    ]
                }
            ],
            "findStreams": [
                6,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "resolveStream": [
                16,
                {
                    "streamId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "resolveBestStream": [
                16,
                {
                    "streamIds": [
                        2,
                        "[ID!]!"
                    ]
                }
            ],
            "recordProgress": [
                17,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "progressSeconds": [
                        5,
                        "Float!"
                    ],
                    "durationSeconds": [
                        5,
                        "Float!"
                    ]
                }
            ],
            "startRewatch": [
                7,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "titleId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "stopRewatch": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "titleId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "setRating": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "rating": [
                        5,
                        "Float!"
                    ]
                }
            ],
            "clearRating": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "addToWatchlist": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "removeFromWatchlist": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "setNotifying": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "notifying": [
                        6,
                        "Boolean!"
                    ]
                }
            ],
            "markNotificationsRead": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "checkNotifications": [
                6
            ],
            "startDownload": [
                40,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "streamId": [
                        2
                    ]
                }
            ],
            "deleteDownload": [
                6,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "openDownloadsFolder": [
                6
            ],
            "hostTogether": [
                43,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "joinTogether": [
                43,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "code": [
                        4,
                        "String!"
                    ]
                }
            ],
            "leaveTogether": [
                6
            ],
            "adoptStream": [
                14,
                {
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "url": [
                        4,
                        "String!"
                    ],
                    "plugin": [
                        4,
                        "String!"
                    ],
                    "label": [
                        4
                    ]
                }
            ],
            "sendTogether": [
                6,
                {
                    "payload": [
                        4,
                        "String!"
                    ]
                }
            ],
            "createProfile": [
                24,
                {
                    "name": [
                        4,
                        "String!"
                    ],
                    "avatarUrl": [
                        4
                    ],
                    "pin": [
                        4
                    ],
                    "isChild": [
                        6
                    ],
                    "languages": [
                        4,
                        "[String!]"
                    ]
                }
            ],
            "updateProfile": [
                24,
                {
                    "id": [
                        2,
                        "ID!"
                    ],
                    "name": [
                        4
                    ],
                    "avatarUrl": [
                        4
                    ],
                    "isChild": [
                        6
                    ],
                    "pin": [
                        4
                    ],
                    "languages": [
                        4,
                        "[String!]"
                    ],
                    "subtitlesEnabled": [
                        6
                    ],
                    "subtitleLanguage": [
                        4
                    ],
                    "subtitleSize": [
                        26
                    ],
                    "subtitleStyle": [
                        27
                    ],
                    "voiceBoost": [
                        6
                    ]
                }
            ],
            "deleteProfile": [
                6,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "verifyPin": [
                28,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "pin": [
                        4,
                        "String!"
                    ]
                }
            ],
            "updateSettings": [
                30,
                {
                    "input": [
                        31,
                        "SettingsInput!"
                    ]
                }
            ],
            "checkTmdbConnection": [
                6
            ],
            "setPluginEnabled": [
                34,
                {
                    "name": [
                        4,
                        "String!"
                    ],
                    "enabled": [
                        6,
                        "Boolean!"
                    ]
                }
            ],
            "setPluginSettings": [
                34,
                {
                    "name": [
                        4,
                        "String!"
                    ],
                    "values": [
                        33,
                        "[PluginSettingInput!]!"
                    ]
                }
            ],
            "loginPlugin": [
                34,
                {
                    "name": [
                        4,
                        "String!"
                    ],
                    "username": [
                        4,
                        "String!"
                    ],
                    "password": [
                        4,
                        "String!"
                    ]
                }
            ],
            "logoutPlugin": [
                34,
                {
                    "name": [
                        4,
                        "String!"
                    ]
                }
            ],
            "updatePlugins": [
                56
            ],
            "startNeoworksSignIn": [
                4,
                {
                    "returnTo": [
                        4
                    ]
                }
            ],
            "completeNeoworksSignIn": [
                50,
                {
                    "code": [
                        4,
                        "String!"
                    ],
                    "state": [
                        4,
                        "String!"
                    ]
                }
            ],
            "signOutOfNeoworks": [
                50
            ],
            "syncNeoworks": [
                50
            ],
            "syncNeoworksSoon": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "PluginUpdate": {
            "name": [
                4
            ],
            "fromVersion": [
                4
            ],
            "toVersion": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "PluginUpdateReport": {
            "updated": [
                55
            ],
            "failed": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "Subscription": {
            "streams": [
                15,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "together": [
                45
            ],
            "challenges": [
                53
            ],
            "__typename": [
                4
            ]
        }
    }
}