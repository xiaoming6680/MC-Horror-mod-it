package com.xm6680.it.manifestation;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.event.EventChanceScaler;
import com.xm6680.it.event.MultiplayerDreadManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ManifestationManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_JUMPSCARE_SEPARATION_TICKS = 30 * TICKS_PER_SECOND;
    private static final int FAKE_VISITOR_MIN_LEAVE_DELAY_TICKS = 200;

    private final HorrorProgressionManager progressionManager;
    private final AnalogHorrorManager analogHorrorManager;
    private final Map<UUID, Long> nextInterferenceTicks = new HashMap<>();
    private final Map<UUID, Long> lastInterferenceTicks = new HashMap<>();
    private final Map<UUID, Long> nextSleepInterferenceTicks = new HashMap<>();
    private final Map<UUID, Long> blockedSleepNightKeys = new HashMap<>();
    private Long globalBlockedSleepNightKey;
    private final Map<UUID, Long> nextFaceScareTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeVisitorTicks = new HashMap<>();
    private final List<ActiveFakeVisitor> activeFakeVisitors = new ArrayList<>();
    private final Random random = new Random();
    private ChaseManager chaseManager;
    private CaveStalkerManager caveStalkerManager;
    private MultiplayerDreadManager multiplayerDreadManager;

    public ManifestationManager(HorrorProgressionManager progressionManager, AnalogHorrorManager analogHorrorManager) {
        this.progressionManager = progressionManager;
        this.analogHorrorManager = analogHorrorManager;
    }

    public void setChaseManager(ChaseManager chaseManager) {
        this.chaseManager = chaseManager;
    }

    public void setCaveStalkerManager(CaveStalkerManager caveStalkerManager) {
        this.caveStalkerManager = caveStalkerManager;
    }

    public void setMultiplayerDreadManager(MultiplayerDreadManager multiplayerDreadManager) {
        this.multiplayerDreadManager = multiplayerDreadManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        tickFakeVisitors(server, currentTick);

        ItConfig config = ItConfigManager.getConfig();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                tryNaturalFakePlayerVisit(player, currentTick, config);
            }
        }

        if (!config.enablePhaseFiveInterference) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                tryInterference(player, currentTick);
                tryFaceScare(player, currentTick);
            }
        }
    }

    public boolean triggerInterference(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && (!config.enablePhaseFiveInterference || progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION)) {
            return false;
        }

        if (!forced && !progressionManager.isManifestationEnvironment(player)) {
            return false;
        }

        UUID uuid = player.getUuid();
        if (!forced && currentTick < nextInterferenceTicks.getOrDefault(uuid, 0L)) {
            return false;
        }

        int minimumDuration = config.reduceFlashingEffects || config.disableRapidFlashes ? 100 : 140;
        int duration = Math.max(minimumDuration, config.phaseFiveFlickerDurationTicks);
        float intensity = (float) Math.max(0.0, Math.min(1.0, config.phaseFiveFlickerIntensity));
        ItNetwork.sendManifestationOverlay(player, duration, intensity, config.reduceFlashingEffects || config.disableRapidFlashes);

        if (config.enablePhaseFiveNoise) {
            playInterferenceSoundBurst(player);
        }

        analogHorrorManager.addManifestationMessage(player, random.nextBoolean()
                ? "检测到信号干扰。"
                : "接收器信号源：内部。");

        progressionManager.recordPhaseFiveInterference(player);
        progressionManager.getData(player).hasTriggeredManifestation = true;
        lastInterferenceTicks.put(uuid, currentTick);
        nextInterferenceTicks.put(uuid, currentTick + secondsToTicks(config.phaseFiveInterferenceCooldownSeconds + randomBetween(0, 240)));

        if (!forced && config.enablePhaseFiveFakeJoinMessages && random.nextDouble() < 0.28) {
            startFakePlayerVisit(player, currentTick, false);
        }

        return true;
    }

    public boolean shouldBlockSleep(ServerPlayerEntity player, long currentTick) {
        Long blockedNightKey = blockedSleepNightKeys.get(player.getUuid());
        long currentNightKey = sleepNightKey(player);
        if (!isNight(player.getEntityWorld())) {
            if (blockedNightKey != null && blockedNightKey <= currentNightKey) {
                blockedSleepNightKeys.remove(player.getUuid());
            }
            if (globalBlockedSleepNightKey != null && globalBlockedSleepNightKey <= currentNightKey) {
                globalBlockedSleepNightKey = null;
            }
            return false;
        }

        if (isSleepBlockedForNight(blockedNightKey, currentNightKey)) {
            sendSleepBlockedMessage(player);
            return true;
        }

        ItConfig config = ItConfigManager.getConfig();
        if (!config.enablePhaseFiveSleepInterference || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.IMITATING)) {
            return false;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextSleepInterferenceTicks.getOrDefault(uuid, 0L)) {
            return false;
        }

        nextSleepInterferenceTicks.put(uuid, currentTick + secondsToTicks(300 + randomBetween(0, 300)));
        double chance = Math.min(0.75D, EventChanceScaler.clampChance(0.45D
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config)));
        if (random.nextDouble() > chance) {
            return false;
        }

        blockSleepForNight(player);
        sendSleepBlockedMessage(player);
        analogHorrorManager.addManifestationMessage(player, "睡眠请求已拒绝。它离你很近了。");
        return true;
    }

    public boolean triggerFakePlayerVisit(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        HorrorPhase phase = progressionManager.getPhase(player);
        if (!forced && (!config.enablePhaseFiveFakeJoinMessages || !phase.isAtLeast(HorrorPhase.INTRUSION))) {
            return false;
        }

        if (!forced && phase == HorrorPhase.MANIFESTATION && !progressionManager.isManifestationEnvironment(player)) {
            return false;
        }

        startFakePlayerVisit(player, currentTick, false);
        return true;
    }

    public boolean triggerFakePlayerChaseVisit(ServerPlayerEntity player, long currentTick, boolean forced) {
        if ((chaseManager == null || chaseManager.isChasing(player))
                && (caveStalkerManager == null || caveStalkerManager.isActive(player))) {
            return false;
        }

        ItConfig config = ItConfigManager.getConfig();
        if (!forced && (!config.enablePhaseFiveFakeJoinMessages
                || progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION
                || !progressionManager.isManifestationEnvironment(player))) {
            return false;
        }

        startFakePlayerVisit(player, currentTick, true);
        return true;
    }

    public boolean triggerFaceScare(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && (!config.enableFaceScare || progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION)) {
            return false;
        }

        if (!forced && !progressionManager.isManifestationEnvironment(player)) {
            return false;
        }

        UUID uuid = player.getUuid();
        if (!forced && currentTick < nextFaceScareTicks.getOrDefault(uuid, 0L)) {
            return false;
        }

        int duration = Math.max(35, config.faceScareDurationTicks);
        ItNetwork.sendFaceScare(player, duration, 1.0F, true);
        analogHorrorManager.addManifestationMessage(player, "画面接触中断。");
        nextFaceScareTicks.put(uuid, currentTick + secondsToTicks(config.faceScareCooldownSeconds + randomBetween(0, 240)));
        return true;
    }

    public boolean triggerSleepInterference(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && (!config.enablePhaseFiveSleepInterference || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.IMITATING))) {
            return false;
        }

        blockSleepForNight(player);
        sendSleepBlockedMessage(player);
        analogHorrorManager.addManifestationMessage(player, "睡眠请求已拒绝。它离你很近了。");
        return true;
    }

    public boolean isSeparatedFromInterference(ServerPlayerEntity player, long currentTick) {
        return currentTick - lastInterferenceTicks.getOrDefault(player.getUuid(), (long) -MIN_JUMPSCARE_SEPARATION_TICKS) >= MIN_JUMPSCARE_SEPARATION_TICKS;
    }

    public long getNextInterferenceTick(ServerPlayerEntity player) {
        return nextInterferenceTicks.getOrDefault(player.getUuid(), 0L);
    }

    private void tryInterference(ServerPlayerEntity player, long currentTick) {
        PlayerHorrorData data = progressionManager.getData(player);
        if (data.currentPhase != HorrorPhase.MANIFESTATION || !progressionManager.isManifestationEnvironment(player)) {
            return;
        }

        if (currentTick < nextInterferenceTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        double chance = EventChanceScaler.clampChance(0.035
                * config.eventChanceMultiplier
                * EventChanceScaler.phaseFiveHighPressureEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerInterference(player, currentTick, false);
        }
    }

    private void tryFaceScare(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFaceScare || progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION) {
            return;
        }

        if (!progressionManager.isManifestationEnvironment(player)) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextFaceScareTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        nextFaceScareTicks.put(uuid, currentTick + secondsToTicks(randomBetween(45, 120)));
        double chance = EventChanceScaler.clampChance(0.08
                * config.faceScareChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.phaseFiveHighPressureEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            return;
        }

        int duration = Math.max(35, config.faceScareDurationTicks);
        ItNetwork.sendFaceScare(player, duration, 1.0F, true);
        analogHorrorManager.addManifestationMessage(player, "画面接触中断。");
        nextFaceScareTicks.put(uuid, currentTick + secondsToTicks(config.faceScareCooldownSeconds + randomBetween(0, 240)));
    }

    private void tryNaturalFakePlayerVisit(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enablePhaseFiveFakeJoinMessages) {
            return;
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        if (!phase.isAtLeast(HorrorPhase.INTRUSION)) {
            return;
        }

        UUID uuid = player.getUuid();
        if (currentTick < nextFakeVisitorTicks.getOrDefault(uuid, 0L) || hasActiveFakeVisitor(uuid)) {
            return;
        }

        if (phase == HorrorPhase.MANIFESTATION && !progressionManager.isManifestationEnvironment(player)) {
            nextFakeVisitorTicks.put(uuid, currentTick + secondsToTicks(randomBetween(160, 280)));
            return;
        }

        int minCooldown = phase == HorrorPhase.INTRUSION ? 520 : 380;
        int maxCooldown = phase == HorrorPhase.INTRUSION ? 920 : 760;
        nextFakeVisitorTicks.put(uuid, currentTick + secondsToTicks(randomBetween(minCooldown, maxCooldown)));
        double watchingMultiplier = phase == HorrorPhase.MANIFESTATION
                ? EventChanceScaler.phaseFiveHighPressureEventMultiplier(player, progressionManager, config)
                : EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config);
        double chance = EventChanceScaler.clampChance((phase == HorrorPhase.INTRUSION ? 0.075 : 0.11)
                * config.eventChanceMultiplier
                * watchingMultiplier);
        if (random.nextDouble() > chance) {
            return;
        }

        startFakePlayerVisit(player, currentTick, false);
    }

    private void playInterferenceSoundBurst(ServerPlayerEntity player) {
        float volume = (float) Math.min(1.0, Math.max(0.0, ItConfigManager.getConfig().phaseFiveNoiseVolume));
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.AMBIENT, volume * 0.70F, 0.50F + random.nextFloat() * 0.12F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, volume * 0.95F, 0.55F + random.nextFloat() * 0.10F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.AMBIENT, volume * 0.42F, 0.36F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.AMBIENT, volume * 0.72F, 0.50F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.AMBIENT, volume * 0.55F, 0.38F + random.nextFloat() * 0.18F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.AMBIENT, volume * 0.36F, 0.28F + random.nextFloat() * 0.15F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.AMBIENT, volume * 0.45F, 0.45F + random.nextFloat() * 0.20F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, volume * 0.50F, 0.70F + random.nextFloat() * 0.40F);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, volume * 0.50F, 0.65F);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, player.getX(), player.getY(), player.getZ(), volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private void startFakePlayerVisit(ServerPlayerEntity target, long currentTick, boolean forceChaseLink) {
        ServerWorld world = target.getEntityWorld();
        String name = randomFakePlayerName();
        FakePlayer fakePlayer = FakePlayer.get(world, new GameProfile(UUID.randomUUID(), name));
        Vec3d side = target.getRotationVec(1.0F).crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
        if (side.lengthSquared() < 0.01) {
            side = new Vec3d(1.0, 0.0, 0.0);
        }

        Vec3d pos = new Vec3d(target.getX(), target.getY(), target.getZ()).add(side.multiply(2.5)).add(0.0, 0.1, 0.0);
        fakePlayer.refreshPositionAndAngles(pos.x, pos.y, pos.z, target.getYaw() + 180.0F, 0.0F);
        TargetOnlyEntityVisibility.registerExternalTarget(fakePlayer, target);
        world.spawnEntity(fakePlayer);
        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(fakePlayer);
        boolean chaseLinked = forceChaseLink || shouldLinkFakePlayerToChase(target);
        List<ScheduledFakeLine> lines = chaseLinked ? chaseFakePlayerLines(target, currentTick) : randomFakePlayerLines(target, currentTick);
        long lastMessageTick = lines.get(lines.size() - 1).tick();
        long chaseTick = chaseLinked ? lastMessageTick + randomBetween(35, 70) : -1L;
        long leaveTick = Math.max(currentTick + randomBetween(340, 500), (chaseLinked ? chaseTick : lastMessageTick) + randomBetween(220, 340));

        activeFakeVisitors.add(new ActiveFakeVisitor(
                target.getUuid(),
                fakePlayer,
                lines,
                leaveTick,
                name,
                chaseLinked,
                chaseTick
        ));

        target.sendMessage(Text.empty()
                .append(Text.literal(name).formatted(Formatting.YELLOW, Formatting.OBFUSCATED))
                .append(Text.literal(" 加入了游戏").formatted(Formatting.YELLOW)), false);

        if (multiplayerDreadManager != null && ItConfigManager.getConfig().fakeTabListIntegrateWithFakeJoin) {
            int tabDurationTicks = (int) Math.max(ItConfigManager.getConfig().fakeTabListDurationTicks, leaveTick - currentTick + 20L);
            multiplayerDreadManager.showFakeTabEntry(target, name, tabDurationTicks, true);
        }
    }

    private void tickFakeVisitors(MinecraftServer server, long currentTick) {
        Iterator<ActiveFakeVisitor> iterator = activeFakeVisitors.iterator();
        while (iterator.hasNext()) {
            ActiveFakeVisitor visitor = iterator.next();
            TargetOnlyEntityVisibility.hideFromNonTargetPlayers(visitor.fakePlayer);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(visitor.targetUuid);
            if (target != null) {
                if (visitor.nextMessageIndex < visitor.messages.size()
                        && currentTick >= visitor.messages.get(visitor.nextMessageIndex).tick()) {
                    ScheduledFakeLine line = visitor.messages.get(visitor.nextMessageIndex++);
                    target.sendMessage(Text.empty()
                            .append(Text.literal("<").formatted(Formatting.WHITE))
                            .append(Text.literal(visitor.name).formatted(Formatting.WHITE, Formatting.OBFUSCATED))
                            .append(Text.literal("> " + line.text()).formatted(Formatting.WHITE)), false);
                    visitor.lastSpokenTick = currentTick;
                }

                if (visitor.chaseLinked && !visitor.chaseTriggered && currentTick >= visitor.chaseTick) {
                    visitor.chaseTriggered = true;
                    if (caveStalkerManager != null
                            && CaveStalkerManager.isCaveLikeEnvironment(target)
                            && !caveStalkerManager.isActive(target)) {
                        caveStalkerManager.triggerCaveStalker(target, currentTick, true);
                    } else if (chaseManager != null && !chaseManager.isChasing(target)) {
                        chaseManager.triggerChase(target, currentTick, true);
                    }
                }
            }

            if (target != null) {
                if (visitor.nextMessageIndex < visitor.messages.size()) {
                    continue;
                }

                if (visitor.lastSpokenTick >= 0L
                        && currentTick < visitor.lastSpokenTick + FAKE_VISITOR_MIN_LEAVE_DELAY_TICKS) {
                    continue;
                }
            }

            if (currentTick < visitor.leaveTick) {
                continue;
            }

            if (target != null) {
                target.sendMessage(Text.empty()
                        .append(Text.literal(visitor.name).formatted(Formatting.YELLOW, Formatting.OBFUSCATED))
                        .append(Text.literal(" 离开了游戏").formatted(Formatting.YELLOW)), false);
            }

            TargetOnlyEntityVisibility.unregisterExternalTarget(visitor.fakePlayer);
            visitor.fakePlayer.discard();
            iterator.remove();
        }
    }

    private boolean hasActiveFakeVisitor(UUID targetUuid) {
        for (ActiveFakeVisitor visitor : activeFakeVisitors) {
            if (visitor.targetUuid.equals(targetUuid)) {
                return true;
            }
        }

        return false;
    }

    private String randomFakePlayerName() {
        String[] fragments = {"ERR", "NULL", "0x", "NaN", "II1", "O0"};
        return fragments[random.nextInt(fragments.length)]
                + fragments[random.nextInt(fragments.length)]
                + random.nextInt(10)
                + random.nextInt(10);
    }

    private void blockSleepForNight(ServerPlayerEntity player) {
        long nightKey = sleepNightKey(player);
        if (!isMultiplayer(player)) {
            blockedSleepNightKeys.put(player.getUuid(), nightKey);
            return;
        }

        globalBlockedSleepNightKey = nightKey;
        for (ServerPlayerEntity onlinePlayer : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (!onlinePlayer.isSpectator()) {
                blockedSleepNightKeys.put(onlinePlayer.getUuid(), sleepNightKey(onlinePlayer));
            }
        }
    }

    private boolean isSleepBlockedForNight(Long playerBlockedNightKey, long currentNightKey) {
        return (playerBlockedNightKey != null && playerBlockedNightKey == currentNightKey)
                || (globalBlockedSleepNightKey != null && globalBlockedSleepNightKey == currentNightKey);
    }

    private boolean isMultiplayer(ServerPlayerEntity player) {
        int activePlayers = 0;
        for (ServerPlayerEntity onlinePlayer : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (!onlinePlayer.isSpectator()) {
                activePlayers++;
            }
        }

        return activePlayers > 1;
    }

    private void sendSleepBlockedMessage(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("你现在不能休息。它太近了。今晚它会一直等着。").formatted(Formatting.DARK_GRAY), true);
    }

    private boolean isNight(ServerWorld world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= 13000L && time <= 23000L;
    }

    private long sleepNightKey(ServerPlayerEntity player) {
        return player.getEntityWorld().getTimeOfDay() / 24000L;
    }

    private String randomFakePlayerLine(ServerPlayerEntity target) {
        String playerName = target.getGameProfile().name();
        boolean underground = target.getBlockY() < target.getEntityWorld().getSeaLevel();
        boolean alone = progressionManager.isAlone(target, 48.0);
        int y = target.getBlockY();
        String[] lines = {
                "它找到你了。",
                "别关门。",
                playerName + "，你刚才数错了。",
                "这里不是你的房间。",
                "接收器一直在响。",
                "外面那个不是我。",
                "灯少了一盏。",
                "它已经进来了。",
                "不要回答下面的声音。",
                "你回来得太晚了。",
                underground ? playerName + " 在地表下。" : playerName + " 还在地表上。",
                "Y=" + y + "，已记录。",
                alone ? playerName + " 现在是一个人。" : playerName + " 身边还有别人。",
                "它离 " + playerName + " 很近了。"
        };
        return lines[random.nextInt(lines.length)];
    }

    private boolean shouldLinkFakePlayerToChase(ServerPlayerEntity target) {
        if ((chaseManager == null || chaseManager.isChasing(target))
                && (caveStalkerManager == null || caveStalkerManager.isActive(target))) {
            return false;
        }

        ItConfig config = ItConfigManager.getConfig();
        boolean caveCandidate = caveStalkerManager != null && CaveStalkerManager.isCaveLikeEnvironment(target);
        if (caveCandidate && !config.enableCaveStalker) {
            return false;
        }

        if (!caveCandidate && !config.enableChaseEvents) {
            return false;
        }

        if (progressionManager.getPhase(target) != HorrorPhase.MANIFESTATION) {
            return false;
        }

        return random.nextDouble() < 0.42;
    }

    private List<ScheduledFakeLine> randomFakePlayerLines(ServerPlayerEntity target, long currentTick) {
        int count = random.nextDouble() < 0.45 ? randomBetween(2, 3) : 1;
        List<ScheduledFakeLine> lines = new ArrayList<>();
        long tick = currentTick + randomBetween(70, 120);
        for (int i = 0; i < count; i++) {
            lines.add(new ScheduledFakeLine(tick, randomFakePlayerLine(target)));
            tick += randomBetween(70, 130);
        }

        return lines;
    }

    private List<ScheduledFakeLine> chaseFakePlayerLines(ServerPlayerEntity target, long currentTick) {
        String playerName = target.getGameProfile().name();
        String[] chaseLines = {
                "它开始走了。",
                "你听见的不是自己的脚步。",
                "别把接收器举起来。",
                playerName + "，不要停。",
                "它会等你读完这句话。",
                "下一次信号会从你后面来。"
        };

        List<String> availableLines = new ArrayList<>(List.of(chaseLines));
        int count = Math.min(randomBetween(2, 3), availableLines.size());
        List<ScheduledFakeLine> lines = new ArrayList<>();
        long tick = currentTick + randomBetween(70, 120);
        for (int i = 0; i < count; i++) {
            lines.add(new ScheduledFakeLine(tick, availableLines.remove(random.nextInt(availableLines.size()))));
            tick += randomBetween(75, 135);
        }

        return lines;
    }

    private record ScheduledFakeLine(long tick, String text) {
    }

    private static final class ActiveFakeVisitor {
        private final UUID targetUuid;
        private final FakePlayer fakePlayer;
        private final List<ScheduledFakeLine> messages;
        private final long leaveTick;
        private final String name;
        private final boolean chaseLinked;
        private final long chaseTick;
        private int nextMessageIndex;
        private boolean chaseTriggered;
        private long lastSpokenTick = -1L;

        private ActiveFakeVisitor(UUID targetUuid, FakePlayer fakePlayer, List<ScheduledFakeLine> messages, long leaveTick, String name, boolean chaseLinked, long chaseTick) {
            this.targetUuid = targetUuid;
            this.fakePlayer = fakePlayer;
            this.messages = messages;
            this.leaveTick = leaveTick;
            this.name = name;
            this.chaseLinked = chaseLinked;
            this.chaseTick = chaseTick;
        }
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }
}
