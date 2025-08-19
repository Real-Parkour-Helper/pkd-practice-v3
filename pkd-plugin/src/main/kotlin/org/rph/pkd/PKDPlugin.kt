package org.rph.pkd

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class PKDPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        println("PKDPlugin is enabled!")
    }

}