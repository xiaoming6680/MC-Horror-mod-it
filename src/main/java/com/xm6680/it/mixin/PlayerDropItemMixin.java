package com.xm6680.it.mixin;

import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.item.ReceiverItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerDropItemMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void it$preventReceiverItemEntity(ItemStack stack, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (!ItConfigManager.getConfig().receiverCannotBeDropped || stack.isEmpty() || !stack.isOf(ModItems.RECEIVER)) {
            return;
        }

        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ReceiverItem.blockReceiverDrop(serverPlayer);
        }
        cir.setReturnValue(null);
    }
}
