package org.rph.pkd.utils

import org.rph.pkd.PKDPlugin
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

class PingSimulator(private val plugin: PKDPlugin) {
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Internal ping simulation parameters (not user-configurable)
    private val variancePercent = 0.20        // 20% variance from base ping
    private val jitterMs = 5L                 // ±5ms random jitter
    private val spikeChance = 0.05            // 5% chance of ping spikes
    private val spikeMultiplier = 2.5         // 2.5x multiplier during spikes
    private val minPingMs = 1L                // Minimum ping floor
    private val maxPingMs = 2000L             // Maximum ping ceiling

    private fun getBasePing(key: String): Long {
        return when (val field = plugin.getConfigField(key)) {
            is Number -> field.toLong().coerceAtLeast(0L)
            is String -> field.toLongOrNull()?.coerceAtLeast(0L) ?: 50L
            else -> 50L
        }
    }

    /**
     * Calculates a realistic ping value with various factors:
     * - Base ping with gaussian variance
     * - Random jitter
     * - Occasional ping spikes
     * - Min/max bounds
     */
    private fun calculateRealisticPing(key: String): Long {
        val basePing = getBasePing(key)
        val random = ThreadLocalRandom.current()

        // Start with base ping
        var ping = basePing.toDouble()

        // Add gaussian variance (percentage-based variance from base ping)
        val varianceAmount = basePing * variancePercent
        val gaussianVariance = random.nextGaussian() * varianceAmount
        ping += gaussianVariance

        // Add random jitter (uniform distribution)
        val randomJitter = random.nextDouble(-jitterMs.toDouble(), jitterMs.toDouble())
        ping += randomJitter

        // Occasional ping spikes
        if (random.nextDouble() < spikeChance) {
            ping *= spikeMultiplier
        }

        // Ensure ping is within bounds
        return ping.toLong().coerceIn(minPingMs, maxPingMs)
    }

    /**
     * Runs an action with simulated network ping delay
     */
    fun runWithPing(key: String = "ping", action: Runnable) {
        val pingMs = calculateRealisticPing(key)
        scheduler.schedule({
            action.run()
        }, pingMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Runs an action with a specific ping value plus realistic variations
     */
    fun runWithPing(basePingMs: Long, action: Runnable) {
        val random = ThreadLocalRandom.current()
        var ping = basePingMs.toDouble()

        // Add variance and jitter based on internal parameters
        val varianceAmount = basePingMs * variancePercent
        ping += random.nextGaussian() * varianceAmount
        ping += random.nextDouble(-jitterMs.toDouble(), jitterMs.toDouble())

        val finalPing = ping.toLong().coerceIn(minPingMs, maxPingMs)

        scheduler.schedule({
            action.run()
        }, finalPing, TimeUnit.MILLISECONDS)
    }

    /**
     * Gets the current calculated ping without executing anything
     */
    fun getCurrentPing(key: String = "ping"): Long {
        return calculateRealisticPing(key)
    }

    fun shutdown() {
        scheduler.shutdownNow()
    }
}