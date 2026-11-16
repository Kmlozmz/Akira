package com.akira.app.core.design.rendering

import java.util.concurrent.Executor

/** Only unused instances enter this pool. A consumer permanently owns each acquired shader. */
internal class PreparedShaderPool<T : Any>(private val executor: Executor, private val factory: (String) -> T,
    private val capacity: Int = 4, private val maxSources: Int = 8) {
    private class Slot<T>(val ready: ArrayDeque<T> = ArrayDeque(), var filling: Boolean = false)
    private val slots = mutableMapOf<String, Slot<T>>()

    fun prepare(source: String) {
        val slot = synchronized(slots) {
            val item = slots[source] ?: if (slots.size < maxSources) Slot<T>().also { slots[source] = it } else return
            if (item.filling || item.ready.size >= capacity) return
            item.filling = true
            item
        }
        executor.execute {
            try {
                while (synchronized(slots) { slot.ready.size < capacity }) {
                    val shader = factory(source)
                    synchronized(slots) { slot.ready.addLast(shader) }
                }
            } catch (_: Exception) {
                // The normal draw path retains the renderer's existing failure handling.
            } finally { synchronized(slots) { slot.filling = false } }
        }
    }

    fun acquire(source: String): T {
        val ready = synchronized(slots) { slots[source]?.ready?.removeFirstOrNull() }
        // Never share a mutable RuntimeShader between surfaces, even when the AGSL is identical.
        val result = ready ?: factory(source)
        prepare(source)
        return result
    }
}
