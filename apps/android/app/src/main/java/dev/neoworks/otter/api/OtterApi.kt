package dev.neoworks.otter.api

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.network.websocket.GraphQLWsProtocol
import com.apollographql.apollo.network.websocket.WebSocketNetworkTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The embedded backend's GraphQL API: queries over HTTP, subscriptions over graphql-ws. */
object OtterApi {

    private const val HEALTH_POLL_MILLISECONDS = 250L

    val client: ApolloClient = ApolloClient.Builder()
        .serverUrl("${ServerSettings.baseUrl}/graphql")
        .subscriptionNetworkTransport(
            WebSocketNetworkTransport.Builder()
                .serverUrl("${ServerSettings.baseUrl.replace("http", "ws")}/graphql")
                .wsProtocol(GraphQLWsProtocol())
                .build()
        )
        .build()

    /** Suspends until the backend answers /health; it takes a moment to start after launch. */
    suspend fun awaitBackend() {
        while (!isHealthy()) {
            delay(HEALTH_POLL_MILLISECONDS)
        }
    }

    private suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL("${ServerSettings.baseUrl}/health").openConnection() as HttpURLConnection
            connection.connectTimeout = 500
            connection.readTimeout = 500
            val healthy = connection.responseCode == 200
            connection.disconnect()
            healthy
        } catch (error: IOException) {
            false
        }
    }
}

/** The response's data, or an exception carrying the server's error message. */
fun <D : Operation.Data> ApolloResponse<D>.dataOrThrow(): D {
    val graphqlError = errors?.firstOrNull()
    if (graphqlError != null) {
        throw IllegalStateException(graphqlError.message)
    }
    return data ?: throw exception ?: IllegalStateException("empty response")
}
