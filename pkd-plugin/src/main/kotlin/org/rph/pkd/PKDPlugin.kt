package org.rph.pkd

import com.samjakob.spigui.SpiGUI
import com.samjakob.spigui.buttons.SGButton
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.rph.core.data.PkdData
import org.rph.core.inventory.ItemBuilder
import org.rph.core.inventory.SkullItemBuilder
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.pkd.skulls.letterSkullMap
import org.rph.pkd.skulls.nextTexture
import org.rph.pkd.skulls.prevTexture
import org.rph.pkd.skulls.restartTexture
import org.rph.pkd.state.StateManager
import org.rph.pkd.worlds.RoomsWorld
import java.util.*

class PKDPlugin : JavaPlugin(), Listener {

    private val stateManagers = mutableMapOf<UUID, StateManager>()
    private lateinit var spiGUI: SpiGUI

    override fun onEnable() {
        println("PKDPlugin is enabled!")

        Bukkit.getPluginManager().registerEvents(this, this)
        HotbarAPI.register(this)

        registerLayouts()

        // PKD Data
        val dataConfig = PkdData.PkdConfig(
            folderOverride = dataFolder.toPath().resolve("pkd-data"),
            cacheDir = dataFolder.toPath().resolve("_cache"),
            githubOwner = "Real-Parkour-Helper",
            githubRepo = "pkd-data",
            useLatest = true,
            tag = null
        )
        PkdData.init(dataConfig)
        println("PKD data source: ${PkdData.where()}")

        RoomsWorld.init()

        spiGUI = SpiGUI(this)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player

        stateManagers[player.uniqueId] = StateManager(this, player)
        stateManagers[player.uniqueId]!!.tpToLobby()
        event.joinMessage = ""
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        stateManagers.remove(player.uniqueId)
        event.quitMessage = ""
    }

    @EventHandler
    fun onFallDamage(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity is Player) {
            event.isCancelled = true
        }
    }

    private fun registerLayouts() {
        HotbarAPI.registerLayout("lobbyLayout") {
            slot(3) {
                state(0) {
                    item = ItemBuilder(Material.STAINED_CLAY)
                        .name("${ChatColor.GREEN}Rooms Practice")
                        .lore("${ChatColor.GRAY}Practice individual rooms.")
                        .durability(3)
                        .build()

                    onClick = { player ->
                        val menu = spiGUI.create("Select a Map", 1)

                        PkdData.maps().forEachIndexed { idx, map ->
                            if (map == "All") return@forEachIndexed

                            val subMenu = spiGUI.create("Select a Room", 5)

                            PkdData.rooms(map).forEach { room ->
                                if ("start" in room.lowercase() || "end" in room.lowercase() || "pregame" in room.lowercase()) return@forEach

                                val r = room.replaceFirst("${map}_", "").replace("_", " ")
                                val roomItem = ItemBuilder(Material.STAINED_GLASS)
                                    .name("${ChatColor.GREEN}${r.upperCaseWords()}")
                                    .lore("${ChatColor.GRAY}Click to go to ${r.upperCaseWords()}.")
                                    .durability(idx.toShort())
                                    .build()

                                val roomButton = SGButton(roomItem).withListener {
                                    stateManagers[player.uniqueId]?.tpToRoom(room)
                                }

                                subMenu.addButton(roomButton)
                            }

                            val firstLetter = map[0].lowercase()
                            val letterSkullTexture = letterSkullMap[firstLetter]
                            val item = if (letterSkullTexture != null) {
                                SkullItemBuilder()
                                    .name("${ChatColor.GREEN}${map.upperCaseWords()}")
                                    .lore("${ChatColor.GRAY}Click to select a room from this map.")
                                    .textureBase64(letterSkullTexture)
                                    .build()
                            } else {
                                ItemBuilder(Material.STAINED_CLAY)
                                    .name("${ChatColor.GREEN}${map.upperCaseWords()}")
                                    .lore("${ChatColor.GRAY}Click to select a room from this map.")
                                    .durability(idx.toShort())
                                    .build()
                            }

                            val button = SGButton(item).withListener { event ->
                                player.openInventory(subMenu.inventory)
                            }

                            menu.addButton(button)
                        }

                        player.openInventory(menu.inventory)
                    }
                }
            }
            slot(5) {
                state(0) {
                    item = ItemBuilder(Material.STAINED_CLAY)
                        .name("${ChatColor.GREEN}Run Practice")
                        .lore("${ChatColor.GRAY}Practice complete runs.")
                        .durability(10)
                        .build()

                    onClick = { player ->
                        // ...
                    }
                }
            }
        }
        HotbarAPI.registerLayout("roomsLayout") {
            slot(0) {
                state(0) {
                    item = ItemBuilder(Material.FEATHER)
                        .name("${ChatColor.GREEN}Boost Forward!")
                        .lore(
                            "${ChatColor.GRAY}Propel yourself forwards in the air like a bird!",
                            "${ChatColor.GRAY}(1 Minute Cooldown)"
                        )
                        .build()

                    onClick = { player ->
                        // ...
                    }
                }
                state(1) {
                    item = ItemBuilder(Material.GHAST_TEAR)
                        .name("${ChatColor.RED}Boost Forward!")
                        .lore(
                            "${ChatColor.GRAY}Propel yourself forwards in the air like a bird!",
                            "${ChatColor.GRAY}(1 Minute Cooldown)"
                        )
                        .build()

                    onClick = { player ->
                        // ...
                    }
                }
            }
            slot(4) {
                state(0) {
                    item = ItemBuilder(Material.GOLD_PLATE)
                        .name("${ChatColor.GREEN}Last Checkpoint")
                        .lore("${ChatColor.GRAY}Teleport to your last checkpoint.")
                        .build()

                    onClick = { player ->
                        getStateManager(player)?.getRunManager()?.resetToCheckpoint()
                    }
                }
            }
            slot(7) {
                state(0) {
                    item = ItemBuilder(Material.REDSTONE)
                        .name("${ChatColor.RED}Reset Room")
                        .lore("${ChatColor.GRAY}Reset to the start of the room.")
                        .build()

                    onClick = { player ->
                        getStateManager(player)?.getRunManager()?.resetRun()
                    }
                }
            }
            slot(8) {
                state(0) {
                    item = ItemBuilder(Material.BED)
                        .name("${ChatColor.RED}Leave Room")
                        .lore("${ChatColor.GRAY}Return to the lobby.")
                        .build()

                    onClick = { player ->
                        stateManagers[player.uniqueId]?.tpToLobby()
                    }
                }
            }
        }
        HotbarAPI.registerLayout("roomRunOverLayout") {
            slot(3) {
                state(0) {
                    item = SkullItemBuilder()
                        .name("${ChatColor.GREEN}Previous Room")
                        .lore("${ChatColor.GRAY}Switch to the previous room.")
                        .textureBase64(prevTexture)
                        .build()

                    onClick = { player ->
                        // ...
                    }
                }
            }
            slot(4) {
                state(0) {
                    item = SkullItemBuilder()
                        .name("${ChatColor.GREEN}Re-do Run")
                        .lore("${ChatColor.GRAY}Re-run this run.")
                        .textureBase64(restartTexture)
                        .build()

                    onClick = { player ->
                        getStateManager(player)?.getRunManager()?.resetRun()
                    }
                }
            }
            slot(5) {
                state(0) {
                    item = SkullItemBuilder()
                        .name("${ChatColor.GREEN}Next Room")
                        .lore("${ChatColor.GRAY}Switch to the next room.")
                        .textureBase64(nextTexture)
                        .build()

                    onClick = { player ->
                        // ...
                    }
                }
            }
        }
    }

    fun getStateManager(player: Player): StateManager? {
        return stateManagers[player.uniqueId]
    }

    fun String.upperCaseWords() =
        this.split(" ")
            .joinToString(" ") { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } }
}