package org.rph.pkd

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.rph.core.data.PkdData
import org.rph.core.inventory.ItemBuilder
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.pkd.state.StateManager
import org.rph.pkd.worlds.RoomsWorld

class PKDPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        println("PKDPlugin is enabled!")

        StateManager.plugin = this

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
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        StateManager.tpToLobby(player)
        HotbarAPI.applyLayout(player, "lobbyLayout")
        event.joinMessage = ""
    }

    private fun registerLayouts() {
        HotbarAPI.registerLayout("lobbyLayout") {
            slot(4) {
                state(0) {
                    item = ItemBuilder(Material.FEATHER)
                        .name("${ChatColor.GREEN}Select Mode")
                        .lore("${ChatColor.GRAY}Click to select a practice mode.")
                        .build()

                    onClick = { player ->
                        // ...
                        StateManager.tpToRoom(player, "castle_around_pillars")
                    }
                }
            }
        }
    }

}