package com.letsmoe.otter.ui.profiles

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.VerifyPinMutation

/** Asks the backend whether [pin] opens the profile; returns an error message to show, or null when it does. */
suspend fun checkProfilePin(profileId: String, pin: String): String? {
    val result = try {
        OtterApi.client.mutation(VerifyPinMutation(profileId, pin)).execute().dataOrThrow().verifyPin
    } catch (error: Exception) {
        return error.message ?: "Could not check the PIN."
    }
    if (result.success) {
        return null
    }
    return pinFailureMessage(result.waitSeconds)
}

private fun pinFailureMessage(waitSeconds: Int): String {
    if (waitSeconds > 0) {
        return "Too many attempts. Try again in ${waitSeconds}s."
    }
    return "Incorrect PIN. Try again."
}
