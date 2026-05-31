package com.xm6680.it.mixin;

import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.item.ReceiverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void it$preventReceiverDrop(boolean entireStack, CallbackInfo ci) {
        if (!ItConfigManager.getConfig().receiverCannotBeDropped) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        ItemStack selectedStack = player.getInventory().getSelectedStack();
        if (selectedStack.isOf(ModItems.RECEIVER)) {
            ReceiverItem.blockReceiverDrop(player);
            ci.cancel();
        }
    }
}
