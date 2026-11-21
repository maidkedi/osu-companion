package com.osuchat.app

import com.osuchat.app.data.irc.IrcRateLimiter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrcRateLimiterTest {

    private class FakeClock {
        var nowMs: Long = 10_000L
        val recordedDelays = mutableListOf<Long>()

        val delayFn: suspend (Long) -> Unit = { ms ->
            recordedDelays.add(ms)
            nowMs += ms
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsZeroCapacity() {
        IrcRateLimiter(capacity = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsNonPositiveRefillInterval() {
        IrcRateLimiter(refillIntervalMs = 0L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsNegativeMinSpacing() {
        IrcRateLimiter(minSpacingMs = -1L)
    }

    @Test
    fun acquire_allowsBurstUpToCapacityWhenSpacingRespected() = runBlocking {
        val clock = FakeClock()
        val limiter = IrcRateLimiter(
            capacity = 4,
            refillIntervalMs = 1_000L,
            minSpacingMs = 200L,
            nowMs = { clock.nowMs },
            delayFn = clock.delayFn
        )

        assertEquals(4.0, limiter.availableTokens(), 1e-6)

        // First permit needs zero wait; subsequent permits within burst wait only minSpacingMs if immediate
        assertEquals(0L, limiter.acquire())
        assertEquals(3.0, limiter.availableTokens(), 1e-6)

        clock.nowMs += 200L
        assertEquals(0L, limiter.acquire())

        // Immediate third call waits minSpacingMs = 200ms
        assertEquals(200L, limiter.acquire())
        assertEquals(listOf(200L), clock.recordedDelays)

        clock.nowMs += 200L
        assertEquals(0L, limiter.acquire())
        assertTrue(limiter.availableTokens() < 1.0)
    }

    @Test
    fun acquire_waitsForTokenRefillWhenBucketExhausted() = runBlocking {
        val clock = FakeClock()
        val limiter = IrcRateLimiter(
            capacity = 2,
            refillIntervalMs = 1_200L,
            minSpacingMs = 250L,
            nowMs = { clock.nowMs },
            delayFn = clock.delayFn
        )

        assertEquals(0L, limiter.acquire()) // 2 -> 1
        clock.nowMs += 250L
        // 250ms added 250/1200 tokens (~1.208), consume 1 -> ~0.208 left
        assertEquals(0L, limiter.acquire())

        // Next immediate call needs maxOf(spacing=250ms, tokenRefill=ceil((1-0.20833)*1200)=950ms)
        val waited = limiter.acquire()
        assertTrue("Expected wait >= 900ms for refill, got $waited", waited >= 900L)
        assertEquals(waited, clock.recordedDelays.sum())
    }

    @Test
    fun tryAcquire_enforcesSpacingAndCapacityNonBlocking() = runBlocking {
        val clock = FakeClock()
        val limiter = IrcRateLimiter(
            capacity = 2,
            refillIntervalMs = 1_000L,
            minSpacingMs = 300L,
            nowMs = { clock.nowMs },
            delayFn = clock.delayFn
        )

        assertTrue(limiter.tryAcquire())
        // Too soon (< 300ms spacing)
        clock.nowMs += 100L
        assertFalse(limiter.tryAcquire())

        // After spacing (total +300ms): 0.3 tokens refilled -> 1.3 tokens available -> succeeds
        clock.nowMs += 200L
        assertTrue(limiter.tryAcquire())
        assertTrue(clock.recordedDelays.isEmpty())

        // Bucket has ~0.3 tokens left; even after 300ms spacing (~0.6 tokens) still < 1.0
        clock.nowMs += 300L
        assertFalse(limiter.tryAcquire())

        // After another 450ms (~1.05 tokens) succeeds
        clock.nowMs += 450L
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun refill_capsAtCapacityAndResetRestoresFullBucketAndClearsCooldown() = runBlocking {
        val clock = FakeClock()
        val limiter = IrcRateLimiter(
            capacity = 3,
            refillIntervalMs = 500L,
            minSpacingMs = 250L,
            nowMs = { clock.nowMs },
            delayFn = clock.delayFn
        )

        assertTrue(limiter.tryAcquire())
        clock.nowMs += 60_000L
        assertEquals(3.0, limiter.availableTokens(), 1e-6)

        assertTrue(limiter.tryAcquire())
        // Immediately blocked by minSpacingMs
        assertFalse(limiter.tryAcquire())

        limiter.reset()
        assertEquals(3.0, limiter.availableTokens(), 1e-6)
        // Cooldown cleared by reset()
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun zeroMinSpacing_allowsBackToBackBurstUntilBucketEmpty() = runBlocking {
        val clock = FakeClock()
        val limiter = IrcRateLimiter(
            capacity = IrcRateLimiter.DEFAULT_CAPACITY,
            refillIntervalMs = IrcRateLimiter.DEFAULT_REFILL_INTERVAL_MS,
            minSpacingMs = 0L,
            nowMs = { clock.nowMs },
            delayFn = clock.delayFn
        )

        repeat(IrcRateLimiter.DEFAULT_CAPACITY) {
            assertEquals(0L, limiter.acquire())
        }
        assertEquals(0.0, limiter.availableTokens(), 1e-6)
        assertEquals(IrcRateLimiter.DEFAULT_REFILL_INTERVAL_MS, limiter.acquire())
    }

    @Test
    fun concurrentAcquire_maintainsSpacingAndNeverNegativeTokens() = runBlocking {
        val clock = FakeClock()
        val lock = Any()
        val limiter = IrcRateLimiter(
            capacity = 3,
            refillIntervalMs = 1_000L,
            minSpacingMs = 200L,
            nowMs = { synchronized(lock) { clock.nowMs } },
            delayFn = { ms ->
                synchronized(lock) {
                    clock.recordedDelays.add(ms)
                    clock.nowMs += ms
                }
            }
        )

        val waits = (1..6).map {
            async { limiter.acquire() }
        }.awaitAll()

        assertTrue(waits.any { it > 0L })
        assertTrue(limiter.availableTokens() >= 0.0)
    }
}
