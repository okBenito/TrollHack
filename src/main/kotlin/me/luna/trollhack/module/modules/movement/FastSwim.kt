package me.luna.trollhack.module.modules.movement

import me.luna.trollhack.event.SafeClientEvent
import me.luna.trollhack.event.events.PacketEvent
import me.luna.trollhack.event.events.player.PlayerMoveEvent
import me.luna.trollhack.event.listener
import me.luna.trollhack.event.safeListener
import me.luna.trollhack.manager.managers.TimerManager.modifyTimer
import me.luna.trollhack.module.Category
import me.luna.trollhack.module.Module
import me.luna.trollhack.util.EntityUtils.isFlying
import me.luna.trollhack.util.MovementUtils
import me.luna.trollhack.util.MovementUtils.calcMoveYaw
import me.luna.trollhack.util.MovementUtils.speedEffectMultiplier
import me.luna.trollhack.util.atTrue
import me.luna.trollhack.util.extension.fastFloor
import net.minecraft.block.BlockLiquid
import net.minecraft.block.material.Material
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.BlockPos
import kotlin.math.*

internal object FastSwim : Module(
    name = "FastSwim",
    category = Category.MOVEMENT,
    description = "Swim faster"
) {
    private val water0 = setting("Water", true)
    private val water by water0
    private val waterHBoost by setting("Water H Boost", 6.0f, 1.0f..8.0f, 0.1f, water0.atTrue())
    private val waterHSpeed by setting("Water H Speed", 5.75f, 0.01f..8.0f, 0.01f, water0.atTrue())
    private val waterVBoost by setting("Water V Boost", 2.9f, 0.1f..8.0f, 0.1f, water0.atTrue())
    private val waterUpSpeed by setting("Water Up Speed", 2.69f, 0.01f..8.0f, 0.01f, water0.atTrue())
    private val waterDownSpeed by setting("Water Down Speed", 0.8f, 0.01f..2.0f, 0.01f, water0.atTrue())
    private val lava0 = setting("Lava", true)
    private val lava by lava0
    private val lavaHBoost by setting("Lava H Boost", 4.0f, 1.0f..8.0f, 0.1f)
    private val lavaHSpeed by setting("Lava H Speed", 3.8f, 0.01f..8.0f, 0.01f)
    private val lavaVBoost by setting("Lava V Boost", 2.0f, 0.1f..8.0f, 0.1f)
    private val lavaUpSpeed by setting("Lava Up Speed", 2.69f, 0.01f..8.0f, 0.01f)
    private val lavaDownSpeed by setting("Lava Down Speed", 4.22f, 0.01f..8.0f, 0.01f)
    private val jitter by setting("Jitter", 8, 1..20, 1)
    private val timerBoost by setting("Timer Boost", 1.09f, 1.0f..1.5f, 0.01f)

    private var moveSpeed = 0.0
    private var motionY = 0.0

    init {
        onDisable {
            reset()
        }

        listener<PacketEvent.Receive> {
            if (it.packet is SPacketPlayerPosLook) {
                reset()
            }
        }

        safeListener<PlayerMoveEvent.Pre>(-1000) {
            if (!runFastSwim()) reset()
        }
    }

    private fun SafeClientEvent.runFastSwim(): Boolean {
        if (player.isFlying || ElytraFlightNew.isActive()) return false

        when {
            isInLiquid(Material.LAVA) -> {
                if (!lava) return false
                lavaSwim()
            }
            isInLiquid(Material.WATER) -> {
                if (!water) return false
                waterSwim()
            }
            else -> {
                return false
            }
        }

        return true
    }

    private fun SafeClientEvent.isInLiquid(material: Material): Boolean {
        val box = player.entityBoundingBox
        val pos = BlockPos.PooledMutableBlockPos.retain()

        for (x in (box.minX + 0.1).fastFloor()..(box.maxX - 0.1).fastFloor()) {
            for (y in (box.minY + 0.5).fastFloor()..(box.maxY - 0.25).fastFloor()) {
                for (z in (box.minZ + 0.1).fastFloor()..(box.maxZ - 0.1).fastFloor()) {
                    val blockState = world.getBlockState(pos.setPos(x, y, z))
                    if (blockState.block !is BlockLiquid) return false
                    if (blockState.material != material) return false
                    if (BlockLiquid.getLiquidHeight(blockState, world, pos) - player.posY < 0.2) return false
                }
            }
        }

        pos.release()
        return true
    }

    private fun SafeClientEvent.lavaSwim() {
        ySwim(lavaVBoost, lavaUpSpeed, lavaDownSpeed)

        if (MovementUtils.isInputtingAny) {
            modifyTimer(50.0f / timerBoost)
        }

        if (MovementUtils.isInputting) {
            val yaw = calcMoveYaw()
            moveSpeed = min(max(moveSpeed * lavaHBoost, 0.05), lavaHSpeed / 20.0)
            player.motionX = -sin(yaw) * moveSpeed
            player.motionZ = cos(yaw) * moveSpeed
        } else {
            stopMotion()
        }
    }

    private fun SafeClientEvent.waterSwim() {
        ySwim(waterVBoost, waterUpSpeed, waterDownSpeed * 20.0f)

        if (MovementUtils.isInputtingAny) {
            modifyTimer(50.0f / timerBoost)
        }

        if (MovementUtils.isInputting) {
            val yaw = calcMoveYaw()
            val multiplier = player.speedEffectMultiplier

            moveSpeed = min(max(moveSpeed * waterHBoost, 0.075), waterHSpeed / 20.0)

            if (player.movementInput.sneak && !player.movementInput.jump) {
                val downMotion = player.motionY * 0.25
                moveSpeed = min(moveSpeed, max(moveSpeed + downMotion, 0.0))
            }

            player.motionX = -sin(yaw) * moveSpeed * multiplier
            player.motionZ = cos(yaw) * moveSpeed * multiplier
        } else {
            stopMotion()
        }
    }

    private fun SafeClientEvent.ySwim(vBoost: Float, upSpeed: Float, downSpeed: Float) {
        val jump = player.movementInput.jump
        val sneak = player.movementInput.sneak

        motionY = if (jump xor sneak) {
            if (jump) {
                min(motionY + vBoost / 20.0, upSpeed / 20.0)
            } else {
                max(motionY - vBoost / 20.0, -downSpeed / 20.0)
            }
        } else {
            val y = 0.1.pow(jitter)
            if (player.ticksExisted % 2 == 0) -y else y
        }

        player.motionY = motionY
    }

    private fun SafeClientEvent.stopMotion() {
        player.motionX = 0.0
        player.motionZ = 0.0
        moveSpeed = 0.0
    }

    private fun reset() {
        moveSpeed = 0.0
        motionY = 0.0
    }
}