package dev.luna5ama.trollhack.module.modules.chat

import dev.luna5ama.trollhack.TrollHackMod
import dev.luna5ama.trollhack.event.SafeClientEvent
import dev.luna5ama.trollhack.event.events.PacketEvent
import dev.luna5ama.trollhack.event.safeListener
import dev.luna5ama.trollhack.module.Category
import dev.luna5ama.trollhack.module.Module
import dev.luna5ama.trollhack.util.TickTimer
import dev.luna5ama.trollhack.util.TimeUnit
import dev.luna5ama.trollhack.util.text.MessageSendUtils.sendServerMessage
import dev.luna5ama.trollhack.util.text.NoSpamMessage
import net.minecraft.init.Items
import net.minecraft.network.play.server.SPacketUpdateHealth
import net.minecraft.util.EnumHand
import java.io.File

internal object AutoExcuse : Module(
    name = "Auto Excuse",
    description = "Makes an excuse for you when you die",
    category = Category.CHAT,
    modulePriority = 500
) {
    private val mode by setting("Mode", Mode.INTERNAL)

    private enum class Mode {
        INTERNAL, EXTERNAL
    }

    private const val CLIENT_NAME = "%CLIENT%"
    private val defaultExcuses = arrayOf(
        "Sorry, im using $CLIENT_NAME client",
        "My ping is so bad",
        "I was changing my config :(",
        "Why did my AutoTotem break",
        "I was desynced",
        "Stupid hackers killed me",
        "Wow, so many try hards",
        "Lagggg",
        "I wasn't trying",
        "I'm not using $CLIENT_NAME client",
        "Thers to much lag",
        "My dog ate my pc",
        "Sorry, $CLIENT_NAME Client is really bad",
        "I was lagging",
        "He was cheating!",
        "Your hacking!",
        "Lol imagine actully trying",
        "I didn't move my mouse",
        "I was playing on easy mode(;",
        "My wifi went down",
        "I'm playing vanila",
        "My optifine didn't work",
        "The CPU cheated!"
    )

    private val file = File("${TrollHackMod.DIRECTORY}/excuses.txt")
    private var loadedExcuses = defaultExcuses

    private val clients = arrayOf(
        "Future",
        "Salhack",
        "Pyro",
        "Impact"
    )

    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<PacketEvent.Receive> {
            if (loadedExcuses.isEmpty() || it.packet !is SPacketUpdateHealth) return@safeListener
            if (it.packet.health <= 0.0f && !isHoldingTotem && timer.tickAndReset(3L)) {
                AutoExcuse.sendServerMessage(getExcuse())
            }
        }

        onEnable {
            loadedExcuses = if (mode == Mode.EXTERNAL) {
                if (file.exists()) {
                    val cacheList = ArrayList<String>()
                    try {
                        file.forEachLine { if (it.isNotBlank()) cacheList.add(it.trim()) }
                        NoSpamMessage.sendMessage("$chatName Loaded spammer messages!")
                    } catch (e: Exception) {
                        TrollHackMod.logger.error("Failed loading excuses", e)
                    }
                    cacheList.toTypedArray()
                } else {
                    file.createNewFile()
                    NoSpamMessage.sendError(
                        "$chatName Excuses file is empty!" +
                            ", please add them in the §7excuses.txt§f under the §7.minecraft/trollhack§f directory."
                    )
                    defaultExcuses
                }
            } else {
                defaultExcuses
            }
        }
    }

    private val SafeClientEvent.isHoldingTotem: Boolean
        get() = EnumHand.values().any { player.getHeldItem(it).item == Items.TOTEM_OF_UNDYING }

    private fun getExcuse() = loadedExcuses.random().replace(CLIENT_NAME, clients.random())
}