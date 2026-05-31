package com.xm6680.it.event;

import com.xm6680.it.ItMod;
import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.analog.ReceiverRecordPolicy;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.HorrorPhase;
import com.xm6680.it.watching.WatchingLevelManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles small horror events affected by phase and watching level.
 */
public class HorrorEventManager {
    private static final int TICKS_PER_SECOND = 20;

    private static final String[] MYSTERIOUS_CONTACT_SENDERS = {
            "???",
            "////",
            "###",
            "000",
            "%%%"
    };

    private static final String[] MYSTERIOUS_CONTACT_THREATS = {
            "别再往下走。",
            "你刚才看见它了吗？",
            "别相信太安静的地方。",
            "如果听见脚步，先别回头。",
            "光不一定能救你。",
            "你不是第一个拿到接收器的人。",
            "它已经开始记住你的路线。",
            "门不是风吹开的。",
            "你少数了一次脚步声。",
            "不要回复地下来的声音。",
            "有些影子不是墙上的。",
            "别让它知道你发现了。"
    };

    private static final String[] MYSTERIOUS_CONTACT_HELPFUL = {
            "它靠近时，接收器会先响。",
            "如果它追你，去找别人。",
            "不要在矿洞里只看前面。",
            "如果你被要求回头，你可以选择不回头。",
            "有些门不是被风吹开的。",
            "听见脚步后，先确认声音是不是跟着你移动。",
            "接收器记录晚几秒出现时，说明事情已经发生过了。",
            "太安静的矿道不一定安全。"
    };

    private static final String[] HAND_DROP_RECEIVER_MESSAGES = {
            "手部动作异常。",
            "物品脱离记录完成。",
            "检测到短暂失控。",
            "目标手部控制中断。"
    };

    private static final String[] INVENTORY_OPEN_RECEIVER_MESSAGES = {
            "背包访问记录异常。",
            "检测到非玩家打开行为。",
            "容器状态被短暂读取。",
            "物品栏焦点异常。"
    };

    private static final String[] NON_LAUGH_EERIE_SOUND_RECEIVER_MESSAGES = {
            "记录到异常声源。",
            "声源位置不稳定。",
            "声音不是从你面前传来的。",
            "矿道回声无法匹配。"
    };

    private static final String[] EERIE_LAUGH_RECEIVER_MESSAGES = {
            "接收器检测到短促笑声。"
    };

    private static final String[] FAKE_CHEST_RECEIVER_MESSAGES = {
            "容器访问记录异常。",
            "附近容器被短暂读取。",
            "检测到未登记的开箱行为。"
    };

    private static final String[] FAKE_BLOCK_PLACE_RECEIVER_MESSAGES = {
            "检测到未登记的放置行为。",
            "方块放置记录不存在。",
            "声音来源未找到。"
    };

    private static final String[] FAKE_EATING_RECEIVER_MESSAGES = {
            "检测到不属于你的进食声。",
            "咀嚼声来源不明。",
            "附近没有进食者。"
    };

    private static final String[][] FORCED_CHAT_SCRIPTS = {
            {
                    "它正在替我打字",
                    "它知道我删不掉",
                    "它就在聊天框后面"
            },
            {
                    "不要把它的名字发出去",
                    "它已经看见光标了",
                    "它会等我按下回车"
            },
            {
                    "这不是我想说的话",
                    "它把字一个个放进来",
                    "它不让我停下来"
            },
            {
                    "队伍里多出来的不是人",
                    "它学会了我们的名字",
                    "它正在学我的声音"
            }
    };

    private static final String[] FORCED_CHAT_RECEIVER_MESSAGES = {
            "输入记录异常。",
            "聊天栏焦点被外部占用。",
            "检测到未提交文本。",
            "玩家输入来源不一致。"
    };

    private final WatchingLevelManager watchingLevelManager;
    private final HorrorProgressionManager progressionManager;
    private final AnalogHorrorManager analogHorrorManager;
    private final Map<UUID, Long> nextCaveSoundTicks = new HashMap<>();
    private final Map<UUID, Long> nextMiningEchoTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeChatTicks = new HashMap<>();
    private final Map<UUID, Long> nextHandDropTicks = new HashMap<>();
    private final Map<UUID, Long> nextInventoryOpenTicks = new HashMap<>();
    private final Map<UUID, Long> nextEerieSoundTicks = new HashMap<>();
    private final Map<UUID, Long> nextFamiliarSoundTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeChestSoundTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeBlockPlaceSoundTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeEatingSoundTicks = new HashMap<>();
    private final Map<UUID, Long> nextForcedChatTicks = new HashMap<>();
    private final List<SoundSequence> activeSoundSequences = new ArrayList<>();
    private final Random random = new Random();

    public HorrorEventManager(WatchingLevelManager watchingLevelManager, HorrorProgressionManager progressionManager, AnalogHorrorManager analogHorrorManager) {
        this.watchingLevelManager = watchingLevelManager;
        this.progressionManager = progressionManager;
        this.analogHorrorManager = analogHorrorManager;
    }

    public void tickActiveSounds(MinecraftServer server, long currentTick) {
        Iterator<SoundSequence> iterator = activeSoundSequences.iterator();

        while (iterator.hasNext()) {
            SoundSequence sequence = iterator.next();
            if (!tickSoundSequence(server, currentTick, sequence)) {
                iterator.remove();
            }
        }
    }

    public void tryRandomEvents(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
                continue;
            }

            tryCaveSound(player, currentTick);
            tryFakeChat(player, currentTick);
            tryHandDrop(player, currentTick);
            tryInventoryOpen(player, currentTick);
            tryEerieSound(player, currentTick);
            tryFamiliarSound(player, currentTick);
            tryForcedChat(player, currentTick);
        }
    }

    public void onPlayerStartedMining(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableMiningEchoSounds || player.isSpectator() || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.WATCHING) || !isMiningEchoArea(player)) {
            return;
        }

        long currentTick = player.getEntityWorld().getServer().getTicks();
        UUID uuid = player.getUuid();
        if (currentTick < nextMiningEchoTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.035, 0.10, eventBalanceMultiplier(config.miningEchoChanceMultiplier));
        if (random.nextDouble() <= chance) {
            startMiningEchoSequence(player, currentTick);
            nextMiningEchoTicks.put(uuid, currentTick + secondsToTicks(randomBetween(55, 130)));
        }
    }

    public boolean triggerCaveSound(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableCaveSounds) {
            return false;
        }

        startFootstepSequence(player, player.getEntityWorld().getServer().getTicks(), true);
        return true;
    }

    public boolean triggerMiningEcho(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableMiningEchoSounds) {
            return false;
        }

        startMiningEchoSequence(player, player.getEntityWorld().getServer().getTicks(), true);
        return true;
    }

    public boolean triggerFakeChat(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeChat || !config.enableMysteriousContact) {
            return false;
        }

        sendMysteriousContact(player);
        return true;
    }

    public boolean triggerHandDrop(ServerPlayerEntity player) {
        return triggerHandDrop(player, true);
    }

    public boolean triggerInventoryOpen(ServerPlayerEntity player) {
        return triggerInventoryOpen(player, true);
    }

    public boolean triggerEerieSound(ServerPlayerEntity player) {
        return triggerEerieSound(player, true);
    }

    public boolean triggerEerieSoundLaugh(ServerPlayerEntity player) {
        return triggerEerieSound(player, true, EerieSoundType.LAUGH);
    }

    public boolean triggerFamiliarSound(ServerPlayerEntity player) {
        return triggerFamiliarSound(player, null, true);
    }

    public boolean triggerFamiliarSound(ServerPlayerEntity player, FamiliarSoundEventType forcedType) {
        return triggerFamiliarSound(player, forcedType, true);
    }

    public boolean triggerForcedChat(ServerPlayerEntity player) {
        return triggerForcedChat(player, true);
    }

    public boolean triggerNaturalFakeChat(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!config.enableFakeChat
                || !config.enableMysteriousContact
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.mysteriousContactMinPhase))
                || currentTick < nextFakeChatTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        sendMysteriousContact(player);
        nextFakeChatTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.mysteriousContactCooldownSeconds + randomBetween(-45, 90))));
        return true;
    }

    public boolean triggerNaturalHandDrop(ServerPlayerEntity player) {
        return triggerHandDrop(player, false);
    }

    public boolean triggerNaturalInventoryOpen(ServerPlayerEntity player) {
        return triggerInventoryOpen(player, false);
    }

    public boolean triggerNaturalEerieSound(ServerPlayerEntity player) {
        return triggerEerieSound(player, false);
    }

    public boolean triggerNaturalFamiliarSound(ServerPlayerEntity player) {
        return triggerFamiliarSound(player, null, false);
    }

    public boolean triggerNaturalFamiliarSound(ServerPlayerEntity player, FamiliarSoundEventType type) {
        return triggerFamiliarSound(player, type, false);
    }

    public boolean triggerNaturalForcedChat(ServerPlayerEntity player) {
        return triggerForcedChat(player, false);
    }

    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        nextCaveSoundTicks.remove(uuid);
        nextMiningEchoTicks.remove(uuid);
        nextFakeChatTicks.remove(uuid);
        nextHandDropTicks.remove(uuid);
        nextInventoryOpenTicks.remove(uuid);
        nextEerieSoundTicks.remove(uuid);
        nextFamiliarSoundTicks.remove(uuid);
        nextFakeChestSoundTicks.remove(uuid);
        nextFakeBlockPlaceSoundTicks.remove(uuid);
        nextFakeEatingSoundTicks.remove(uuid);
        nextForcedChatTicks.remove(uuid);
        activeSoundSequences.removeIf(sequence -> sequence.playerUuid.equals(uuid));
    }

    private void tryCaveSound(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableCaveSounds || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.WATCHING) || !isCaveSoundArea(player)) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextCaveSoundTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.006, 0.025, eventBalanceMultiplier(config.caveFootstepChanceMultiplier));
        if (random.nextDouble() <= chance) {
            startFootstepSequence(player, currentTick);
            nextCaveSoundTicks.put(uuid, currentTick + secondsToTicks(randomBetween(90, 180)));
        }
    }

    private void tryFakeChat(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeChat
                || !config.enableMysteriousContact
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.mysteriousContactMinPhase))) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextFakeChatTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.001, 0.006, 1.0);
        if (random.nextDouble() <= chance) {
            sendMysteriousContact(player);
            nextFakeChatTicks.put(uuid, currentTick + secondsToTicks(Math.max(60, config.mysteriousContactCooldownSeconds + randomBetween(-45, 90))));
        }
    }

    private void tryHandDrop(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableHandDropAnomaly
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.handDropMinPhase))) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextHandDropTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.0009, 0.0045, config.handDropChanceMultiplier);
        if (random.nextDouble() <= chance) {
            if (triggerHandDrop(player, false)) {
                nextHandDropTicks.put(uuid, currentTick + secondsToTicks(config.handDropCooldownSeconds));
            } else {
                nextHandDropTicks.put(uuid, currentTick + secondsToTicks(45));
            }
        }
    }

    private void tryInventoryOpen(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableInventoryOpenAnomaly
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.inventoryOpenMinPhase))) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextInventoryOpenTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.0007, 0.0038, config.inventoryOpenChanceMultiplier);
        if (random.nextDouble() <= chance) {
            if (triggerInventoryOpen(player, false)) {
                nextInventoryOpenTicks.put(uuid, currentTick + secondsToTicks(config.inventoryOpenCooldownSeconds));
            } else {
                nextInventoryOpenTicks.put(uuid, currentTick + secondsToTicks(45));
            }
        }
    }

    private void tryEerieSound(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableEerieSoundEvents
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.eerieSoundMinPhase))) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextEerieSoundTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.0024, 0.010, config.eerieSoundChanceMultiplier);
        if (random.nextDouble() <= chance) {
            if (triggerEerieSound(player, false)) {
                nextEerieSoundTicks.put(uuid, currentTick + secondsToTicks(config.eerieSoundCooldownSeconds));
            } else {
                nextEerieSoundTicks.put(uuid, currentTick + secondsToTicks(35));
            }
        }
    }

    private void tryFamiliarSound(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFamiliarSoundEvents
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.familiarSoundMinPhase))) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextFamiliarSoundTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.0015, 0.0065, config.familiarSoundChanceMultiplier);
        if (random.nextDouble() <= chance) {
            if (triggerFamiliarSound(player, null, false)) {
                nextFamiliarSoundTicks.put(uuid, currentTick + secondsToTicks(config.familiarSoundCooldownSeconds));
            } else {
                nextFamiliarSoundTicks.put(uuid, currentTick + secondsToTicks(40));
            }
        }
    }

    private void tryForcedChat(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableForcedChatEvent
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.forcedChatMinPhase))
                || isStrongEventActive(player, currentTick)
                || currentTick < nextForcedChatTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        double chance = scaledChance(player, 0.00065D, 0.0032D, config.forcedChatChanceMultiplier);
        if (random.nextDouble() <= chance) {
            triggerForcedChat(player, false);
        }
    }

    private boolean triggerHandDrop(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && (!config.enableHandDropAnomaly || currentTick < nextHandDropTicks.getOrDefault(player.getUuid(), 0L))) {
            return false;
        }

        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }

        if (!forced && (player.isCreative() || isStrongEventActive(player, currentTick))) {
            return false;
        }

        if (!forced && config.handDropPreventNearLava && isUnsafeHandDropArea(player)) {
            return false;
        }

        Hand hand = chooseDroppableHand(player, forced);
        if (hand == null) {
            return false;
        }

        ItemStack sourceStack = player.getStackInHand(hand);
        if (sourceStack.isEmpty() || sourceStack.isOf(ModItems.RECEIVER)) {
            return false;
        }

        boolean wholeStack = sourceStack.getCount() > 1 && random.nextDouble() < config.handDropDropWholeStackChance;
        ItemStack droppedStack;
        if (wholeStack) {
            droppedStack = sourceStack.copy();
            player.setStackInHand(hand, ItemStack.EMPTY);
        } else {
            droppedStack = sourceStack.split(1);
            if (sourceStack.isEmpty()) {
                player.setStackInHand(hand, ItemStack.EMPTY);
            }
        }

        Vec3d dropPos = findSafeHandDropPosition(player, forced);
        if (dropPos == null) {
            restoreDroppedStack(player, hand, droppedStack);
            syncInventory(player);
            return false;
        }

        ItemEntity itemEntity = new ItemEntity(player.getEntityWorld(), dropPos.x, dropPos.y, dropPos.z, droppedStack);
        itemEntity.setPickupDelay(8);
        itemEntity.setVelocity((random.nextDouble() - 0.5D) * 0.08D, 0.08D, (random.nextDouble() - 0.5D) * 0.08D);
        if (!player.getEntityWorld().spawnEntity(itemEntity)) {
            restoreDroppedStack(player, hand, droppedStack);
            syncInventory(player);
            return false;
        }

        syncInventory(player);

        if (random.nextBoolean()) {
            player.sendMessage(Text.literal("你的手抖了一下。").formatted(Formatting.GRAY), true);
        } else {
            player.sendMessage(Text.literal("有什么东西碰了你的手。").formatted(Formatting.GRAY), true);
        }

        progressionManager.recordHandDropAnomaly(player);
        analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, pick(HAND_DROP_RECEIVER_MESSAGES), config.handDropReceiverDelayTicks, ReceiverRecordPolicy.IMPORTANT);
        nextHandDropTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(15, config.handDropCooldownSeconds)));
        return true;
    }

    private boolean triggerInventoryOpen(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && (!config.enableInventoryOpenAnomaly || currentTick < nextInventoryOpenTicks.getOrDefault(player.getUuid(), 0L))) {
            return false;
        }

        if (player.isSpectator() || player.isCreative() || !player.isAlive() || isStrongEventActive(player, currentTick)) {
            return false;
        }

        if (player.currentScreenHandler != player.playerScreenHandler) {
            return false;
        }

        ItNetwork.sendOpenInventory(player);
        progressionManager.recordInventoryOpenAnomaly(player);
        analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.SYSTEM_ERROR, pick(INVENTORY_OPEN_RECEIVER_MESSAGES), config.inventoryOpenReceiverDelayTicks, ReceiverRecordPolicy.IMPORTANT);
        nextInventoryOpenTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(15, config.inventoryOpenCooldownSeconds)));
        return true;
    }

    private boolean triggerForcedChat(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && (!config.enableForcedChatEvent || currentTick < nextForcedChatTicks.getOrDefault(player.getUuid(), 0L))) {
            return false;
        }

        if (player.isSpectator() || !player.isAlive()) {
            return false;
        }

        if (!forced && (!progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.forcedChatMinPhase)) || isStrongEventActive(player, currentTick))) {
            return false;
        }

        String[] script = FORCED_CHAT_SCRIPTS[random.nextInt(FORCED_CHAT_SCRIPTS.length)];
        ItNetwork.sendForcedChat(player, List.of(script), 2, 18, 20);
        analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.SYSTEM_ERROR, pick(FORCED_CHAT_RECEIVER_MESSAGES), config.forcedChatReceiverDelayTicks, ReceiverRecordPolicy.SAMPLED);
        nextForcedChatTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.forcedChatCooldownSeconds)));
        return true;
    }

    private boolean triggerEerieSound(ServerPlayerEntity player, boolean forced) {
        return triggerEerieSound(player, forced, null);
    }

    private boolean triggerEerieSound(ServerPlayerEntity player, boolean forced, EerieSoundType forcedType) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && (!config.enableEerieSoundEvents || currentTick < nextEerieSoundTicks.getOrDefault(player.getUuid(), 0L))) {
            return false;
        }

        if (player.isSpectator() || !player.isAlive() || isStrongEventActive(player, currentTick)) {
            return false;
        }

        EerieSoundType soundType = forcedType == null ? chooseEerieSoundType(config) : forcedType;
        playEerieSound(player, config, soundType);
        progressionManager.recordEerieSoundEvent(player);
        ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "eerie_sound");
        String receiverMessage = soundType == EerieSoundType.LAUGH
                ? pick(EERIE_LAUGH_RECEIVER_MESSAGES)
                : pick(NON_LAUGH_EERIE_SOUND_RECEIVER_MESSAGES);
        if (soundType == EerieSoundType.LAUGH) {
            analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, receiverMessage, config.eerieSoundReceiverDelayTicks, ReceiverRecordPolicy.SAMPLED);
        } else if (config.enableReceiverForGenericEerieSound || config.enableReceiverForMinorSounds) {
            analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, receiverMessage, config.eerieSoundReceiverDelayTicks, ReceiverRecordPolicy.RARE);
        }
        nextEerieSoundTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(15, config.eerieSoundCooldownSeconds)));
        return true;
    }

    private boolean triggerFamiliarSound(ServerPlayerEntity player, FamiliarSoundEventType forcedType, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && (!config.enableFamiliarSoundEvents || currentTick < nextFamiliarSoundTicks.getOrDefault(player.getUuid(), 0L))) {
            return false;
        }

        if (player.isSpectator() || !player.isAlive() || (!forced && isStrongEventActive(player, currentTick))) {
            return false;
        }

        FamiliarSoundEventType type = forcedType == null ? chooseFamiliarSoundType(player, config, currentTick, forced) : forcedType;
        if (type == null) {
            return false;
        }

        boolean triggered = switch (type) {
            case CHEST -> triggerFakeChestSound(player, forced, currentTick);
            case BLOCK_PLACE -> triggerFakeBlockPlaceSound(player, forced, currentTick);
            case EATING -> triggerFakeEatingSound(player, forced, currentTick);
        };

        if (triggered) {
            progressionManager.recordFamiliarSoundEvent(player);
            nextFamiliarSoundTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(30, config.familiarSoundCooldownSeconds)));
        }

        return triggered;
    }

    private FamiliarSoundEventType chooseFamiliarSoundType(ServerPlayerEntity player, ItConfig config, long currentTick, boolean forced) {
        List<FamiliarSoundEventType> available = new ArrayList<>();
        if (config.enableFakeChestSounds && (forced || currentTick >= nextFakeChestSoundTicks.getOrDefault(player.getUuid(), 0L))) {
            available.add(FamiliarSoundEventType.CHEST);
        }
        if (config.enableFakeBlockPlaceSounds && (forced || currentTick >= nextFakeBlockPlaceSoundTicks.getOrDefault(player.getUuid(), 0L))) {
            available.add(FamiliarSoundEventType.BLOCK_PLACE);
        }
        if (config.enableFakeEatingSounds && (forced || currentTick >= nextFakeEatingSoundTicks.getOrDefault(player.getUuid(), 0L))) {
            available.add(FamiliarSoundEventType.EATING);
        }

        while (!available.isEmpty()) {
            FamiliarSoundEventType type = available.remove(random.nextInt(available.size()));
            if (type != FamiliarSoundEventType.CHEST || !config.fakeChestSoundRequiresNearbyContainer || findNearbyContainer(player, config.fakeChestSoundRadius) != null) {
                return type;
            }
        }

        return null;
    }

    private boolean triggerFakeChestSound(ServerPlayerEntity player, boolean forced, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeChestSounds && !forced) {
            return false;
        }

        if (!forced && currentTick < nextFakeChestSoundTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        BlockPos containerPos = findNearbyContainer(player, config.fakeChestSoundRadius);
        if (containerPos == null && config.fakeChestSoundRequiresNearbyContainer) {
            return false;
        }

        if (containerPos == null) {
            containerPos = player.getBlockPos();
        }

        SoundEvent openSound = containerOpenSound(player.getEntityWorld().getBlockState(containerPos));
        SoundEvent closeSound = containerCloseSound(player.getEntityWorld().getBlockState(containerPos));
        sendSoundToPlayer(player, openSound, SoundCategory.BLOCKS, containerPos.getX() + 0.5D, containerPos.getY() + 0.5D, containerPos.getZ() + 0.5D, 0.72F, 0.88F + random.nextFloat() * 0.12F);
        activeSoundSequences.add(SoundSequence.createContainerClose(player.getUuid(), currentTick + randomBetween(10, 30), containerPos, closeSound));
        analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, pick(FAKE_CHEST_RECEIVER_MESSAGES), config.familiarSoundReceiverDelayTicks, ReceiverRecordPolicy.SAMPLED);
        nextFakeChestSoundTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(30, config.fakeChestSoundCooldownSeconds)));
        return true;
    }

    private boolean triggerFakeBlockPlaceSound(ServerPlayerEntity player, boolean forced, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeBlockPlaceSounds && !forced) {
            return false;
        }

        if (!forced && currentTick < nextFakeBlockPlaceSoundTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        Vec3d source = rearOrSideSource(player, 4.0D, 10.0D);
        SoundEvent sound = switch (random.nextInt(5)) {
            case 0 -> SoundEvents.BLOCK_STONE_PLACE;
            case 1 -> SoundEvents.BLOCK_DEEPSLATE_PLACE;
            case 2 -> Blocks.OAK_PLANKS.getDefaultState().getSoundGroup().getPlaceSound();
            case 3 -> SoundEvents.BLOCK_GRAVEL_PLACE;
            default -> SoundEvents.BLOCK_GRASS_PLACE;
        };
        activeSoundSequences.add(SoundSequence.createRepeatedSound(
                SoundSequenceType.FAMILIAR_BLOCK_PLACE,
                player.getUuid(),
                currentTick,
                angleTo(player, source),
                Math.max(4.0D, distanceToPlayer(player, source)),
                source.y - player.getY(),
                randomBetween(2, 3),
                3,
                7,
                sound
        ));
        ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "fake_block_place");
        if (config.enableReceiverForFakeBlockPlaceSound || config.enableReceiverForMinorSounds) {
            analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, pick(FAKE_BLOCK_PLACE_RECEIVER_MESSAGES), config.familiarSoundReceiverDelayTicks, ReceiverRecordPolicy.RARE);
        }
        nextFakeBlockPlaceSoundTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(30, config.fakeBlockPlaceSoundCooldownSeconds)));
        return true;
    }

    private boolean triggerFakeEatingSound(ServerPlayerEntity player, boolean forced, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeEatingSounds && !forced) {
            return false;
        }

        if (!forced && currentTick < nextFakeEatingSoundTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        Vec3d source = rearOrSideSource(player, 3.0D, 8.0D);
        activeSoundSequences.add(SoundSequence.createRepeatedSound(
                SoundSequenceType.FAMILIAR_EATING,
                player.getUuid(),
                currentTick,
                angleTo(player, source),
                Math.max(3.0D, distanceToPlayer(player, source)),
                source.y - player.getY(),
                randomBetween(2, 4),
                5,
                10,
                SoundEvents.ENTITY_GENERIC_EAT
        ));
        ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "fake_eating");
        if (config.enableReceiverForFakeEatingSound || config.enableReceiverForMinorSounds) {
            analogHorrorManager.tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, pick(FAKE_EATING_RECEIVER_MESSAGES), config.familiarSoundReceiverDelayTicks, ReceiverRecordPolicy.RARE);
        }
        nextFakeEatingSoundTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(30, config.fakeEatingSoundCooldownSeconds)));
        return true;
    }

    private boolean tickSoundSequence(MinecraftServer server, long currentTick, SoundSequence sequence) {
        ItConfig config = ItConfigManager.getConfig();
        if (sequence.type == SoundSequenceType.FOOTSTEPS && !config.enableCaveSounds) {
            return false;
        }

        if (sequence.type == SoundSequenceType.MINING_ECHO && !config.enableMiningEchoSounds) {
            return false;
        }

        if (sequence.type == SoundSequenceType.CONTAINER_CLOSE && !config.enableFamiliarSoundEvents) {
            return false;
        }

        if (sequence.type == SoundSequenceType.FAMILIAR_BLOCK_PLACE && !config.enableFakeBlockPlaceSounds) {
            return false;
        }

        if (sequence.type == SoundSequenceType.FAMILIAR_EATING && !config.enableFakeEatingSounds) {
            return false;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(sequence.playerUuid);
        if (player == null || player.isSpectator()) {
            return false;
        }

        if (currentTick < sequence.nextSoundTick) {
            return true;
        }

        if (sequence.type == SoundSequenceType.MINING_ECHO) {
            return tickMiningEchoSound(player, sequence, currentTick);
        }

        if (sequence.type == SoundSequenceType.CONTAINER_CLOSE) {
            BlockPos pos = sequence.blockPos;
            if (pos == null) {
                return false;
            }
            sendSoundToPlayer(player, sequence.singleSound, SoundCategory.BLOCKS, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.68F, 0.84F + random.nextFloat() * 0.12F);
            return false;
        }

        if (sequence.type == SoundSequenceType.FAMILIAR_BLOCK_PLACE || sequence.type == SoundSequenceType.FAMILIAR_EATING) {
            return tickFamiliarRepeatedSound(player, sequence, currentTick, config);
        }

        playFootstepSound(player, sequence);
        sequence.playedSounds++;
        if (sequence.playedSounds >= sequence.totalSounds) {
            return false;
        }

        sequence.nextSoundTick = currentTick + randomBetween(sequence.minDelayTicks, sequence.maxDelayTicks);
        return true;
    }

    private boolean tickFamiliarRepeatedSound(ServerPlayerEntity player, SoundSequence sequence, long currentTick, ItConfig config) {
        SoundPosition pos = sequence.getPositionNear(player);
        SoundCategory category = sequence.type == SoundSequenceType.FAMILIAR_BLOCK_PLACE ? SoundCategory.BLOCKS : SoundCategory.PLAYERS;
        float volume = sequence.type == SoundSequenceType.FAMILIAR_BLOCK_PLACE ? 0.70F : 0.68F;
        float pitch = sequence.type == SoundSequenceType.FAMILIAR_BLOCK_PLACE ? 0.82F + random.nextFloat() * 0.18F : 0.86F + random.nextFloat() * 0.20F;
        sendSoundToPlayer(player, sequence.singleSound, category, pos.x, pos.y, pos.z, volume, pitch);
        sequence.playedSounds++;
        if (sequence.playedSounds >= sequence.totalSounds) {
            if (sequence.type == SoundSequenceType.FAMILIAR_EATING && random.nextDouble() < config.fakeEatingSoundBurpChance) {
                sendSoundToPlayer(player, SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, pos.x, pos.y, pos.z, 0.48F, 0.78F + random.nextFloat() * 0.12F);
            }
            return false;
        }

        sequence.nextSoundTick = currentTick + randomBetween(sequence.minDelayTicks, sequence.maxDelayTicks);
        return true;
    }

    private boolean isCaveSoundArea(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();

        boolean dark = world.getLightLevel(pos) <= 8;
        boolean underground = player.getY() < 50.0 || (!world.isSkyVisible(pos) && player.getY() < world.getSeaLevel() + 8);

        return dark && underground;
    }

    private boolean isMiningEchoArea(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();

        boolean dim = world.getLightLevel(pos) <= 10;
        boolean undergroundOrEnclosed = player.getY() < 56.0 || (!world.isSkyVisible(pos) && player.getY() < world.getSeaLevel() + 12);

        return dim && undergroundOrEnclosed;
    }

    private void startFootstepSequence(ServerPlayerEntity player, long currentTick) {
        startFootstepSequence(player, currentTick, false);
    }

    private void startFootstepSequence(ServerPlayerEntity player, long currentTick, boolean forcedReceiverRecord) {
        SoundSequence sequence = SoundSequence.create(
                SoundSequenceType.FOOTSTEPS,
                player.getUuid(),
                currentTick + randomBetween(2, 8),
                random.nextDouble() * Math.PI * 2.0,
                8.0 + random.nextDouble() * 6.0,
                randomBetween(-2, 1),
                randomBetween(13, 20),
                2,
                5
        );

        activeSoundSequences.add(sequence);
        progressionManager.recordCaveFootstep(player);
        analogHorrorManager.onCaveFootstep(player, forcedReceiverRecord);
    }

    private void startMiningEchoSequence(ServerPlayerEntity player, long currentTick) {
        startMiningEchoSequence(player, currentTick, false);
    }

    private void startMiningEchoSequence(ServerPlayerEntity player, long currentTick, boolean forcedReceiverRecord) {
        MiningSoundSet soundSet = chooseMiningSoundSet();
        SoundSequence sequence = SoundSequence.createMiningEcho(
                player.getUuid(),
                currentTick + randomBetween(3, 8),
                random.nextDouble() * Math.PI * 2.0,
                random.nextDouble() * Math.PI * 2.0,
                7.0 + random.nextDouble() * 5.0,
                randomBetween(-2, 2),
                randomBetween(2, 4),
                randomBetween(5, 8),
                soundSet.hitSound(),
                soundSet.breakSound()
        );

        activeSoundSequences.add(sequence);
        progressionManager.recordMiningEcho(player);
        analogHorrorManager.onMiningEcho(player, forcedReceiverRecord);
    }

    private void playFootstepSound(ServerPlayerEntity player, SoundSequence sequence) {
        SoundPosition position = sequence.getPositionNear(player);
        SoundEvent footstepSound = random.nextBoolean() ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_GRAVEL_STEP;

        sendSoundToPlayer(
                player,
                footstepSound,
                SoundCategory.AMBIENT,
                position.x,
                position.y,
                position.z,
                0.56F,
                0.82F + random.nextFloat() * 0.24F
        );

        if (sequence.playedSounds == 0 && random.nextDouble() < 0.25) {
            sendSoundToPlayer(
                    player,
                    SoundEvents.AMBIENT_CAVE,
                    SoundCategory.AMBIENT,
                    position.x,
                    position.y,
                    position.z,
                    0.18F,
                    0.75F + random.nextFloat() * 0.15F
            );
        }
    }

    private boolean tickMiningEchoSound(ServerPlayerEntity player, SoundSequence sequence, long currentTick) {
        SoundPosition position = sequence.getMiningBlockPosition(player);

        if (sequence.miningHitsThisBlock < sequence.miningHitsNeededForBlock) {
            sendSoundToPlayer(
                    player,
                    sequence.miningHitSound,
                    SoundCategory.BLOCKS,
                    position.x,
                    position.y,
                    position.z,
                    0.74F,
                    0.78F + random.nextFloat() * 0.12F
            );

            sequence.miningHitsThisBlock++;
            sequence.nextSoundTick = currentTick + randomBetween(4, 7);
            return true;
        }

        sendSoundToPlayer(
                player,
                sequence.miningBreakSound,
                SoundCategory.BLOCKS,
                position.x,
                position.y,
                position.z,
                0.82F,
                0.82F + random.nextFloat() * 0.12F
        );

        sequence.minedBlocks++;
        if (sequence.minedBlocks >= sequence.totalMiningBlocks) {
            return false;
        }

        sequence.startNextMiningBlock(randomBetween(5, 8));
        sequence.nextSoundTick = currentTick + randomBetween(10, 18);
        return true;
    }

    private MiningSoundSet chooseMiningSoundSet() {
        if (random.nextBoolean()) {
            return new MiningSoundSet(SoundEvents.BLOCK_DEEPSLATE_HIT, SoundEvents.BLOCK_DEEPSLATE_BREAK);
        }

        return new MiningSoundSet(SoundEvents.BLOCK_STONE_HIT, SoundEvents.BLOCK_STONE_BREAK);
    }

    private EerieSoundType chooseEerieSoundType(ItConfig config) {
        double laughWeight = Math.max(0.0D, config.eerieSoundLaughWeight);
        double caveWeight = 1.0D;
        double staticWeight = 1.0D;
        double behindWeight = config.eerieSoundAllowBehindPlayer ? 1.0D : 0.6D;
        double total = laughWeight + caveWeight + staticWeight + behindWeight;
        if (total <= 0.0D) {
            return EerieSoundType.CAVE_AMBIENT;
        }

        double roll = random.nextDouble() * total;
        if (roll < laughWeight) {
            return EerieSoundType.LAUGH;
        }
        roll -= laughWeight;
        if (roll < caveWeight) {
            return EerieSoundType.CAVE_AMBIENT;
        }
        roll -= caveWeight;
        if (roll < staticWeight) {
            return EerieSoundType.WHISPER_STATIC;
        }
        return EerieSoundType.BEHIND_PLAYER;
    }

    private void playEerieSound(ServerPlayerEntity player, ItConfig config, EerieSoundType type) {
        float baseVolume = Math.max(0.0F, config.eerieSoundVolume);
        Vec3d source = switch (type) {
            case BEHIND_PLAYER -> config.eerieSoundAllowBehindPlayer ? behindPlayer(player, 5.0D + random.nextDouble() * 4.0D) : nearbySource(player, 7.0D);
            case CAVE_AMBIENT -> nearbySource(player, 9.0D + random.nextDouble() * 7.0D);
            case LAUGH -> config.eerieSoundAllowBehindPlayer ? behindPlayer(player, 6.0D + random.nextDouble() * 3.0D) : nearbySource(player, 8.0D);
            case WHISPER_STATIC -> nearbySource(player, 6.0D + random.nextDouble() * 8.0D);
        };

        switch (type) {
            case LAUGH -> {
                sendSoundToPlayer(player, SoundEvents.ENTITY_WITCH_AMBIENT, SoundCategory.AMBIENT, source, baseVolume * 0.42F, 0.74F + random.nextFloat() * 0.10F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_WITCH_CELEBRATE, SoundCategory.AMBIENT, source, baseVolume * 0.26F, 0.58F + random.nextFloat() * 0.10F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_FOX_SCREECH, SoundCategory.AMBIENT, source, baseVolume * 0.30F, 0.50F + random.nextFloat() * 0.08F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.AMBIENT, source, baseVolume * 0.20F, 0.62F + random.nextFloat() * 0.12F);
            }
            case CAVE_AMBIENT -> {
                sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, baseVolume * 1.05F, 0.55F + random.nextFloat() * 0.08F);
                sendSoundToPlayer(player, SoundEvents.BLOCK_DEEPSLATE_HIT, SoundCategory.AMBIENT, source, baseVolume * 0.42F, 0.52F + random.nextFloat() * 0.08F);
                sendSoundToPlayer(player, SoundEvents.BLOCK_DRIPSTONE_BLOCK_STEP, SoundCategory.AMBIENT, source, baseVolume * 0.28F, 0.64F + random.nextFloat() * 0.08F);
            }
            case WHISPER_STATIC -> {
                sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, source, baseVolume * 0.45F, 0.72F + random.nextFloat() * 0.18F);
                sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, source, baseVolume * 0.35F, 1.55F + random.nextFloat() * 0.18F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, SoundCategory.AMBIENT, source, baseVolume * 0.22F, 0.48F + random.nextFloat() * 0.08F);
            }
            case BEHIND_PLAYER -> {
                sendSoundToPlayer(player, SoundEvents.BLOCK_STONE_STEP, SoundCategory.AMBIENT, source, baseVolume * 0.65F, 0.62F + random.nextFloat() * 0.08F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.AMBIENT, source, baseVolume * 0.25F, 0.45F + random.nextFloat() * 0.08F);
            }
        }
    }

    public boolean hasNearbyContainerForFakeChest(ServerPlayerEntity player) {
        return findNearbyContainer(player, ItConfigManager.getConfig().fakeChestSoundRadius) != null;
    }

    private BlockPos findNearbyContainer(ServerPlayerEntity player, double radius) {
        ServerWorld world = player.getEntityWorld();
        BlockPos center = player.getBlockPos();
        int searchRadius = Math.max(2, (int) Math.ceil(radius));
        double maxDistanceSquared = radius * radius;
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos pos : BlockPos.iterate(center.add(-searchRadius, -4, -searchRadius), center.add(searchRadius, 4, searchRadius))) {
            double dx = center.getX() - pos.getX();
            double dy = center.getY() - pos.getY();
            double dz = center.getZ() - pos.getZ();
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }

            BlockState state = world.getBlockState(pos);
            if (isContainerBlock(state) && !hasPlayerOpenNearbyContainer(player, pos)) {
                candidates.add(pos.toImmutable());
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    private boolean isContainerBlock(BlockState state) {
        return state.isOf(Blocks.CHEST)
                || state.isOf(Blocks.TRAPPED_CHEST)
                || state.isOf(Blocks.BARREL)
                || state.isOf(Blocks.ENDER_CHEST)
                || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean hasPlayerOpenNearbyContainer(ServerPlayerEntity target, BlockPos containerPos) {
        for (ServerPlayerEntity player : target.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != target.getEntityWorld()) {
                continue;
            }

            if (player.squaredDistanceTo(containerPos.getX() + 0.5D, containerPos.getY() + 0.5D, containerPos.getZ() + 0.5D) <= 4.0D * 4.0D
                    && player.currentScreenHandler != player.playerScreenHandler) {
                return true;
            }
        }

        return false;
    }

    private SoundEvent containerOpenSound(BlockState state) {
        if (state.isOf(Blocks.BARREL)) {
            return SoundEvents.BLOCK_BARREL_OPEN;
        }
        if (state.isOf(Blocks.ENDER_CHEST)) {
            return SoundEvents.BLOCK_ENDER_CHEST_OPEN;
        }
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            return SoundEvents.BLOCK_SHULKER_BOX_OPEN;
        }
        return SoundEvents.BLOCK_CHEST_OPEN;
    }

    private SoundEvent containerCloseSound(BlockState state) {
        if (state.isOf(Blocks.BARREL)) {
            return SoundEvents.BLOCK_BARREL_CLOSE;
        }
        if (state.isOf(Blocks.ENDER_CHEST)) {
            return SoundEvents.BLOCK_ENDER_CHEST_CLOSE;
        }
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            return SoundEvents.BLOCK_SHULKER_BOX_CLOSE;
        }
        return SoundEvents.BLOCK_CHEST_CLOSE;
    }

    private Vec3d rearOrSideSource(ServerPlayerEntity player, double minDistance, double maxDistance) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01D) {
            look = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        Vec3d horizontalLook = new Vec3d(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSquared() < 0.01D) {
            horizontalLook = new Vec3d(0.0D, 0.0D, 1.0D);
        } else {
            horizontalLook = horizontalLook.normalize();
        }

        Vec3d side = new Vec3d(-horizontalLook.z, 0.0D, horizontalLook.x);
        if (random.nextBoolean()) {
            side = side.multiply(-1.0D);
        }

        double distance = minDistance + random.nextDouble() * Math.max(0.5D, maxDistance - minDistance);
        double sideDistance = (random.nextDouble() * 2.0D - 1.0D) * 2.5D;
        return new Vec3d(player.getX(), player.getY() + 0.25D, player.getZ())
                .subtract(horizontalLook.multiply(distance))
                .add(side.multiply(sideDistance));
    }

    private double angleTo(ServerPlayerEntity player, Vec3d source) {
        return Math.atan2(source.z - player.getZ(), source.x - player.getX());
    }

    private double distanceToPlayer(ServerPlayerEntity player, Vec3d source) {
        double dx = source.x - player.getX();
        double dz = source.z - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void sendMysteriousContact(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        boolean helpful = random.nextDouble() < config.mysteriousContactHelpfulChance;
        String line = helpful ? pick(MYSTERIOUS_CONTACT_HELPFUL) : pick(MYSTERIOUS_CONTACT_THREATS);

        player.sendMessage(Text.literal("")
                .append(Text.literal(pick(MYSTERIOUS_CONTACT_SENDERS)).formatted(Formatting.GRAY, Formatting.ITALIC, Formatting.OBFUSCATED))
                .append(Text.literal("悄悄地对你说：" + line).formatted(Formatting.GRAY, Formatting.ITALIC)), false);
        progressionManager.recordMysteriousContact(player);
        analogHorrorManager.onFakeChat(player);
    }

    private Hand chooseDroppableHand(ServerPlayerEntity player, boolean forced) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        if (forced && !mainHand.isEmpty() && !mainHand.isOf(ModItems.RECEIVER)) {
            return Hand.MAIN_HAND;
        }

        if (forced && !offHand.isEmpty() && !offHand.isOf(ModItems.RECEIVER)) {
            return Hand.OFF_HAND;
        }

        List<Hand> hands = new ArrayList<>(2);
        if (!mainHand.isEmpty() && !mainHand.isOf(ModItems.RECEIVER)) {
            hands.add(Hand.MAIN_HAND);
        }
        if (!offHand.isEmpty() && !offHand.isOf(ModItems.RECEIVER)) {
            hands.add(Hand.OFF_HAND);
        }

        if (hands.isEmpty()) {
            return null;
        }

        return hands.get(random.nextInt(hands.size()));
    }

    private boolean isUnsafeHandDropArea(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        if (player.getY() <= world.getBottomY() + 3.0D) {
            return true;
        }

        BlockPos center = player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(center.add(-2, -1, -2), center.add(2, 2, 2))) {
            if (isDangerousBlock(world, pos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isDangerousBlock(ServerWorld world, BlockPos pos) {
        if (world.getFluidState(pos).isIn(FluidTags.LAVA)) {
            return true;
        }

        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.FIRE)
                || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.WITHER_ROSE);
    }

    private Vec3d findSafeHandDropPosition(ServerPlayerEntity player, boolean forced) {
        ServerWorld world = player.getEntityWorld();
        Vec3d base = new Vec3d(player.getX(), player.getY() + 0.35D, player.getZ());
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01D) {
            look = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        Vec3d horizontalLook = new Vec3d(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSquared() < 0.01D) {
            horizontalLook = new Vec3d(0.0D, 0.0D, 1.0D);
        } else {
            horizontalLook = horizontalLook.normalize();
        }

        Vec3d side = new Vec3d(-horizontalLook.z, 0.0D, horizontalLook.x);
        List<Vec3d> candidates = List.of(
                base.add(horizontalLook.multiply(0.85D)),
                base.add(horizontalLook.multiply(1.25D)),
                base.add(horizontalLook.multiply(0.65D)).add(side.multiply(0.55D)),
                base.add(horizontalLook.multiply(0.65D)).subtract(side.multiply(0.55D)),
                base,
                base.subtract(horizontalLook.multiply(0.55D))
        );

        for (Vec3d candidate : candidates) {
            if (isSafeItemDropPosition(world, candidate, forced)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean isSafeItemDropPosition(ServerWorld world, Vec3d position, boolean forced) {
        if (position.y <= world.getBottomY() + 2.0D) {
            return false;
        }

        BlockPos pos = BlockPos.ofFloored(position);
        BlockPos below = pos.down();
        if (isDangerousBlock(world, pos) || isDangerousBlock(world, below) || isDangerousBlock(world, pos.up())) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (state.isSolidBlock(world, pos)) {
            return false;
        }

        for (int i = 1; i <= 5; i++) {
            BlockPos floor = pos.down(i);
            if (floor.getY() <= world.getBottomY()) {
                return false;
            }

            if (isDangerousBlock(world, floor)) {
                return false;
            }

            BlockState floorState = world.getBlockState(floor);
            if (floorState.isSolidBlock(world, floor)) {
                return true;
            }
        }

        return forced;
    }

    private void restoreDroppedStack(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        ItemStack heldStack = player.getStackInHand(hand);
        if (heldStack.isEmpty()) {
            player.setStackInHand(hand, stack);
            return;
        }

        if (ItemStack.areItemsAndComponentsEqual(heldStack, stack) && heldStack.getCount() < heldStack.getMaxCount()) {
            int transferable = Math.min(stack.getCount(), heldStack.getMaxCount() - heldStack.getCount());
            heldStack.increment(transferable);
            stack.decrement(transferable);
        }

        if (!stack.isEmpty() && !player.getInventory().insertStack(stack)) {
            player.setStackInHand(hand, stack);
        }
    }

    private void syncInventory(ServerPlayerEntity player) {
        player.getInventory().markDirty();
        player.playerScreenHandler.sendContentUpdates();
        player.playerScreenHandler.syncState();
        if (player.currentScreenHandler != player.playerScreenHandler) {
            player.currentScreenHandler.sendContentUpdates();
            player.currentScreenHandler.syncState();
        }
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return ItMod.getChaseManager().isChasing(player)
                || ItMod.getCaveStalkerManager().isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick)
                || ItNetwork.isAnimalDisguiseRetaliationActive(player, currentTick);
    }

    private Vec3d nearbySource(ServerPlayerEntity player, double distance) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double yOffset = -1.0D + random.nextDouble() * 3.0D;
        return new Vec3d(
                player.getX() + Math.cos(angle) * distance,
                player.getY() + yOffset,
                player.getZ() + Math.sin(angle) * distance
        );
    }

    private Vec3d behindPlayer(ServerPlayerEntity player, double distance) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01D) {
            look = new Vec3d(0.0D, 0.0D, 1.0D);
        }
        return new Vec3d(player.getX(), player.getY() + 0.8D, player.getZ()).subtract(look.multiply(distance));
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, x, y, z, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, sound, category, pos.x, pos.y, pos.z, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, sound, category, pos.x, pos.y, pos.z, volume, pitch);
    }

    private double scaledChance(ServerPlayerEntity player, double baseChance, double watchingBonusAtMax, double multiplier) {
        ItConfig config = ItConfigManager.getConfig();
        double watchingPercent = EventChanceScaler.watchingPercent(player, progressionManager, config);
        double watchingMultiplier = EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config);
        return EventChanceScaler.clampChance((baseChance + watchingBonusAtMax * watchingPercent) * watchingMultiplier * config.eventChanceMultiplier * Math.max(0.0D, multiplier));
    }

    private double eventBalanceMultiplier(double configuredMultiplier) {
        return ItConfigManager.getConfig().enableEventWeightBalancing ? configuredMultiplier : 1.0D;
    }

    private HorrorPhase phaseFromNumber(int phaseNumber) {
        return switch (Math.max(1, Math.min(5, phaseNumber))) {
            case 2 -> HorrorPhase.WATCHING;
            case 3 -> HorrorPhase.IMITATING;
            case 4 -> HorrorPhase.INTRUSION;
            case 5 -> HorrorPhase.MANIFESTATION;
            default -> HorrorPhase.DORMANT;
        };
    }

    private String pick(String... values) {
        return values[random.nextInt(values.length)];
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private enum SoundSequenceType {
        FOOTSTEPS,
        MINING_ECHO,
        CONTAINER_CLOSE,
        FAMILIAR_BLOCK_PLACE,
        FAMILIAR_EATING
    }

    private enum EerieSoundType {
        LAUGH,
        CAVE_AMBIENT,
        WHISPER_STATIC,
        BEHIND_PLAYER
    }

    private static class SoundSequence {
        private final SoundSequenceType type;
        private final UUID playerUuid;
        private final double angle;
        private final double miningStepAngle;
        private final double distance;
        private final double yOffset;
        private final int totalSounds;
        private final int minDelayTicks;
        private final int maxDelayTicks;
        private final int totalMiningBlocks;
        private final SoundEvent miningHitSound;
        private final SoundEvent miningBreakSound;
        private final BlockPos blockPos;
        private final RegistryEntry<SoundEvent> singleSound;

        private long nextSoundTick;
        private int playedSounds;
        private int minedBlocks;
        private int miningHitsThisBlock;
        private int miningHitsNeededForBlock;

        private static SoundSequence create(SoundSequenceType type, UUID playerUuid, long firstSoundTick, double angle, double distance, double yOffset, int totalSounds, int minDelayTicks, int maxDelayTicks) {
            return new SoundSequence(type, playerUuid, firstSoundTick, angle, angle, distance, yOffset, totalSounds, minDelayTicks, maxDelayTicks, 0, 0, null, null, null, null);
        }

        private static SoundSequence createMiningEcho(UUID playerUuid, long firstSoundTick, double angle, double miningStepAngle, double distance, double yOffset, int totalMiningBlocks, int firstBlockHits, SoundEvent hitSound, SoundEvent breakSound) {
            return new SoundSequence(SoundSequenceType.MINING_ECHO, playerUuid, firstSoundTick, angle, miningStepAngle, distance, yOffset, 0, 0, 0, totalMiningBlocks, firstBlockHits, hitSound, breakSound, null, null);
        }

        private static SoundSequence createContainerClose(UUID playerUuid, long firstSoundTick, BlockPos blockPos, SoundEvent sound) {
            return new SoundSequence(SoundSequenceType.CONTAINER_CLOSE, playerUuid, firstSoundTick, 0.0D, 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0, null, null, blockPos.toImmutable(), Registries.SOUND_EVENT.getEntry(sound));
        }

        private static SoundSequence createRepeatedSound(SoundSequenceType type, UUID playerUuid, long firstSoundTick, double angle, double distance, double yOffset, int totalSounds, int minDelayTicks, int maxDelayTicks, SoundEvent sound) {
            return createRepeatedSound(type, playerUuid, firstSoundTick, angle, distance, yOffset, totalSounds, minDelayTicks, maxDelayTicks, Registries.SOUND_EVENT.getEntry(sound));
        }

        private static SoundSequence createRepeatedSound(SoundSequenceType type, UUID playerUuid, long firstSoundTick, double angle, double distance, double yOffset, int totalSounds, int minDelayTicks, int maxDelayTicks, RegistryEntry<SoundEvent> sound) {
            return new SoundSequence(type, playerUuid, firstSoundTick, angle, angle, distance, yOffset, totalSounds, minDelayTicks, maxDelayTicks, 0, 0, null, null, null, sound);
        }

        private SoundSequence(SoundSequenceType type, UUID playerUuid, long firstSoundTick, double angle, double miningStepAngle, double distance, double yOffset, int totalSounds, int minDelayTicks, int maxDelayTicks, int totalMiningBlocks, int miningHitsNeededForBlock, SoundEvent miningHitSound, SoundEvent miningBreakSound, BlockPos blockPos, RegistryEntry<SoundEvent> singleSound) {
            this.type = type;
            this.playerUuid = playerUuid;
            this.nextSoundTick = firstSoundTick;
            this.angle = angle;
            this.miningStepAngle = miningStepAngle;
            this.distance = distance;
            this.yOffset = yOffset;
            this.totalSounds = totalSounds;
            this.minDelayTicks = minDelayTicks;
            this.maxDelayTicks = maxDelayTicks;
            this.totalMiningBlocks = totalMiningBlocks;
            this.miningHitsNeededForBlock = miningHitsNeededForBlock;
            this.miningHitSound = miningHitSound;
            this.miningBreakSound = miningBreakSound;
            this.blockPos = blockPos;
            this.singleSound = singleSound;
        }

        private SoundPosition getPositionNear(ServerPlayerEntity player) {
            double sideAngle = angle + Math.PI / 2.0;
            double smallMovement = playedSounds * 0.72;
            double sideDrift = Math.sin(playedSounds * 1.4) * 0.75;

            double x = player.getX() + Math.cos(angle) * (distance - smallMovement) + Math.cos(sideAngle) * sideDrift;
            double y = player.getY() + yOffset;
            double z = player.getZ() + Math.sin(angle) * (distance - smallMovement) + Math.sin(sideAngle) * sideDrift;

            return new SoundPosition(x, y, z);
        }

        private SoundPosition getMiningBlockPosition(ServerPlayerEntity player) {
            double blockStep = minedBlocks * 1.0;
            double smallJitter = Math.sin(miningHitsThisBlock * 1.7) * 0.08;

            double x = player.getX() + Math.cos(angle) * distance + Math.cos(miningStepAngle) * blockStep + Math.cos(angle + Math.PI / 2.0) * smallJitter;
            double y = player.getY() + yOffset;
            double z = player.getZ() + Math.sin(angle) * distance + Math.sin(miningStepAngle) * blockStep + Math.sin(angle + Math.PI / 2.0) * smallJitter;

            return new SoundPosition(x, y, z);
        }

        private void startNextMiningBlock(int hitsNeeded) {
            this.miningHitsThisBlock = 0;
            this.miningHitsNeededForBlock = hitsNeeded;
        }
    }

    private record SoundPosition(double x, double y, double z) {
    }

    private record MiningSoundSet(SoundEvent hitSound, SoundEvent breakSound) {
    }
}
