package com.akira.app.core.design.rendering

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class PreparedShaderPoolTest {
    private class Jobs : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.addLast(command) }
        fun run() { while (jobs.isNotEmpty()) jobs.removeFirst().run() }
    }

    @Test fun preparedInstancesAreExclusiveAndRefillOffTheConsumerPath() {
        val work = Jobs()
        val created = mutableListOf<Any>()
        val pool = PreparedShaderPool(work, { _: String -> Any().also(created::add) }, capacity = 3)
        pool.prepare("glass")
        assertTrue(created.isEmpty())
        work.run()
        assertEquals(3, created.size)
        val first = pool.acquire("glass")
        val second = pool.acquire("glass")
        assertNotSame(first, second)
        assertEquals(3, created.size)
        assertEquals(1, work.jobs.size)
        work.run()
        assertEquals(5, created.size)
        assertNotSame(first, pool.acquire("glass"))
    }

    @Test fun sourceIsolationAndTheSourceLimitDoNotChangeFallbackBehaviour() {
        val work = Jobs()
        val pool = PreparedShaderPool(work, { source: String -> source to Any() }, capacity = 1, maxSources = 1)
        pool.prepare("one")
        pool.prepare("two")
        assertEquals(1, work.jobs.size)
        work.run()
        assertEquals("two", pool.acquire("two").first)
        assertEquals("one", pool.acquire("one").first)
        assertEquals(1, work.jobs.size)
    }

    @Test fun failedPreparationCanBeRetriedWithoutLosingOwnership() {
        val work = Jobs()
        var fails = true
        val pool = PreparedShaderPool(work, { _: String -> if (fails) throw IllegalArgumentException() else Any() }, capacity = 1)
        pool.prepare("glass"); work.run()
        fails = false
        pool.prepare("glass"); work.run()
        assertNotSame(pool.acquire("glass"), pool.acquire("glass"))
    }
}
