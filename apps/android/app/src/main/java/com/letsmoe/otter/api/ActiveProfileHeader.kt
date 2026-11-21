package com.letsmoe.otter.api

import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpResponse
import com.apollographql.apollo.network.http.HttpInterceptor
import com.apollographql.apollo.network.http.HttpInterceptorChain
import com.letsmoe.otter.profiles.ProfileSession

private const val PROFILE_HEADER = "X-Otter-Profile"

/**
 * Tells the backend which profile asks, so it can leave out what the profile's age limit holds
 * back (search, home rows, related titles) without every query naming the profile.
 */
class ActiveProfileHeader : HttpInterceptor {

    override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse {
        val profileId = ProfileSession.activeProfile.value?.id
        if (profileId == null) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().addHeader(PROFILE_HEADER, profileId).build())
    }
}
