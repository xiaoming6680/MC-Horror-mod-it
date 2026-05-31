package com.xm6680.it.item;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfigManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReceiverItem extends Item {
    private static final Map<UUID, Long> DROP_NOTICE_TICKS = new HashMap<>();

    public ReceiverItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            ItMod.getReceiverManager().openReceiver(serverPlayer);
        }

        return ActionResult.SUCCESS_SERVER;
    }

    public static boolean blockReceiverDrop(PlayerEntity player) {
        if (!ItConfigManager.getConfig().receiverCannotBeDropped) {
            return false;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            syncInventory(serverPlayer);
            notifyBlockedDrop(serverPlayer);
        }

        return true;
    }

    private static void syncInventory(ServerPlayerEntity player) {
        player.getInventory().markDirty();
        player.playerScreenHandler.sendContentUpdates();
        player.playerScreenHandler.syncState();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            player.currentScreenHandler.sendContentUpdates();
            player.currentScreenHandler.syncState();
        }
    }

    private static void notifyBlockedDrop(ServerPlayerEntity player) {
        long currentTick = player.getEntityWorld().getServer().getTicks();
        UUID uuid = player.getUuid();
        if (currentTick < DROP_NOTICE_TICKS.getOrDefault(uuid, 0L)) {
            return;
        }

        DROP_NOTICE_TICKS.put(uuid, currentTick + 45L);
        String[] messages = {
                "它没有离开你。",
                "接收器回到了你的手中。",
                "你无法丢掉它。"
        };
        int index = player.getRandom().nextBetween(0, messages.length - 1);
        player.sendMessage(Text.literal(messages[index]).formatted(Formatting.GRAY), true);
    }
}
