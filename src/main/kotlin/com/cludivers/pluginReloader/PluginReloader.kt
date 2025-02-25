package com.cludivers.pluginReloader

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class PluginReloader : JavaPlugin() {

    private val fileTimestamps = mutableMapOf<File, Long>()
    private var autoReloadEnabled = true

    override fun onEnable() {
        logger.info("Plugin Reloader enabled")
        initializeFileWatcher()
    }

    private fun initializeFileWatcher() {
        val pluginsFolder = dataFolder.parentFile
        if (!pluginsFolder.exists()) {
            logger.severe("Plugins folder not found!")
            return
        }

        pluginsFolder.listFiles()?.filter { it.isJarFile() }?.forEach { file ->
            fileTimestamps[file] = file.lastModified()
        }

        server.scheduler.runTaskTimer(this, Runnable {
            checkForChanges(pluginsFolder)
        }, 0L, 20L)

    }

    private fun checkForChanges(pluginsFolder: File) {
        pluginsFolder.listFiles()?.filter { it.isJarFile() }?.forEach { file ->
            val lastModified = file.lastModified()

            if (fileTimestamps.containsKey(file)) {
                if (fileTimestamps[file] != lastModified) {
                    logger.info("Detected change in JAR file: ${file.name}")
                    reloadServer()
                    fileTimestamps[file] = lastModified // Update the timestamp
                    val broadcastTail =
                        Component.text(" modified").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false)
                    Bukkit.broadcast(
                        Component.text(file.name).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                            .append(broadcastTail)
                    )
                }
            } else {
                logger.info("Detected new JAR file: ${file.name}")
                reloadServer()
                fileTimestamps[file] = lastModified // Add the new file
                val broadcastTail =
                    Component.text(" added").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false)
                Bukkit.broadcast(
                    Component.text(file.name).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)
                        .append(broadcastTail)
                )
            }

        }

        // Check for deleted JAR files
        fileTimestamps.keys.removeIf { !it.exists() }
    }

    private fun reloadServer() {
        logger.info("Reloading server...")
        Bukkit.broadcast(Component.text("Auto Reloading Server").color(NamedTextColor.GREEN))
        server.reload() // This will trigger the "reload confirm" requirement
    }

    private fun File.isJarFile(): Boolean {
        return isFile && name.endsWith(".jar", ignoreCase = true)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name.equals("autoreload", ignoreCase = true)) {
            if (!sender.hasPermission("pluginwatcher.toggle")) {
                sender.sendMessage(
                    Component.text(
                        "You do not have permission to use this command!",
                        NamedTextColor.RED
                    )
                )
                return true
            }

            // Toggle auto-reload
            autoReloadEnabled = !autoReloadEnabled

            // Send status message
            val status = if (autoReloadEnabled) "ON" else "OFF"
            val statusColor = if (autoReloadEnabled) NamedTextColor.GREEN else NamedTextColor.RED
            val statusMessage = Component.text()
                .content("Auto-reload is now: ")
                .append(Component.text(status, statusColor, TextDecoration.BOLD))
                .build()
            sender.sendMessage(statusMessage)

            // Reinitialize the file watcher if auto-reload is enabled
            if (autoReloadEnabled) {
                initializeFileWatcher()
            }

            return true
        }
        return false
    }

    private fun broadcastChangeState() {
        Bukkit.broadcast(createClickableCommandReference("/autoreload", ": Enable/Disable autoreload"))
    }

    private fun createClickableCommandReference(command: String, description: String): Component {
        return Component.text(command).decorate(TextDecoration.BOLD).color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand(command)).append(
                Component.text(description).color(NamedTextColor.WHITE).decorations(
                    mapOf(TextDecoration.BOLD to TextDecoration.State.FALSE)
                )
            )
    }

    // Register the command
    override fun onLoad() {
        getCommand("autoreload")?.setExecutor(this)
        broadcastChangeState()
    }

    override fun onDisable() {
        logger.info("Plugin Reloader disabled")
    }
}
