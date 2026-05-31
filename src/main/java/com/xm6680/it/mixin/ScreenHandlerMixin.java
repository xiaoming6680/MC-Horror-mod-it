package com.xm6680.it.mixin;

import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.item.ReceiverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.screen.ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Shadow
    public abstract ItemStack getCursorStack();

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void it$preventReceiverThrowFromInventory(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (it$cancelReceiverDropAttempt(slotIndex, actionType, player)) {
            ci.cancel();
        }
    }

    @Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
    private void it$preventReceiverInternalThrowFromInventory(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (it$cancelReceiverDropAttempt(slotIndex, actionType, player)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean it$cancelReceiverDropAttempt(int slotIndex, SlotActionType actionType, PlayerEntity player) {
        if (!ItConfigManager.getConfig().receiverCannotBeDropped) {
            return false;
        }

        boolean outsideClickWithReceiver = (actionType == SlotActionType.PICKUP || actionType == SlotActionType.THROW)
                && slotIndex < 0
                && getCursorStack().isOf(ModItems.RECEIVER);
        if (outsideClickWithReceiver) {
            ReceiverItem.blockReceiverDrop(player);
            return true;
        }

        if (actionType != SlotActionType.THROW) {
            return false;
        }

        if (slotIndex >= 0 && slotIndex < slots.size()) {
            Slot slot = slots.get(slotIndex);
            if (slot.hasStack() && slot.getStack().isOf(ModItems.RECEIVER)) {
                ReceiverItem.blockReceiverDrop(player);
                return true;
            }
            return false;
        }

        if (getCursorStack().isOf(ModItems.RECEIVER)) {
            ReceiverItem.blockReceiverDrop(player);
            return true;
        }

        return false;
    }
}
