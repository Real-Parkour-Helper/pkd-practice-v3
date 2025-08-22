package org.rph.pkd.worlds

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import org.rph.core.data.PkdData
import org.rph.pkd.utils.Schematics
import org.rph.pkd.utils.VoidWorldGenerator
import java.util.UUID

object RunWorld {

    private val floorY = 64

    data class GeneratedWorld(
        val roomPositions: MutableMap<String, Triple<Int, Int, Int>>,
        val checkpoints: MutableList<Location>,
        val world: World
    )

    fun createRunWorld(plugin: JavaPlugin, rooms: List<String>, cb: (GeneratedWorld) -> Unit) {
        Bukkit.getScheduler().runTask(plugin) {
            val roomPositions = mutableMapOf<String, Triple<Int, Int, Int>>()
            val checkpoints = mutableListOf<Location>()
            val worldName = "tmp_run_${UUID.randomUUID()}"

            val world = WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .generator(VoidWorldGenerator())
                .createWorld() ?: error("Failed to generate a world for the run!")

            world.setGameRuleValue("doDaylightCycle", "false")
            world.setGameRuleValue("doWeatherCycle", "false")
            world.setGameRuleValue("doMobSpawning", "false")
            world.setGameRuleValue("randomTickSpeed", "0")
            world.setStorm(false)
            world.time = 6000L

            // paste first room assuming pre-game lobby
            val lobby = rooms[0]
            val lobbyAsset = PkdData.get(lobby) ?: error("Asset for room $lobby not found.")

            val lobbyPasteLocation = Location(world, 0.0, floorY.toDouble(), -lobbyAsset.meta!!.length.toDouble())
            Schematics.pasteSchematic(lobbyAsset.schem.toFile(), world, lobbyPasteLocation)

            val spawn = lobbyAsset.meta!!.checkpoints[0]
            checkpoints.add(lobbyPasteLocation.clone().add(spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5))

            val remainingRooms = mutableListOf<String>()
            rooms.forEachIndexed { index, s -> if (index > 0) remainingRooms.add(s) }

            val pasteJobs = mutableListOf<Schematics.PasteJob>()

            var lastBackDoorVec: Pair<Int, Int>? = null // x,y
            var lastRoomCorner: Vector? = null
            var lastRoomDepth: Int? = null
            for (room in remainingRooms) {
                val asset = PkdData.get(room) ?: error("Asset for room $room not found.")
                val frontDoorVec = if (asset.meta!!.frontDoor != null) {
                    Pair(asset.meta!!.frontDoor.x, asset.meta!!.frontDoor.y)
                } else null

                val transposeVec = if (lastBackDoorVec == null || frontDoorVec == null) {
                    Pair(0, 0)
                } else {
                    Pair(lastBackDoorVec.first - frontDoorVec.first, lastBackDoorVec.second - frontDoorVec.second)
                }

                val lastCorner = lastRoomCorner ?: Vector(0, floorY, 0)
                var roomCorner = lastCorner.add(Vector(transposeVec.first, transposeVec.second, lastRoomDepth ?: 0))

                pasteJobs += Schematics.PasteJob(
                    asset.schem.toFile(),
                    Location(world, roomCorner.x, roomCorner.y, roomCorner.z)
                )

                for (cp in asset.meta!!.checkpoints) {
                    checkpoints.add(Location(world, roomCorner.x + cp.x, roomCorner.y + cp.y, roomCorner.z + cp.z))
                }

                roomPositions[room] = Triple(roomCorner.z.toInt(), roomCorner.y.toInt(), asset.meta!!.deathPlane ?: 2)

                lastBackDoorVec = if (asset.meta!!.backDoor != null) {
                    Pair(asset.meta!!.backDoor.x, asset.meta!!.backDoor.y)
                } else null // when this is the last room we don't care anymore

                // paste doors
                if (asset.meta!!.frontDoor != null) {
                    val pasteLocation = Location(world, roomCorner.x + asset.meta!!.frontDoor.x + 1, roomCorner.y + asset.meta!!.frontDoor.y + 1, roomCorner.z + asset.meta!!.frontDoor.z)
                    val door = PkdData.doors(asset.map).firstOrNull { it.front }
                    if (door != null) {
                        pasteJobs += Schematics.PasteJob(
                            door.schem.toFile(),
                            pasteLocation
                        )
                    }
                }

                if (asset.meta!!.backDoor != null) {
                    val pasteLocation = Location(world, roomCorner.x + asset.meta!!.backDoor.x + 1, roomCorner.y + asset.meta!!.backDoor.y + 1, roomCorner.z + asset.meta!!.backDoor.z)
                    val door = PkdData.doors(asset.map).firstOrNull { !it.front }
                    if (door != null) {
                        pasteJobs += Schematics.PasteJob(
                            door.schem.toFile(),
                            pasteLocation
                        )
                    }

                }

                lastRoomCorner = roomCorner
                lastRoomDepth = asset.meta!!.length
            }

            Schematics.pasteBatch(world, pasteJobs)

            cb(GeneratedWorld(roomPositions, checkpoints, world))
        }
    }

}