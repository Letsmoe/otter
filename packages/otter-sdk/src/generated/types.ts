export default {
    "scalars": [
        0,
        2,
        3,
        4,
        5,
        6,
        25,
        27,
        34,
        36,
        38,
        43,
        44
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
                12
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
                7
            ],
            "seasonCount": [
                3
            ],
            "streams": [
                13
            ],
            "progress": [
                16,
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
            "__typename": [
                4
            ]
        },
        "ID": {},
        "Int": {},
        "String": {},
        "Float": {},
        "Boolean": {},
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
                8
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
                13
            ],
            "progress": [
                16,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "next": [
                8
            ],
            "skipSegments": [
                9
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
                11,
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
                10
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
                10
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
                13
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
                8
            ],
            "progress": [
                16
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
                8
            ],
            "progress": [
                16
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
                10
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
                25
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
                27
            ],
            "usesDefaultTmdbKey": [
                6
            ],
            "tmdbKeyPromptSeen": [
                6
            ],
            "requireVpn": [
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
                30
            ],
            "__typename": [
                4
            ]
        },
        "Status": {
            "tmdbConfigured": [
                6
            ],
            "vpnConnected": [
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
                34
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
                8
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
                36
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
                8
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
                38
            ],
            "self": [
                39
            ],
            "peers": [
                39
            ],
            "__typename": [
                4
            ]
        },
        "TogetherMessage": {
            "from": [
                39
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
                40
            ],
            "message": [
                41
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
                8,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "person": [
                10,
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
                        20
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
                10,
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
                21,
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
                        43
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
                        44,
                        "CatalogTheme!"
                    ]
                }
            ],
            "catalogRows": [
                45,
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
                19,
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
                22,
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
                17,
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
                35,
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
                37,
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
                40
            ],
            "subtitles": [
                24,
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
                23
            ],
            "settings": [
                28
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
                32
            ],
            "status": [
                33
            ],
            "__typename": [
                4
            ]
        },
        "Mutation": {
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
                15,
                {
                    "streamId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "resolveBestStream": [
                15,
                {
                    "streamIds": [
                        2,
                        "[ID!]!"
                    ]
                }
            ],
            "recordProgress": [
                16,
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
            "markNotificationsRead": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "startDownload": [
                37,
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
                40,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "joinTogether": [
                40,
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
                13,
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
                23,
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
                23,
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
                        25
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
                26,
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
                28,
                {
                    "input": [
                        29,
                        "SettingsInput!"
                    ]
                }
            ],
            "setPluginEnabled": [
                32,
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
                32,
                {
                    "name": [
                        4,
                        "String!"
                    ],
                    "values": [
                        31,
                        "[PluginSettingInput!]!"
                    ]
                }
            ],
            "__typename": [
                4
            ]
        },
        "Subscription": {
            "streams": [
                14,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "together": [
                42
            ],
            "__typename": [
                4
            ]
        }
    }
}