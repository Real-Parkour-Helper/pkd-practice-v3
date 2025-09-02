package org.rph.pkd.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.rph.pkd.PKDPlugin

class ConfigCommand(private val plugin: PKDPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be run by a player.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("§cUsage:")
            sender.sendMessage("§c  /config <key> [value]§7: Show or set the value for <key>")
            sender.sendMessage("§c  /config list§7: List config options")
            return true
        }

        val key = args[0]

        if (key == "list") {
            val keys = plugin.config.getKeys(false)

            sender.sendMessage("§cConfig fields:")
            for (k in keys) {
                val v = plugin.getConfigField(k) ?: continue
                val type = when (v::class.simpleName) {
                    "Int" -> {
                        "Number (positive)"
                    }
                    "Boolean" -> {
                        "true / false"
                    }
                    else -> null
                } ?: continue
                sender.sendMessage("§c  $k§7: $type (currently §l$v§r§7)")
            }
            return true
        }

        val configValue = plugin.getConfigField(key)
        if (configValue == null) {
            sender.sendMessage("§cKey '$key' not found in config.")
            return true
        }

        if (args.size < 2) {
            sender.sendMessage("§a${key} = $configValue")
            return true
        }

        val value = args.drop(1).joinToString(" ")
        when (configValue) {
            is String -> plugin.setConfigField(key, value)
            is Int -> {
                val intValue = value.toIntOrNull()
                if (intValue != null && intValue >= 0) {
                    plugin.setConfigField(key, intValue)
                    sender.sendMessage("§a${key} set to $intValue")
                } else {
                    sender.sendMessage("§cInvalid integer value.")
                }
            }
            is Boolean -> {
                val boolValue = value.toBooleanStrictOrNull()
                if (boolValue != null) {
                    plugin.setConfigField(key, boolValue)
                    sender.sendMessage("§a${key} set to $boolValue")
                } else {
                    sender.sendMessage("§cInvalid boolean value.")
                }
            }
            else -> sender.sendMessage("§cUnsupported config type.")
        }
        return true
    }
}