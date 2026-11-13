package com.letsmoe.otter.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {

    @Test
    fun invitesAreOtterLetsmoeLinks() {
        assertEquals("https://otter.letsmoe.com/together/K7QD9MZA", inviteLink("K7QD9MZA"))
        assertEquals("K7QD9MZA", roomCodeFromInviteLink(inviteLink("K7QD9MZA")))
    }

    @Test
    fun otherLinksAreNoInvites() {
        assertNull(roomCodeFromInviteLink("https://otter.letsmoe.com/movie/603-the-matrix"))
        assertNull(roomCodeFromInviteLink("otter://together/K7QD9MZA"))
    }
}
