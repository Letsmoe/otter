package com.letsmoe.otter.cast.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherLayerTest {

    private val layer = TogetherLayer()
    private val laugh = TogetherReaction(7, "😂", "Anna")
    private val hello = TogetherChatLine(8, "Anna", "so good")

    @Test
    fun whatWasThereBeforeTheStreamIsNotDrawn() {
        val before = TogetherMoments(listOf(laugh), listOf(hello))

        val marks = layer.marksAt(100.0, before)

        assertTrue(marks.reactions.isEmpty())
        assertTrue(marks.chat.isEmpty())
    }

    @Test
    fun aReactionRisesFromWhenItArrivedAndIsGoneAfterItsSeconds() {
        layer.marksAt(100.0, TogetherMoments())

        val arrived = layer.marksAt(101.0, TogetherMoments(listOf(laugh)))
        val halfway = layer.marksAt(102.3, TogetherMoments(listOf(laugh)))
        val gone = layer.marksAt(103.7, TogetherMoments(listOf(laugh)))

        assertEquals(0f, arrived.reactions.single().progress, 0.001f)
        assertEquals(0.5f, halfway.reactions.single().progress, 0.02f)
        assertTrue(gone.reactions.isEmpty())
    }

    @Test
    fun aChatLineStaysForSevenSeconds() {
        layer.marksAt(100.0, TogetherMoments())

        assertEquals(listOf(hello), layer.marksAt(101.0, TogetherMoments(chat = listOf(hello))).chat)
        assertEquals(listOf(hello), layer.marksAt(107.9, TogetherMoments(chat = listOf(hello))).chat)
        assertTrue(layer.marksAt(108.0, TogetherMoments(chat = listOf(hello))).chat.isEmpty())
    }
}
