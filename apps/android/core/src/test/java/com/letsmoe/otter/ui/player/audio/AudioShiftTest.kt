package com.letsmoe.otter.ui.player.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioShiftTest {

    private val shift = AudioShift()

    @Test
    fun aLaterSoundGetsSilenceOnceThenNothing() {
        shift.moveTo(2400)

        assertEquals(2400, shift.takeSilence())
        assertEquals(0, shift.takeSilence())
        assertEquals(0, shift.takeDrop(1000))
    }

    @Test
    fun anEarlierSoundLosesFramesAcrossBuffers() {
        shift.moveTo(-2400)

        assertEquals(0, shift.takeSilence())
        assertEquals(1000, shift.takeDrop(1000))
        assertEquals(1000, shift.takeDrop(1000))
        assertEquals(400, shift.takeDrop(1000))
        assertEquals(0, shift.takeDrop(1000))
    }

    @Test
    fun changingTheTargetMovesOnlyTheDifference() {
        shift.moveTo(2400)
        shift.takeSilence()

        shift.moveTo(3600)
        assertEquals(1200, shift.takeSilence())

        shift.moveTo(2400)
        assertEquals(1200, shift.takeDrop(5000))
    }

    @Test
    fun aLaterSoundThatIsMovedBackBeforeItsSilenceCameCancelsIt() {
        shift.moveTo(2400)
        shift.moveTo(0)

        assertEquals(0, shift.takeSilence())
        assertEquals(0, shift.takeDrop(1000))
    }

    @Test
    fun resettingStartsFromTheUnmovedSound() {
        shift.moveTo(2400)
        shift.takeSilence()
        shift.reset()

        shift.moveTo(2400)
        assertEquals(2400, shift.takeSilence())
    }

    @Test
    fun theDelayStaysWithinHalfASecondInWholeSteps() {
        assertEquals(50, steppedAudioDelay(0, 50))
        assertEquals(-50, steppedAudioDelay(0, -50))
        assertEquals(500, steppedAudioDelay(500, 50))
        assertEquals(-500, steppedAudioDelay(-450, -100))
    }

    @Test
    fun theDelayReadsAsSignedMilliseconds() {
        assertEquals("0 ms", audioDelayLabel(0))
        assertEquals("+150 ms", audioDelayLabel(150))
        assertEquals("−50 ms", audioDelayLabel(-50))
    }
}
