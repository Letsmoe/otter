export default {
    "scalars": [
        0,
        2,
        3,
        4,
        5,
        6,
        10,
        29,
        42,
        43,
        45,
        53,
        55,
        57,
        62,
        63,
        66,
        72
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
            "hasPostCreditsScene": [
                6
            ],
            "detailFetched": [
                6
            ],
            "collectionId": [
                2
            ],
            "credits": [
                14
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
                15
            ],
            "progress": [
                20,
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
            "ageRating": [
                4,
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
                15
            ],
            "progress": [
                20,
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
                11
            ],
            "filler": [
                10
            ],
            "__typename": [
                4
            ]
        },
        "FillerKind": {},
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
                13,
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
                12
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
                12
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
            "report": [
                16
            ],
            "__typename": [
                4
            ]
        },
        "LinkReport": {
            "claimedHeight": [
                3
            ],
            "measuredHeight": [
                3
            ],
            "overclaimed": [
                6
            ],
            "bitrateBps": [
                3
            ],
            "stalls": [
                3
            ],
            "stallMillis": [
                3
            ],
            "firstFrameMillis": [
                3
            ],
            "plays": [
                3
            ],
            "roughPlays": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "LinkReportInput": {
            "streamId": [
                2
            ],
            "widthPixels": [
                3
            ],
            "heightPixels": [
                3
            ],
            "bitrateBps": [
                3
            ],
            "stalls": [
                3
            ],
            "stallMillis": [
                3
            ],
            "firstFrameMillis": [
                3
            ],
            "playedMillis": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "StreamUpdate": {
            "streams": [
                15
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
        "Bookmark": {
            "id": [
                2
            ],
            "mediaId": [
                2
            ],
            "positionSeconds": [
                5
            ],
            "caption": [
                4
            ],
            "createdAt": [
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
                20
            ],
            "__typename": [
                4
            ]
        },
        "TimeSlotItem": {
            "title": [
                1
            ],
            "episode": [
                9
            ],
            "minutes": [
                3
            ],
            "reason": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "UpcomingEpisode": {
            "series": [
                1
            ],
            "episode": [
                9
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
                20
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
                12
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
        "WatchStatus": {},
        "Collection": {
            "id": [
                2
            ],
            "tmdbId": [
                3
            ],
            "name": [
                4
            ],
            "overview": [
                4
            ],
            "posterPath": [
                4
            ],
            "backdropPath": [
                4
            ],
            "parts": [
                31
            ],
            "watchedCount": [
                3
            ],
            "next": [
                31
            ],
            "__typename": [
                4
            ]
        },
        "CollectionPart": {
            "number": [
                3
            ],
            "title": [
                1
            ],
            "status": [
                29
            ],
            "progress": [
                5
            ],
            "released": [
                6
            ],
            "__typename": [
                4
            ]
        },
        "Shelf": {
            "id": [
                2
            ],
            "name": [
                4
            ],
            "titles": [
                1
            ],
            "__typename": [
                4
            ]
        },
        "YearInReview": {
            "year": [
                3
            ],
            "hasActivity": [
                6
            ],
            "hoursWatched": [
                5
            ],
            "moviesFinished": [
                3
            ],
            "episodesFinished": [
                3
            ],
            "seriesWatched": [
                3
            ],
            "topSeries": [
                34
            ],
            "topGenres": [
                35
            ],
            "longestBinge": [
                36
            ],
            "mostRewatched": [
                37
            ],
            "firstTitle": [
                38
            ],
            "lastTitle": [
                38
            ],
            "__typename": [
                4
            ]
        },
        "TopSeries": {
            "title": [
                1
            ],
            "hoursWatched": [
                5
            ],
            "episodesWatched": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "GenreShare": {
            "genre": [
                4
            ],
            "hoursWatched": [
                5
            ],
            "share": [
                5
            ],
            "__typename": [
                4
            ]
        },
        "Binge": {
            "title": [
                1
            ],
            "episodes": [
                3
            ],
            "date": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "Rewatched": {
            "title": [
                1
            ],
            "count": [
                3
            ],
            "__typename": [
                4
            ]
        },
        "YearMoment": {
            "title": [
                1
            ],
            "date": [
                4
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
                42
            ],
            "subtitleStyle": [
                43
            ],
            "voiceBoost": [
                6
            ],
            "ageLimit": [
                3
            ],
            "bedtimeMinutes": [
                3
            ],
            "simpleMode": [
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
                45
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
                48
            ],
            "account": [
                51
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
                53
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
                55
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
                57
            ],
            "self": [
                58
            ],
            "peers": [
                58
            ],
            "__typename": [
                4
            ]
        },
        "TogetherMessage": {
            "from": [
                58
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
                59
            ],
            "message": [
                60
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
                12,
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
                        27
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
                12,
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
            "collection": [
                30,
                {
                    "id": [
                        2,
                        "ID!"
                    ],
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "franchises": [
                30,
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
            "moreWith": [
                28,
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
                        62
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
                        63,
                        "CatalogTheme!"
                    ]
                }
            ],
            "catalogRows": [
                64,
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
                26,
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
                39,
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
                22,
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
            "timeSlot": [
                23,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "minutes": [
                        3,
                        "Int!"
                    ],
                    "limit": [
                        3
                    ]
                }
            ],
            "upcomingEpisodes": [
                24,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "days": [
                        3
                    ]
                }
            ],
            "continueRewatching": [
                22,
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
                25,
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
            "bookmarks": [
                21,
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
            "shelves": [
                32,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "yearInReview": [
                33,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "year": [
                        3,
                        "Int!"
                    ],
                    "utcOffsetMinutes": [
                        3
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
                54,
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
                56,
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
                59
            ],
            "subtitles": [
                41,
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
                40
            ],
            "calendarFeedPath": [
                4,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "settings": [
                46
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
                50
            ],
            "status": [
                52
            ],
            "neoworksAccount": [
                70
            ],
            "letterboxdImport": [
                67,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "letterboxdAccount": [
                69,
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
        "LetterboxdImportState": {},
        "LetterboxdImport": {
            "state": [
                66
            ],
            "total": [
                3
            ],
            "processed": [
                3
            ],
            "matched": [
                3
            ],
            "watched": [
                3
            ],
            "rated": [
                3
            ],
            "watchlisted": [
                3
            ],
            "unmatchedCount": [
                3
            ],
            "notMatched": [
                68
            ],
            "error": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "LetterboxdFilm": {
            "name": [
                4
            ],
            "year": [
                3
            ],
            "uri": [
                4
            ],
            "reason": [
                4
            ],
            "__typename": [
                4
            ]
        },
        "LetterboxdAccount": {
            "username": [
                4
            ],
            "lastSyncedAt": [
                4
            ],
            "lastError": [
                4
            ],
            "lastWatched": [
                3
            ],
            "lastRated": [
                3
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
                71
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
                72
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
                19,
                {
                    "streamId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "resolveBestStream": [
                19,
                {
                    "streamIds": [
                        2,
                        "[ID!]!"
                    ]
                }
            ],
            "reportLinkPlayback": [
                6,
                {
                    "input": [
                        17,
                        "LinkReportInput!"
                    ]
                }
            ],
            "recordProgress": [
                20,
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
            "dismissRecommendation": [
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
            "restoreRecommendation": [
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
            "addBookmark": [
                21,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ],
                    "positionSeconds": [
                        5,
                        "Float!"
                    ],
                    "caption": [
                        4
                    ]
                }
            ],
            "removeBookmark": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "bookmarkId": [
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
            "createShelf": [
                32,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "name": [
                        4,
                        "String!"
                    ]
                }
            ],
            "renameShelf": [
                32,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "shelfId": [
                        2,
                        "ID!"
                    ],
                    "name": [
                        4,
                        "String!"
                    ]
                }
            ],
            "deleteShelf": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "shelfId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "addToShelf": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "shelfId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "removeFromShelf": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "shelfId": [
                        2,
                        "ID!"
                    ],
                    "mediaId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "reorderShelves": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "shelfIds": [
                        2,
                        "[ID!]!"
                    ]
                }
            ],
            "reorderShelfTitles": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "shelfId": [
                        2,
                        "ID!"
                    ],
                    "mediaIds": [
                        2,
                        "[ID!]!"
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
                56,
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
                59,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "joinTogether": [
                59,
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
                15,
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
                40,
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
                40,
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
                        42
                    ],
                    "subtitleStyle": [
                        43
                    ],
                    "voiceBoost": [
                        6
                    ],
                    "ageLimit": [
                        3
                    ],
                    "bedtimeMinutes": [
                        3
                    ],
                    "simpleMode": [
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
                44,
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
            "resetCalendarFeed": [
                4,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "updateSettings": [
                46,
                {
                    "input": [
                        47,
                        "SettingsInput!"
                    ]
                }
            ],
            "checkTmdbConnection": [
                6
            ],
            "setPluginEnabled": [
                50,
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
                50,
                {
                    "name": [
                        4,
                        "String!"
                    ],
                    "values": [
                        49,
                        "[PluginSettingInput!]!"
                    ]
                }
            ],
            "loginPlugin": [
                50,
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
                50,
                {
                    "name": [
                        4,
                        "String!"
                    ]
                }
            ],
            "updatePlugins": [
                76
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
                70,
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
                70
            ],
            "syncNeoworks": [
                70
            ],
            "syncNeoworksSoon": [
                6
            ],
            "importLetterboxd": [
                67,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "zipBase64": [
                        4,
                        "String!"
                    ]
                }
            ],
            "connectLetterboxd": [
                69,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ],
                    "username": [
                        4,
                        "String!"
                    ]
                }
            ],
            "disconnectLetterboxd": [
                6,
                {
                    "profileId": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "syncLetterboxd": [
                69,
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
                75
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
                18,
                {
                    "id": [
                        2,
                        "ID!"
                    ]
                }
            ],
            "together": [
                61
            ],
            "challenges": [
                73
            ],
            "__typename": [
                4
            ]
        }
    }
}