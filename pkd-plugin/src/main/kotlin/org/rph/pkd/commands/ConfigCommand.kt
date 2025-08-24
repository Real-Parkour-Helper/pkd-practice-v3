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
            sender.sendMessage("§cUsage: /config <key> [value]")
            return true
        }

        val key = args[0]
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
                } else {
                    sender.sendMessage("§cInvalid integer value.")
                }
            }
            is Boolean -> {
                val boolValue = value.toBooleanStrictOrNull()
                if (boolValue != null) {
                    plugin.setConfigField(key, boolValue)
                } else {
                    sender.sendMessage("§cInvalid boolean value.")
                }
            }
            else -> sender.sendMessage("§cUnsupported config type.")
        }
        return true
    }
}