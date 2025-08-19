package org.rph.pkd

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.rph.core.inventory.ItemBuilder
import org.rph.core.inventory.hotbar.HotbarAPI

class PKDPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        println("PKDPlugin is enabled!")

        Bukkit.getPluginManager().registerEvents(this, this)
        HotbarAPI.register(this)

        registerLayouts()
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        HotbarAPI.applyLayout(player, "lobbyLayout")
    }

    private fun registerLayouts() {
        HotbarAPI.registerLayout("lobbyLayout") {
            slot(4) {
                state(0) {
                    item = ItemBuilder(Material.COMPASS)
                        .name("${ChatColor.GREEN}Select Mode")
                        .lore("${ChatColor.GRAY}Click to select a practice mode.")
                        .build()

                    onClick = { player ->
                        // ...
                    }
                }
            }
        }
    }

}