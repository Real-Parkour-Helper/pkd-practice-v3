package org.rph.pkd.utils

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.util.Vector
import org.rph.pkd.PKDPlugin
import java.util.*
import kotlin.math.floor


class SlimeLagback(private val plugin: PKDPlugin) : Listener {

    private val playerData = mutableMapOf<UUID, PlayerBounceData>()
    private val oldWorld = mutableMapOf<UUID, String>()

    data class PlayerBounceData(
        var lastY: Double = 0.0,
        var lastVelocityY: Double = 0.0,
        var velocityHistory: MutableList<Double> = mutableListOf(),
        var ticksSinceBounce: Int = 0,
        var lastX: Double = 0.0,
        var lastZ: Double = 0.0,
        var positionHistory: MutableList<Triple<Double, Double, Double>> = mutableListOf()
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player

        val w = oldWorld.getOrElse(player.uniqueId) { "" }
        oldWorld[player.uniqueId] = player.world.name
        if (w != "" && w != player.world.name) {
            return
        }

        val to = event.to ?: return
        val from = event.from

        // Skip if player didn't actually move vertically
        if (from.y == to.y) return

        val uuid = player.uniqueId
        val data = playerData.getOrPut(uuid) {
            PlayerBounceData().apply {
                lastY = from.y
                lastX = from.x
                lastZ = from.z
            }
        }

        val currentY = to.y
        val currentX = to.x
        val currentZ = to.z
        val velocityY = currentY - data.lastY

        // Add to velocity history (keep last 10 ticks for better pattern detection)
        data.velocityHistory.add(velocityY)
        if (data.velocityHistory.size > 10) {
            data.velocityHistory.removeAt(0)
        }

        // Add to position history (keep last 5 positions for bounce detection)
        data.positionHistory.add(Triple(currentX, currentY, currentZ))
        if (data.positionHistory.size > 5) {
            data.positionHistory.removeAt(0)
        }

        data.ticksSinceBounce++

        // Debug output (remove this in production)
//        if (velocityY > 0.1) {
//            player.sendMessage("§7VelY: §e${String.format("%.3f", velocityY)} §7Change: §e${String.format("%.3f", velocityY - data.lastVelocityY)}")
//        }

        // Check for slime bounce
        if (detectSlimeBounce(player, data, currentY, velocityY)) {
            onSlimeBounce(player, player.location.block)
            data.ticksSinceBounce = 0
        }

        // Update player data
        data.lastY = currentY
        data.lastX = currentX
        data.lastZ = currentZ
        data.lastVelocityY = velocityY
    }

    private fun detectSlimeBounce(
        player: Player,
        data: PlayerBounceData,
        currentY: Double,
        velocityY: Double
    ): Boolean {
        // Prevent spam detection
        if (data.ticksSinceBounce < 5) return false

        // Must be moving upward
        if (velocityY <= 0.05) return false

        // Check bounce height - ignore small bounces (regular jumps)
        // Regular player jumps reach about 1.25 blocks, slime bounces are much higher
        val bounceHeight = estimateBounceHeight(velocityY)
        if (bounceHeight < 1.5) return false

        // Check for velocity patterns that indicate a bounce
        val isBouncePattern = checkBouncePattern(data.velocityHistory, velocityY)

        if (!isBouncePattern) return false

        // Check if there's a slime block that could have caused this bounce
        // Look at recent positions, not just current position
        return wasAboveSlimeBlockRecently(player, data, currentY)
    }

    private fun estimateBounceHeight(initialVelocity: Double): Double {
        // Physics calculation: h = v²/(2*g)
        // In Minecraft, gravity is approximately 0.08 blocks/tick²
        val gravity = 0.08
        return (initialVelocity * initialVelocity) / (2 * gravity)
    }

    private fun checkBouncePattern(velocityHistory: List<Double>, currentVelocity: Double): Boolean {
        if (velocityHistory.size < 2) return false

        // Get recent velocities
        val recent = velocityHistory.takeLast(4)
        val lastVelocity = recent.lastOrNull() ?: return false

        // Pattern 1: Sudden upward velocity increase (classic slime bounce)
        // Regular jumps start at ~0.42, slime bounces have a distinctive acceleration pattern
        val velocityIncrease = currentVelocity - lastVelocity
        if (velocityIncrease > 0.2 && currentVelocity > 0.15) {
            // Check if this looks like a player-initiated jump vs slime bounce
            // Player jumps: immediate 0.42 velocity from stationary/slow state
            // Slime bounces: build up from negative/low velocity to high velocity

            // If the previous velocity was very close to 0 and current is ~0.42, it's likely a player jump
            if (lastVelocity >= -0.05 && lastVelocity <= 0.05 && currentVelocity >= 0.35 && currentVelocity <= 0.45) {
                // This looks like a regular jump (0 -> 0.42), not a slime bounce
                return false
            }

            return true
        }

        // Pattern 2: Going from downward/slow to fast upward (characteristic of slime bounces)
        val hadDownwardMotion = recent.any { it < -0.1 } // More strict - need actual downward motion
        if (hadDownwardMotion && currentVelocity > 0.25) {
            return true
        }

        // Pattern 3: Characteristic slime velocity with proper buildup
        // Slime bounces typically have a more gradual acceleration or come from falling
        if (currentVelocity > 0.35 && velocityIncrease > 0.15) {
            // Check if there was falling motion recently (indicates landing on slime)
            val hadFallingMotion = recent.any { it < -0.2 }
            if (hadFallingMotion) {
                return true
            }
        }

        return false
    }

    private fun wasAboveSlimeBlockRecently(player: Player, data: PlayerBounceData, currentY: Double): Boolean {
        // Check current position first
        if (isAboveSlimeBlockAt(player, player.location.x, currentY, player.location.z)) {
           // player.sendMessage("§aFound slime at current position")
            return true
        }

        // Check recent positions (last 3-4 ticks) where the bounce could have originated
        val recentPositions = data.positionHistory.takeLast(4)

        for ((index, position) in recentPositions.withIndex()) {
            val (x, y, z) = position

            // Check if this position had slime blocks below it
            if (isAboveSlimeBlockAt(player, x, y, z)) {
                //player.sendMessage("§aFound slime at recent position #$index: ${String.format("%.2f", x)}, ${String.format("%.2f", y)}, ${String.format("%.2f", z)}")
                return true
            }
        }

        return false
    }

    private fun isAboveSlimeBlockAt(player: Player, playerX: Double, playerY: Double, playerZ: Double): Boolean {

        // Player hitbox is 0.6 blocks wide
        val hitboxHalfWidth = 0.3

        // Check multiple Y levels below the player (slime bounces can happen even when slightly above)
        for (yOffset in 0 downTo -4) {
            val checkY = floor(playerY + yOffset).toInt()

            // Get the range of blocks the player's hitbox covers
            val minBlockX = floor(playerX - hitboxHalfWidth).toInt()
            val maxBlockX = floor(playerX + hitboxHalfWidth).toInt()
            val minBlockZ = floor(playerZ - hitboxHalfWidth).toInt()
            val maxBlockZ = floor(playerZ + hitboxHalfWidth).toInt()

            // Check all blocks that could be under the player's hitbox
            for (x in minBlockX..maxBlockX) {
                for (z in minBlockZ..maxBlockZ) {
                    val block = player.world.getBlockAt(x, checkY, z)

                    if (block.type == Material.SLIME_BLOCK) {
                        // Check if player's hitbox overlaps with this block
                        if (doesHitboxOverlapBlock(playerX, playerZ, hitboxHalfWidth, x, z)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun doesHitboxOverlapBlock(
        playerX: Double,
        playerZ: Double,
        hitboxHalfWidth: Double,
        blockX: Int,
        blockZ: Int
    ): Boolean {
        // Player hitbox bounds
        val playerMinX = playerX - hitboxHalfWidth
        val playerMaxX = playerX + hitboxHalfWidth
        val playerMinZ = playerZ - hitboxHalfWidth
        val playerMaxZ = playerZ + hitboxHalfWidth

        // Block bounds
        val blockMinX = blockX.toDouble()
        val blockMaxX = blockX + 1.0
        val blockMinZ = blockZ.toDouble()
        val blockMaxZ = blockZ + 1.0

        // Check if hitboxes overlap
        return playerMaxX > blockMinX && playerMinX < blockMaxX &&
                playerMaxZ > blockMinZ && playerMinZ < blockMaxZ
    }

    private fun onSlimeBounce(player: Player, block: Block) {
        val is18StyleBounce = isSlimeBelowPlayerCenter(player)

        val lagback =  plugin.getConfigField("slimeLagback").toString().toBooleanStrictOrNull() ?: true

        if (!is18StyleBounce && lagback) {
            lagPlayerBackToSlime(player)
        }
    }

    private fun lagPlayerBackToSlime(player: Player) {
        val uuid = player.uniqueId
        val data = playerData[uuid] ?: return

        // Find the most recent position where the player was over a slime block
        val recentPositions = data.positionHistory.takeLast(5).reversed() // Check most recent first

        for ((x, y, z) in recentPositions) {
            if (isAboveSlimeBlockAt(player, x, y, z)) {
                // Found a position over slime - teleport player back there
                val lagbackLocation = player.location.clone().apply {
                    this.x = x
                    this.y = y
                    this.z = z
                    // Keep the player's current pitch and yaw
                }

                // Cancel their current velocity
                player.velocity = Vector(0, 0, 0)

                // Teleport them back
                player.teleport(lagbackLocation)
                return
            }
        }

        // If we can't find a good lagback position, just cancel their velocity
        player.velocity = Vector(0.0, -0.5, 0.0) // Small downward velocity
//        player.sendMessage("§cIllegal bounce - velocity cancelled")
//        plugin.logger.warning("Could not find lagback position for ${player.name} - cancelled velocity instead")
    }

    private fun isSlimeBelowPlayerCenter(player: Player): Boolean {
        val loc = player.location
        val playerCenterX = loc.x
        val playerCenterZ = loc.z

        // Check multiple Y levels below the player center
        for (yOffset in 0 downTo -4) {
            val checkY = floor(loc.y + yOffset).toInt()

            // Get the block directly under the player's center point
            val centerBlockX = floor(playerCenterX).toInt()
            val centerBlockZ = floor(playerCenterZ).toInt()

            val block = player.world.getBlockAt(centerBlockX, checkY, centerBlockZ)

            if (block.type == Material.SLIME_BLOCK) {
                // Verify the center point is actually within this block
                val blockMinX = centerBlockX.toDouble()
                val blockMaxX = centerBlockX + 1.0
                val blockMinZ = centerBlockZ.toDouble()
                val blockMaxZ = centerBlockZ + 1.0

                // Check if player center is within the block bounds
                if (playerCenterX >= blockMinX && playerCenterX < blockMaxX &&
                    playerCenterZ >= blockMinZ && playerCenterZ < blockMaxZ) {
                    return true
                }
            }
        }

        return false
    }

    // Clean up player data when they leave
    fun onPlayerQuit(uuid: UUID) {
        playerData.remove(uuid)
    }
}