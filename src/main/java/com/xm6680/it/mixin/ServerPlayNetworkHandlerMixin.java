package com.xm6680.it.mixin;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.item.ReceiverItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Unique
    private static final String IT_CHASE_COMMAND_BLOCK_MESSAGE = "你不能就这样逃走....";

    @Shadow
    @Final
    public ServerPlayerEntity player;

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void it$blockUnsignedCommandDuringChase(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (it$shouldBlockCommand(packet.command())) {
            it$sendCommandBlockedMessage();
            ci.cancel();
        }
    }

    @Inject(method = "onChatCommandSigned", at = @At("HEAD"), cancellable = true)
    private void it$blockSignedCommandDuringChase(ChatCommandSignedC2SPacket packet, CallbackInfo ci) {
        if (it$shouldBlockCommand(packet.command())) {
            it$sendCommandBlockedMessage();
            ci.cancel();
        }
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void it$preventReceiverClickDropPacket(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (!ItConfigManager.getConfig().receiverCannotBeDropped) {
            return;
        }

        ScreenHandler handler = player.currentScreenHandler;
        if (handler == null) {
            return;
        }

        if (it$isReceiverDropAttempt(handler, packet.slot(), packet.actionType())) {
            ReceiverItem.blockReceiverDrop(player);
            ci.cancel();
        }
    }

    @Unique
    private static boolean it$isReceiverDropAttempt(ScreenHandler handler, int slotIndex, SlotActionType actionType) {
        ItemStack cursorStack = handler.getCursorStack();
        if (slotIndex < 0) {
            return (actionType == SlotActionType.PICKUP || actionType == SlotActionType.THROW)
                    && cursorStack.isOf(ModItems.RECEIVER);
        }

        if (actionType != SlotActionType.THROW || slotIndex >= handler.slots.size()) {
            return false;
        }

        Slot slot = handler.slots.get(slotIndex);
        return slot.hasStack() && slot.getStack().isOf(ModItems.RECEIVER);
    }

    @Unique
    private boolean it$shouldBlockCommand(String command) {
        if (player == null || player.isSpectator()) {
            return false;
        }

        if (!ItMod.getChaseManager().isChasing(player) && !ItMod.getCaveStalkerManager().isActive(player)) {
            return false;
        }

        String normalized = command == null ? "" : command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }

        if (normalized.isEmpty()) {
            return false;
        }

        String root = normalized.split("\\s+", 2)[0].toLowerCase(java.util.Locale.ROOT);
        return !root.equals("it") && !root.equals("xm");
    }

    @Unique
    private void it$sendCommandBlockedMessage() {
        player.sendMessage(Text.literal(IT_CHASE_COMMAND_BLOCK_MESSAGE).formatted(Formatting.RED), false);
    }
}
