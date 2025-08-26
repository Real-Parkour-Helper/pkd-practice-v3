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
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.rph.core.boost.CooldownAPI
import org.rph.core.data.PkdData
import org.rph.core.inventory.ItemBuilder
import org.rph.core.inventory.SkullItemBuilder
import org.rph.core.inventory.hotbar.HotbarAPI
import org.rph.core.sound.PkdSounds
import org.rph.pkd.commands.*
import org.rph.pkd.skulls.letterSkullMap
import org.rph.pkd.skulls.nextTexture
import org.rph.pkd.skulls.prevTexture
import org.rph.pkd.skulls.restartTexture
import org.rph.pkd.state.StateManager
import org.rph.pkd.state.runs.RoomRunManager
import org.rph.pkd.utils.SlimeLagback
import org.rph.pkd.utils.extensions.upperCaseWords
import org.rph.pkd.worlds.RoomsWorld
import java.util.*

class PKDPlugin : JavaPlugin(), Listener {

    private val stateManagers = mutableMapOf<UUID, StateManager>()
    private lateinit var spiGUI: SpiGUI

    private var listeningForCustomRooms = false
    private var customRooms = mutableListOf<String>()

    fun getConfigField(key: String): Any? {
        return config.get(key)
    }

    fun setConfigField(key: String, value: Any) {
        config.set(key, value)
        saveConfig()
    }

    override fun onEnable() {
        println("PKDPlugin is enabled!")

        saveDefaultConfig()

        try {
            val root = org.apache.logging.log4j.LogManager.getRootLogger()
                    as org.apache.logging.log4j.core.Logger

            root.addFilter(object : org.apache.logging.log4j.core.filter.AbstractFilter() {
                override fun filter(event: org.apache.logging.log4j.core.LogEvent)
                        : org.apache.logging.log4j.core.Filter.Result {
                    val msg = event.message?.formattedMessage ?: event.message?.toString() ?: ""
                    return if (msg.contains("@CuboidClipboard used by Schematics.paste") || msg.contains("Alternatives: { class com.sk89q.worldedit.extent.clipboard."))
                        org.apache.logging.log4j.core.Filter.Result.DENY
                    else
                        org.apache.logging.log4j.core.Filter.Result.NEUTRAL
                }
            })
        } catch (t: Throwable) {
            logger.warning("Failed to install Log4j2 root filter: ${t.javaClass.simpleName}: ${t.message}")
        }

        Bukkit.getPluginManager().registerEvents(this, this)
        HotbarAPI.register(this)
        CooldownAPI.register(this)

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

        getCommand("rooms").executor = RoomsCommand(this)
        getCommand("prev").executor = PrevCommand(this)
        getCommand("next").executor = NextCommand(this)
        getCommand("lobby").executor = LobbyCommand(this)
        getCommand("run").executor = RunCommand(this)
        getCommand("custom").executor = CustomCommand(this)
        getCommand("config").executor = ConfigCommand(this)

        Bukkit.getPluginManager().registerEvents(SlimeLagback(this), this)
    }

    override fun onDisable() {
        stateManagers.forEach { (_, manager) ->
            manager.ensureOldStateCleanup()
        }
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
        stateManagers[player.uniqueId]?.ensureOldStateCleanup()
        stateManagers.remove(player.uniqueId)
        event.quitMessage = ""
    }

    @EventHandler
    fun onFallDamage(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL) {
            event.isCancelled = true
        } else if (event.cause == EntityDamageEvent.DamageCause.VOID && event.entity is Player) {
            event.isCancelled = true
            val runManager = stateManagers[(event.entity as Player).uniqueId]?.getRunManager() ?: return
            runManager.resetToCheckpoint()
        }
    }

    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity is Player) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        if (listeningForCustomRooms) {
            event.isCancelled = true
            val message = event.message.trim()
            // Split the message by spaces, allow multiple rooms at once
            val rooms = message.split(" ").map { it.lowercase() }
            for (room in rooms) {
                val res = PkdData.find(room)
                if (res.size > 1) {
                    val str = res.joinToString("${ChatColor.RED}, ") { "${ChatColor.GRAY}$it" }
                    event.player.sendMessage("${ChatColor.RED}Multiple rooms matching '${ChatColor.GRAY}$room${ChatColor.RED}' found: $str")
                } else if (res.size == 1) {
                    customRooms.add(res[0])
                    event.player.sendMessage("${ChatColor.GREEN}Added room '${ChatColor.GRAY}${res[0]}${ChatColor.GREEN}'.")
                } else {
                    event.player.sendMessage("${ChatColor.RED}Room '${ChatColor.GRAY}$room${ChatColor.RED}' not found.")
                }
            }
        }
    }

    @EventHandler
    fun onPlayerPreCommand(event: PlayerCommandPreprocessEvent) {
        if (listeningForCustomRooms) {
            event.isCancelled = true
            val command = event.message.trim()

            if (command.equals("/done", true)) {
                listeningForCustomRooms = false
                if (customRooms.isEmpty()) {
                    event.player.sendMessage("${ChatColor.RED}You didn't select any rooms!")
                } else {
                    val hasPregame = customRooms.any { "pregame" in it }
                    val hasStartRoom = customRooms.any { "start" in it }
                    val hasEndRoom = customRooms.any { "end" in it }

                    val allRooms = PkdData.rooms()
                    val pregameRooms = allRooms.filter { "pregame" in it }
                    val startRooms = allRooms.filter { "start" in it }
                    val endRooms = allRooms.filter { "end" in it }

                    val mapDist = customRooms
                        .map { it.substringBefore("_") }
                        .groupingBy { it }
                        .eachCount()

                    val map = mapDist.maxByOrNull { it.value }?.key ?: "castle"

                    if (!hasStartRoom) {
                        val fittingStartRoom = startRooms.firstOrNull { map in it } ?: startRooms.random()
                        customRooms.add(0, fittingStartRoom)
                    }

                    if (!hasPregame) {
                        val fittingPregameRoom = pregameRooms.firstOrNull { map in it } ?: pregameRooms.random()
                        customRooms.add(0, fittingPregameRoom)
                    }

                    if (!hasEndRoom) {
                        val fittingEndRoom = endRooms.firstOrNull { map in it } ?: endRooms.random()
                        customRooms.add(fittingEndRoom)
                    }

                    val copyList = mutableListOf<String>()
                    copyList.addAll(customRooms)
                    getStateManager(event.player)?.tpToRun(copyList)

                    customRooms.clear()
                }
            } else if (command.equals("/cancel", true)) {
                listeningForCustomRooms = false
                event.player.sendMessage("${ChatColor.RED}Cancelled room selection.")
                customRooms.clear()
            } else {
                event.player.sendMessage("${ChatColor.RED}You are currently selecting rooms. Use /done to start or /cancel to finish.")
            }
        }
    }

    fun startListeningForCustomRooms(player: Player) {
        player.sendMessage("${ChatColor.GREEN}Listening for custom rooms...")
        listeningForCustomRooms = true
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
                        val menu = spiGUI.create("8-Room Practice Run", 1)

                        val allItem = ItemBuilder(Material.STAINED_GLASS)
                            .name("${ChatColor.GREEN}All Maps")
                            .lore("${ChatColor.GRAY}Click to start an 8 room run with rooms from all maps.")
                            .durability(4.toShort())
                            .build()

                        val allButton = SGButton(allItem).withListener { event ->
                            getStateManager(player)?.startRun()
                        }

                        menu.addButton(allButton)

                        PkdData.maps().forEachIndexed { idx, map ->
                            if (map == "All") return@forEachIndexed

                            val firstLetter = map[0].lowercase()
                            val letterSkullTexture = letterSkullMap[firstLetter]
                            val item = if (letterSkullTexture != null) {
                                SkullItemBuilder()
                                    .name("${ChatColor.GREEN}${map.upperCaseWords()}")
                                    .lore("${ChatColor.GRAY}Click to start an 8 room run with rooms from $map.")
                                    .textureBase64(letterSkullTexture)
                                    .build()
                            } else {
                                ItemBuilder(Material.STAINED_CLAY)
                                    .name("${ChatColor.GREEN}${map.upperCaseWords()}")
                                    .lore("${ChatColor.GRAY}Click to start an 8 room run with rooms from $map.")
                                    .durability(idx.toShort())
                                    .build()
                            }

                            val button = SGButton(item).withListener { event ->
                                getStateManager(player)?.startRun(map)
                            }

                            menu.addButton(button)
                        }

                        player.openInventory(menu.inventory)
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
                        getStateManager(player)?.getRunManager()?.currentBoostManager()?.tryBoost(
                            onSuccess = {
                                setState(1)
                            },
                            onCooldownEnd = {
                                player.sendMessage("${ChatColor.GREEN}Your Parkour Booster is now ready!")
                                PkdSounds.playBoostReadySound(player)
                                setState(0)
                            },
                            onFail = { secondsLeft ->
                                player.sendMessage("${ChatColor.RED}You can use this again in $secondsLeft second${if (secondsLeft != 1) "s" else ""}!")
                            }
                        )
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
                        getStateManager(player)?.getRunManager()?.currentBoostManager()?.tryBoost(
                            onSuccess = {
                                setState(1)
                            },
                            onCooldownEnd = {
                                player.sendMessage("${ChatColor.GREEN}Your Parkour Booster is now ready!")
                                PkdSounds.playBoostReadySound(player)
                                setState(0)
                            },
                            onFail = { secondsLeft ->
                                player.sendMessage("${ChatColor.RED}You can use this again in $secondsLeft second${if (secondsLeft != 1) "s" else ""}!")
                            }
                        )
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
            slot(6) {
                state(0) {
                    item = ItemBuilder(Material.WOOL)
                        .name("${ChatColor.RED}Toggle Barriers")
                        .lore("${ChatColor.GRAY}Hide / show the barriers around you.")
                        .durability(14)
                        .build()

                    onClick = { player ->
                        try {
                            (getStateManager(player)?.getRunManager() as RoomRunManager?)?.toggleBarriers()
                        } finally {}
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
                        .name("${ChatColor.RED}Lobby")
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
                        getStateManager(player)?.tpToPrevRoom()
                    }
                }
            }
            slot(4) {
                state(0) {
                    item = SkullItemBuilder()
                        .name("${ChatColor.GREEN}Redo Run")
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
                        getStateManager(player)?.tpToNextRoom()
                    }
                }
            }
            slot(8) {
                state(0) {
                    item = ItemBuilder(Material.BED)
                        .name("${ChatColor.RED}Lobby")
                        .lore("${ChatColor.GRAY}Return to the lobby.")
                        .build()

                    onClick = { player ->
                        stateManagers[player.uniqueId]?.tpToLobby()
                    }
                }
            }
        }
        HotbarAPI.registerLayout("preRunLayout") {
            slot(0) {
                state(0) {
                    item = ItemBuilder(Material.BED)
                        .name("${ChatColor.RED}Lobby")
                        .lore("${ChatColor.GRAY}Return to the lobby.")
                        .build()

                    onClick = { player ->
                        stateManagers[player.uniqueId]?.tpToLobby()
                    }
                }
            }
        }
        HotbarAPI.registerLayout("fullRunLayout") {
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
                        getStateManager(player)?.getRunManager()?.currentBoostManager()?.tryBoost(
                            onSuccess = {
                                setState(1)
                            },
                            onCooldownEnd = {
                                player.sendMessage("${ChatColor.GREEN}Your Parkour Booster is now ready!")
                                PkdSounds.playBoostReadySound(player)
                                setState(0)
                            },
                            onFail = { secondsLeft ->
                                player.sendMessage("${ChatColor.RED}You can use this again in $secondsLeft second${if (secondsLeft != 1) "s" else ""}!")
                            }
                        )
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
                        getStateManager(player)?.getRunManager()?.currentBoostManager()?.tryBoost(
                            onSuccess = {
                                setState(1)
                            },
                            onCooldownEnd = {
                                player.sendMessage("${ChatColor.GREEN}Your Parkour Booster is now ready!")
                                PkdSounds.playBoostReadySound(player)
                                setState(0)
                            },
                            onFail = { secondsLeft ->
                                player.sendMessage("${ChatColor.RED}You can use this again in $secondsLeft second${if (secondsLeft != 1) "s" else ""}!")
                            }
                        )
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
                        .name("${ChatColor.RED}Reset Run")
                        .lore("${ChatColor.GRAY}Reset to the start of the run.")
                        .build()

                    onClick = { player ->
                        getStateManager(player)?.getRunManager()?.resetRun()
                    }
                }
            }
        }
        HotbarAPI.registerLayout("fullRunOverLayout") {
            slot(4) {
                state(0) {
                    item = SkullItemBuilder()
                        .name("${ChatColor.GREEN}Redo Run")
                        .lore("${ChatColor.GRAY}Re-run this run.")
                        .textureBase64(restartTexture)
                        .build()

                    onClick = { player ->
                        getStateManager(player)?.getRunManager()?.resetRun()
                    }
                }
            }
            slot(8) {
                state(0) {
                    item = ItemBuilder(Material.BED)
                        .name("${ChatColor.RED}Lobby")
                        .lore("${ChatColor.GRAY}Return to the lobby.")
                        .build()

                    onClick = { player ->
                        stateManagers[player.uniqueId]?.tpToLobby()
                    }
                }
            }
        }
    }

    fun getStateManager(player: Player): StateManager? {
        return stateManagers[player.uniqueId]
    }
}