package com.xm6680.it.analog;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.ItMod;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Receiver state. Messages are persisted to the active world save by the
 * persistence manager.
 */
public class ReceiverManager {
    private final HorrorProgressionManager progressionManager;
    private final Map<UUID, Deque<ReceiverMessage>> messages = new HashMap<>();
    private final java.util.Set<UUID> signalBlockedPlayers = new java.util.HashSet<>();
    private final List<PendingReceiverMessage> pendingMessages = new ArrayList<>();

    public ReceiverManager(HorrorProgressionManager progressionManager) {
        this.progressionManager = progressionManager;
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        PlayerHorrorData data = progressionManager.getData(player);
        if (config.enableReceiver
                && ((config.giveReceiverOnFirstJoin && !data.hasReceivedReceiver)
                || (config.receiverAutoRestoreIfMissing && !hasReceiver(player)))) {
            giveReceiver(player, true);
        }
    }

    public void onPlayerRespawn(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (config.enableReceiver && (config.receiverKeepAfterDeath || config.receiverAutoRestoreIfMissing) && !hasReceiver(player)) {
            giveReceiver(player, true);
        }
    }

    public boolean giveReceiver(ServerPlayerEntity player, boolean bypassConfig) {
        if (!bypassConfig && !ItConfigManager.getConfig().enableReceiver) {
            return false;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        boolean firstReceiver = !data.hasReceivedReceiver || data.receiverFirstReceivedGameTime <= 0L;
        data.hasReceivedReceiver = true;
        if (firstReceiver) {
            data.receiverFirstReceivedGameTime = progressionManager.getProgressionTick(player);
            data.lastReceiverMessageGameTime = 0L;
            data.nextReceiverFallbackGameTime = 0L;
            data.receiverFallbackPhaseNumber = data.currentPhase.getNumber();
            data.receiverFallbacksInPhase = 0;
            if (ItConfigManager.getConfig().receiverFirstUseHintEnabled) {
                player.sendMessage(Text.literal("接收器正在震动。右键打开查看信号。"), true);
            }
        }

        if (hasReceiver(player)) {
            return true;
        }

        ItemStack stack = new ItemStack(ModItems.RECEIVER);
        if (player.getInventory().insertStack(stack)) {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
            return true;
        }

        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
        return false;
    }

    public boolean hasReceiver(ServerPlayerEntity player) {
        return player.getInventory().contains(stack -> stack.isOf(ModItems.RECEIVER));
    }

    public void openReceiver(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableReceiver) {
            player.sendMessage(Text.literal("旧式接收器已在配置中关闭。"), true);
            return;
        }

        progressionManager.recordReceiverOpened(player);
        ItMod.getCaveStalkerManager().onReceiverOpened(player, player.getEntityWorld().getServer().getTicks());

        PlayerHorrorData data = progressionManager.getData(player);
        data.lastReceiverOpenedGameTime = progressionManager.getProgressionTick(player);
        data.lastReceiverOpenedMessageCount = getMessageCount(player);
        data.unreadSignalPressure = 0.0D;
        List<ReceiverMessage> recentMessages = getRecentMessages(player, 10);
        if (config.enableReceiverGui) {
            ItNetwork.sendReceiverScreen(player, data, recentMessages);
            return;
        }

        if (recentMessages.isEmpty()) {
            player.sendMessage(Text.literal("没有接收到信号"), false);
            return;
        }

        for (ReceiverMessage message : recentMessages) {
            player.sendMessage(Text.literal(message.formattedText()), false);
        }
    }

    public boolean addMessage(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text) {
        return addMessage(player, type, phase, text, true, false);
    }

    public boolean addMessageSilently(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text) {
        return addMessage(player, type, phase, text, false, true);
    }

    public void scheduleMessage(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text, long delayTicks) {
        scheduleMessage(player, type, phase, text, delayTicks, false);
    }

    public void scheduleMessageAfterPhaseCooldown(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text, long delayTicks) {
        scheduleMessage(player, type, phase, text, delayTicks, true);
    }

    private void scheduleMessage(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text, long delayTicks, boolean waitForPhaseCooldown) {
        ItConfig config = ItConfigManager.getConfig();
        long cooldownDelayTicks = progressionManager.getReceiverPhaseCooldownRemainingTicks(player);
        if (!waitForPhaseCooldown && cooldownDelayTicks > 0L) {
            return;
        }

        if (!config.enableDelayedReceiverMessages && (!waitForPhaseCooldown || cooldownDelayTicks <= 0L)) {
            addMessage(player, type, phase, text);
            return;
        }

        long effectiveDelayTicks = Math.max(0L, delayTicks);
        if (config.enableDelayedReceiverMessages) {
            effectiveDelayTicks = Math.max(effectiveDelayTicks, Math.max(1, config.receiverEventMessageMinDelayTicks));
        }
        if (waitForPhaseCooldown) {
            effectiveDelayTicks += cooldownDelayTicks;
        }

        if (effectiveDelayTicks <= 0L) {
            addMessage(player, type, phase, text);
            return;
        }

        long currentTick = player.getEntityWorld().getServer().getTicks();
        pendingMessages.add(new PendingReceiverMessage(
                player.getUuid(),
                type,
                phase,
                text,
                currentTick + effectiveDelayTicks
        ));
    }

    public void tickDelayedMessages(MinecraftServer server, long currentTick) {
        if (pendingMessages.isEmpty()) {
            return;
        }

        var iterator = pendingMessages.iterator();
        while (iterator.hasNext()) {
            PendingReceiverMessage pending = iterator.next();
            if (currentTick < pending.deliverServerTick()) {
                continue;
            }

            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(pending.playerId());
            if (player == null || !player.isAlive() || player.isSpectator()) {
                continue;
            }

            addMessage(player, pending.type(), pending.phase(), pending.text());
        }
    }

    public void notifyStrongSignal(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("你的接收器传来了剧烈的震动...").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD), true);
        playStandardReceiverSignalSound(player);
    }

    private boolean addMessage(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text, boolean notify, boolean bypassSignalBlock) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableReceiver || !config.enableReceiverMessages) {
            return false;
        }

        if (!bypassSignalBlock && signalBlockedPlayers.contains(player.getUuid())) {
            return false;
        }

        if (!bypassSignalBlock && progressionManager.isReceiverPhaseCooldownActive(player)) {
            return false;
        }

        if (!hasReceiver(player)) {
            return false;
        }

        ReceiverMessage message = new ReceiverMessage(type, phase, System.currentTimeMillis() / 1000L, text);
        Deque<ReceiverMessage> playerMessages = messages.computeIfAbsent(player.getUuid(), uuid -> new ArrayDeque<>());
        playerMessages.addLast(message);

        while (playerMessages.size() > Math.max(1, config.maxReceiverMessages)) {
            playerMessages.removeFirst();
        }

        progressionManager.recordReceiverMessage(player);
        if (notify) {
            notifyNewSignal(player);
        }
        syncMessages(player);
        return true;
    }

    private void syncMessages(ServerPlayerEntity player) {
        ItNetwork.sendReceiverMessagesSync(player, progressionManager.getData(player), getRecentMessages(player, 10));
    }

    public int getMessageCount(ServerPlayerEntity player) {
        return messages.getOrDefault(player.getUuid(), new ArrayDeque<>()).size();
    }

    public Map<UUID, List<ReceiverMessage>> snapshotMessages() {
        Map<UUID, List<ReceiverMessage>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, Deque<ReceiverMessage>> entry : messages.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    public void replaceMessages(Map<UUID, List<ReceiverMessage>> loadedMessages) {
        messages.clear();
        int maxMessages = Math.max(1, ItConfigManager.getConfig().maxReceiverMessages);
        for (Map.Entry<UUID, List<ReceiverMessage>> entry : loadedMessages.entrySet()) {
            Deque<ReceiverMessage> playerMessages = new ArrayDeque<>();
            int skip = Math.max(0, entry.getValue().size() - maxMessages);
            for (int i = skip; i < entry.getValue().size(); i++) {
                playerMessages.addLast(entry.getValue().get(i));
            }
            messages.put(entry.getKey(), playerMessages);
        }
    }

    public int getStoredPlayerCount() {
        return messages.size();
    }

    public List<ReceiverMessage> getRecentMessages(ServerPlayerEntity player, int limit) {
        Deque<ReceiverMessage> playerMessages = messages.get(player.getUuid());
        if (playerMessages == null || playerMessages.isEmpty()) {
            return List.of();
        }

        int skip = Math.max(0, playerMessages.size() - limit);
        List<ReceiverMessage> recentMessages = new ArrayList<>();
        int index = 0;
        for (ReceiverMessage message : playerMessages) {
            if (index++ >= skip) {
                recentMessages.add(message);
            }
        }

        return recentMessages;
    }

    public void clearMessages(ServerPlayerEntity player) {
        messages.remove(player.getUuid());
        pendingMessages.removeIf(message -> message.playerId().equals(player.getUuid()));
    }

    public void setSignalBlocked(ServerPlayerEntity player, boolean blocked) {
        if (blocked) {
            signalBlockedPlayers.add(player.getUuid());
        } else {
            signalBlockedPlayers.remove(player.getUuid());
        }
    }

    public void clearSignalBlocked(UUID playerId) {
        signalBlockedPlayers.remove(playerId);
    }

    private void notifyNewSignal(ServerPlayerEntity player) {
        String[] notices = {
                "你的接收器震动了一下。",
                "接收器发出微弱的杂音。",
                "新的信号已被记录。"
        };
        int index = player.getRandom().nextBetween(0, notices.length - 1);
        player.sendMessage(Text.literal(notices[index]), true);
        playStandardReceiverSignalSound(player);
    }

    private void playStandardReceiverSignalSound(ServerPlayerEntity player) {
        sendReceiverSound(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1.0F, 1.35F);
        sendReceiverSound(player, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9F, 1.65F);
    }

    private void sendReceiverSound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        sendReceiverSound(player, Registries.SOUND_EVENT.getEntry(sound), volume, pitch);
    }

    private void sendReceiverSound(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(
                sound,
                SoundCategory.PLAYERS,
                player.getX(),
                player.getY() + 1.0,
                player.getZ(),
                volume,
                pitch,
                player.getRandom().nextLong()
        );
        player.networkHandler.sendPacket(packet);
    }
}
