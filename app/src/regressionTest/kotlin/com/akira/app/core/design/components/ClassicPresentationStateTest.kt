package com.akira.app.core.design.components

import org.junit.Assert.*
import org.junit.Test

class ClassicPresentationStateTest {
    private fun entry(onDismiss: () -> Unit = {}) =
        AkiraPresentationEntry(AkiraPresentationKind.Menu, null, null, false, {}, onDismiss)

    @Test fun `returning from a nested classic selector retains its parent menu`() {
        val state = AkiraPresentationState(null)
        val parent = entry()
        var closed = 0
        val child = entry { closed++ }
        state.add(parent, false); state.add(child, false)
        state.closing = child
        assertSame(parent, state.desiredEntry)
        state.finishClose()
        assertEquals(listOf(parent), state.entries.toList())
        assertEquals(1, closed)
    }

    @Test fun `a direct menu action dismisses the entire chain once`() {
        val state = AkiraPresentationState(null)
        var closed = 0
        state.add(entry { closed++ }, false); state.add(entry { closed++ }, false)
        state.dismissAll(); state.dismissAll(); state.finishClose(); state.finishClose()
        assertTrue(state.entries.isEmpty())
        assertEquals(2, closed)
    }

    @Test fun `detached callers do not receive a duplicate close callback`() {
        val state = AkiraPresentationState(null)
        var closed = 0
        val item = entry { closed++ }
        state.add(item, false); item.removed = true; state.closing = item
        state.finishClose()
        assertTrue(state.entries.isEmpty())
        assertEquals(0, closed)
    }

    @Test fun `classic keeps its opening control visible while glass can transfer it`() {
        val anchors = AkiraMenuAnchors()
        val source = AkiraMenuSource(anchors)
        val item = AkiraPresentationEntry(AkiraPresentationKind.Menu, null, source, false, {}, {})
        val state = AkiraPresentationState(anchors)
        state.add(item, false)
        assertNull(anchors.hiddenSource)
        item.removed = true; state.release()
        // A separate Glass opening retains the original source-transfer contract.
        val glassAnchors = AkiraMenuAnchors()
        val glassSource = AkiraMenuSource(glassAnchors)
        AkiraPresentationState(glassAnchors).add(AkiraPresentationEntry(AkiraPresentationKind.Menu, null, glassSource, false, {}, {}), true)
        assertSame(glassSource, glassAnchors.hiddenSource)
    }
}
