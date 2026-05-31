package com.xm6680.it.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xm6680.it.ItMod;
import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.cavestalker.CaveStalkerState;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.chase.ChaseState;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.director.AvoidanceManager;
import com.xm6680.it.director.GroupDreadManager;
import com.xm6680.it.director.HorrorDirectorManager;
import com.xm6680.it.director.PlayerContextDetector;
import com.xm6680.it.director.PlayerContextSnapshot;
import com.xm6680.it.effect.ClientDistortionManager;
import com.xm6680.it.event.AnimalDisguiseManager;
import com.xm6680.it.event.FamiliarSoundEventType;
import com.xm6680.it.event.HorrorEventManager;
import com.xm6680.it.event.MultiplayerDreadManager;
import com.xm6680.it.event.NetherSignalManager;
import com.xm6680.it.event.WatcherSpawnManager;
import com.xm6680.it.event.WorldAnomalyManager;
import com.xm6680.it.jumpscare.JumpscareManager;
import com.xm6680.it.manifestation.ManifestationManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.runtime.ItManagers;
import com.xm6680.it.watching.HorrorPhase;
import com.xm6680.it.watching.WatchingLevelManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the gated /it test command tree and the hidden /xm debug toggle.
 */
public final class ItCommandRegistration {
    private ItCommandRegistration() {
    }

    public static void register(ItManagers managers) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(buildRoot("it", managers));
            dispatcher.register(buildHiddenRoot());
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildHiddenRoot() {
        return literal("xm")
                .requires(ItCommandRegistration::canUseCommand)
                .then(literal("debug")
                        .then(literal("on")
                                .executes(context -> setTestCommands(context.getSource(), true)))
                        .then(literal("off")
                                .executes(context -> setTestCommands(context.getSource(), false))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildRoot(String root, ItManagers managers) {
        WatchingLevelManager watchingLevelManager = managers.watchingLevelManager();
        HorrorProgressionManager progressionManager = managers.progressionManager();
        ReceiverManager receiverManager = managers.receiverManager();
        AnalogHorrorManager analogHorrorManager = managers.analogHorrorManager();
        HorrorEventManager horrorEventManager = managers.horrorEventManager();
        WatcherSpawnManager watcherSpawnManager = managers.watcherSpawnManager();
        WorldAnomalyManager worldAnomalyManager = managers.worldAnomalyManager();
        MultiplayerDreadManager multiplayerDreadManager = managers.multiplayerDreadManager();
        AnimalDisguiseManager animalDisguiseManager = managers.animalDisguiseManager();
        NetherSignalManager netherSignalManager = managers.netherSignalManager();
        ManifestationManager manifestationManager = managers.manifestationManager();
        JumpscareManager jumpscareManager = managers.jumpscareManager();
        ChaseManager chaseManager = managers.chaseManager();
        CaveStalkerManager caveStalkerManager = managers.caveStalkerManager();
        ClientDistortionManager clientDistortionManager = managers.clientDistortionManager();
        PlayerContextDetector playerContextDetector = managers.playerContextDetector();
        AvoidanceManager avoidanceManager = managers.avoidanceManager();
        GroupDreadManager groupDreadManager = managers.groupDreadManager();
        HorrorDirectorManager horrorDirectorManager = managers.horrorDirectorManager();

        return literal(root)
                .requires(ItCommandRegistration::canUseTestCommand)
                .then(literal("watching")
                        .then(literal("get")
                                .executes(context -> getWatchingLevel(context.getSource(), watchingLevelManager, progressionManager)))
                        .then(literal("set")
                                .then(argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> setWatchingLevel(
                                                context.getSource(),
                                                watchingLevelManager,
                                                progressionManager,
                                                IntegerArgumentType.getInteger(context, "value"))))))
                .then(literal("phase")
                        .then(literal("get")
                                .executes(context -> getPhase(context.getSource(), progressionManager)))
                        .then(literal("debug")
                                .executes(context -> phaseDebug(context.getSource(), progressionManager)))
                        .then(literal("set")
                                .then(argument("value", IntegerArgumentType.integer(1, 5))
                                        .executes(context -> setPhase(
                                                context.getSource(),
                                                progressionManager,
                                                IntegerArgumentType.getInteger(context, "value")))))
                        .then(literal("reset")
                                .executes(context -> resetPhase(context.getSource(), progressionManager, receiverManager))))
                .then(literal("setphase")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("phase", IntegerArgumentType.integer(1, 5))
                                        .executes(context -> setPhaseForPlayer(
                                                context.getSource(),
                                                progressionManager,
                                                EntityArgumentType.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "phase"))))))
                .then(literal("context")
                        .executes(context -> showContext(context.getSource(), progressionManager, receiverManager, playerContextDetector, avoidanceManager, groupDreadManager, horrorDirectorManager, context.getSource().getPlayerOrThrow()))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> showContext(
                                        context.getSource(),
                                        progressionManager,
                                        receiverManager,
                                        playerContextDetector,
                                        avoidanceManager,
                                        groupDreadManager,
                                        horrorDirectorManager,
                                        EntityArgumentType.getPlayer(context, "player")))))
                .then(literal("avoidance")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> showAvoidance(
                                        context.getSource(),
                                        avoidanceManager,
                                        EntityArgumentType.getPlayer(context, "player")))
                                .then(argument("value", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                        .executes(context -> setAvoidance(
                                                context.getSource(),
                                                avoidanceManager,
                                                EntityArgumentType.getPlayer(context, "player"),
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(literal("forceevent")
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("eventType", StringArgumentType.word())
                                        .executes(context -> forceDirectorEvent(
                                                context.getSource(),
                                                horrorDirectorManager,
                                                EntityArgumentType.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "eventType"))))))
                .then(literal("testmode")
                        .then(literal("on")
                                .executes(context -> setTestMode(context.getSource(), true)))
                        .then(literal("off")
                                .executes(context -> setTestMode(context.getSource(), false))))
                .then(literal("event")
                        .then(literal("fakechat")
                                .executes(context -> triggerFakeChat(context.getSource(), horrorEventManager)))
                        .then(literal("cavesound")
                                .executes(context -> triggerCaveSound(context.getSource(), horrorEventManager)))
                        .then(literal("mining")
                                .executes(context -> triggerMiningEcho(context.getSource(), horrorEventManager)))
                        .then(literal("handdrop")
                                .executes(context -> triggerHandDrop(context.getSource(), horrorEventManager)))
                        .then(literal("inventoryopen")
                                .executes(context -> triggerInventoryOpen(context.getSource(), horrorEventManager)))
                        .then(literal("forcedchat")
                                .executes(context -> triggerForcedChat(context.getSource(), horrorEventManager)))
                        .then(literal("eeriesound")
                                .executes(context -> triggerEerieSound(context.getSource(), horrorEventManager))
                                .then(literal("laugh")
                                        .executes(context -> triggerEerieSoundLaugh(context.getSource(), horrorEventManager))))
                        .then(literal("familiarsound")
                                .executes(context -> triggerFamiliarSound(context.getSource(), horrorEventManager, null))
                                .then(literal("chest")
                                        .executes(context -> triggerFamiliarSound(context.getSource(), horrorEventManager, FamiliarSoundEventType.CHEST)))
                                .then(literal("place")
                                        .executes(context -> triggerFamiliarSound(context.getSource(), horrorEventManager, FamiliarSoundEventType.BLOCK_PLACE)))
                                .then(literal("eat")
                                        .executes(context -> triggerFamiliarSound(context.getSource(), horrorEventManager, FamiliarSoundEventType.EATING))))
                        .then(literal("animalstare")
                                .executes(context -> triggerAnimalStare(context.getSource(), worldAnomalyManager)))
                        .then(literal("separation")
                                .executes(context -> triggerSeparation(context.getSource(), multiplayerDreadManager)))
                        .then(literal("teammatefootsteps")
                                .executes(context -> triggerTeammateFootsteps(context.getSource(), multiplayerDreadManager)))
                        .then(literal("teamgaze")
                                .executes(context -> triggerTeamGaze(context.getSource(), multiplayerDreadManager)))
                        .then(literal("faketab")
                                .executes(context -> triggerFakeTab(context.getSource(), multiplayerDreadManager)))
                        .then(literal("fakeadvancement")
                                .executes(context -> triggerFakeAdvancement(context.getSource(), multiplayerDreadManager)))
                        .then(literal("mimicplayer")
                                .executes(context -> triggerMimicPlayer(context.getSource(), multiplayerDreadManager))
                                .then(literal("clear")
                                        .executes(context -> clearMimicPlayers(context.getSource(), multiplayerDreadManager))))
                        .then(literal("fakerescue")
                                .executes(context -> triggerFakeRescue(context.getSource(), multiplayerDreadManager, null))
                                .then(literal("helpful")
                                        .executes(context -> triggerFakeRescue(context.getSource(), multiplayerDreadManager, true)))
                                .then(literal("misleading")
                                        .executes(context -> triggerFakeRescue(context.getSource(), multiplayerDreadManager, false))))
                        .then(literal("animaldisguise")
                                .executes(context -> triggerAnimalDisguise(context.getSource(), animalDisguiseManager)))
                        .then(literal("receiver")
                                .executes(context -> triggerReceiver(context.getSource(), analogHorrorManager))
                                .then(literal("weather")
                                        .executes(context -> triggerReceiverWeather(context.getSource(), analogHorrorManager))))
                        .then(literal("nether")
                                .then(literal("signal")
                                        .executes(context -> triggerNetherSignal(context.getSource(), netherSignalManager)))
                                .then(literal("ghastcry")
                                        .executes(context -> triggerNetherGhastCry(context.getSource(), netherSignalManager)))
                                .then(literal("soulsand")
                                        .executes(context -> triggerNetherSoulSand(context.getSource(), netherSignalManager)))
                                .then(literal("portal")
                                        .executes(context -> triggerNetherPortal(context.getSource(), netherSignalManager))))
                        .then(literal("interference")
                                .executes(context -> triggerInterference(context.getSource(), manifestationManager)))
                        .then(literal("fakejoin")
                                .executes(context -> triggerFakeJoin(context.getSource(), manifestationManager))
                                .then(literal("chase")
                                        .executes(context -> triggerFakeJoinChase(context.getSource(), manifestationManager))))
                        .then(literal("sleepblock")
                                .executes(context -> triggerSleepBlock(context.getSource(), manifestationManager)))
                        .then(literal("anomaly")
                                .then(literal("animalstare")
                                        .executes(context -> triggerAnimalStareAlias(context.getSource(), worldAnomalyManager)))
                                .then(literal("door")
                                        .executes(context -> triggerDoorAnomaly(context.getSource(), worldAnomalyManager)))
                                .then(literal("remove")
                                        .executes(context -> triggerRemoveAnomaly(context.getSource(), worldAnomalyManager)))
                                .then(literal("flower")
                                        .executes(context -> triggerFlowerAnomaly(context.getSource(), worldAnomalyManager)))
                                .then(literal("sign")
                                        .executes(context -> triggerSignAnomaly(context.getSource(), worldAnomalyManager)))
                                .then(literal("cross")
                                        .executes(context -> triggerCrossAnomaly(context.getSource(), worldAnomalyManager)))
                                .then(literal("base")
                                        .executes(context -> triggerBaseAnomaly(context.getSource(), worldAnomalyManager))))
                        .then(literal("jumpscare")
                                .executes(context -> triggerJumpscare(context.getSource(), jumpscareManager)))
                        .then(literal("facescare")
                                .executes(context -> triggerFaceScare(context.getSource())))
                        .then(literal("huntfacescare")
                                .executes(context -> triggerHuntFaceScare(context.getSource())))
                        .then(literal("viewdistance")
                                .executes(context -> triggerViewDistanceAnomaly(context.getSource(), clientDistortionManager)))
                        .then(literal("legacytexture")
                                .executes(context -> triggerLegacyTextureAnomaly(context.getSource(), clientDistortionManager)))
                        .then(literal("monochrome")
                                .executes(context -> triggerMonochromeAnomaly(context.getSource(), clientDistortionManager)))
                        .then(literal("chase")
                                .executes(context -> triggerChase(context.getSource(), chaseManager)))
                        .then(literal("cavestalker")
                                .executes(context -> triggerCaveStalker(context.getSource(), caveStalkerManager)))
                        .then(literal("tunnelwatcher")
                                .executes(context -> triggerTunnelWatcher(context.getSource(), watcherSpawnManager))))
                .then(literal("receiver")
                        .then(literal("give")
                                .executes(context -> giveReceiver(context.getSource(), receiverManager)))
                        .then(literal("status")
                                .executes(context -> receiverStatus(context.getSource(), receiverManager, progressionManager)))
                        .then(literal("clear")
                                .executes(context -> clearReceiver(context.getSource(), receiverManager))))
                .then(literal("chase")
                        .then(literal("start")
                                .executes(context -> triggerChase(context.getSource(), chaseManager)))
                        .then(literal("stop")
                                .executes(context -> stopChase(context.getSource(), chaseManager)))
                        .then(literal("status")
                                .executes(context -> chaseStatus(context.getSource(), chaseManager)))
                        .then(literal("cooldown")
                                .then(literal("reset")
                                        .executes(context -> resetChaseCooldown(context.getSource(), chaseManager)))))
                .then(literal("cavestalker")
                        .then(literal("start")
                                .executes(context -> triggerCaveStalker(context.getSource(), caveStalkerManager)))
                        .then(literal("stop")
                                .executes(context -> stopCaveStalker(context.getSource(), caveStalkerManager)))
                        .then(literal("status")
                                .executes(context -> caveStalkerStatus(context.getSource(), caveStalkerManager)))
                        .then(literal("cooldown")
                                .then(literal("reset")
                                        .executes(context -> resetCaveStalkerCooldown(context.getSource(), caveStalkerManager)))))
                .then(literal("spawnwatcher")
                        .executes(context -> spawnWatcher(context.getSource(), watcherSpawnManager)))
                .then(literal("watcher")
                        .then(literal("debug")
                                .executes(context -> watcherDebug(context.getSource(), watcherSpawnManager)))
                        .then(literal("clear")
                                .executes(context -> clearWatchers(context.getSource(), watcherSpawnManager))))
                .then(literal("animaldisguise")
                        .then(literal("spawnaggressive")
                                .executes(context -> triggerAnimalDisguiseAggressive(context.getSource(), animalDisguiseManager)))
                        .then(literal("clear")
                                .executes(context -> clearAnimalDisguises(context.getSource(), animalDisguiseManager)))
                        .then(literal("status")
                                .executes(context -> animalDisguiseStatus(context.getSource(), animalDisguiseManager))))
                .then(literal("debug")
                        .executes(context -> debug(context.getSource(), watchingLevelManager, progressionManager, receiverManager, manifestationManager, jumpscareManager, chaseManager, caveStalkerManager, animalDisguiseManager))
                        .then(literal("player")
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(context -> debugPlayer(
                                                context.getSource(),
                                                progressionManager,
                                                receiverManager,
                                                playerContextDetector,
                                                avoidanceManager,
                                                groupDreadManager,
                                                horrorDirectorManager,
                                                EntityArgumentType.getPlayer(context, "player")))))
                        .then(literal("reset")
                                .executes(context -> resetDebugData(context.getSource(), progressionManager, receiverManager, chaseManager, caveStalkerManager, multiplayerDreadManager, animalDisguiseManager))))
                .then(literal("config")
                        .then(literal("get")
                                .executes(context -> showConfig(context.getSource())))
                        .then(literal("set")
                                .then(argument("key", StringArgumentType.word())
                                        .then(argument("value", StringArgumentType.greedyString())
                                                .executes(context -> setConfigValue(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "key"),
                                                        StringArgumentType.getString(context, "value"))))))
                        .then(literal("watcher")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setWatcher(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(literal("cavesounds")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setCaveSounds(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(literal("miningecho")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setMiningEcho(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(literal("fakechat")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setFakeChat(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(literal("fakechatnames")
                                .then(literal("enable")
                                        .executes(context -> setFakeChatNames(context.getSource(), true)))
                                .then(literal("disable")
                                        .executes(context -> setFakeChatNames(context.getSource(), false)))
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setFakeChatNames(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(literal("debug")
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setDebugMode(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")))))
                        .then(argument("key", StringArgumentType.word())
                                .then(argument("value", StringArgumentType.greedyString())
                                        .executes(context -> setConfigValue(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "key"),
                                                StringArgumentType.getString(context, "value"))))));
    }

    private static boolean canUseCommand(ServerCommandSource source) {
        if (source.getPermissions() instanceof LeveledPermissionPredicate permissionPredicate) {
            return permissionPredicate.getLevel().isAtLeast(PermissionLevel.GAMEMASTERS);
        }

        return false;
    }

    private static boolean canUseTestCommand(ServerCommandSource source) {
        return canUseCommand(source) && ItConfigManager.getConfig().enableTestCommands;
    }

    private static int setTestCommands(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().enableTestCommands = enabled;
        ItConfigManager.save();
        refreshCommandTrees(source);
        source.sendFeedback(() -> Text.literal(enabled
                ? "It 测试指令已开启。"
                : "It 测试指令已关闭。"), true);
        return enabled ? 1 : 0;
    }

    private static void refreshCommandTrees(ServerCommandSource source) {
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            source.getServer().getPlayerManager().sendCommandTree(player);
        }
    }

    private static int getWatchingLevel(ServerCommandSource source, WatchingLevelManager watchingLevelManager, HorrorProgressionManager progressionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        progressionManager.syncWatchingLevel(player);
        PlayerHorrorData data = progressionManager.getData(player);
        int level = (int) Math.round(watchingLevelManager.getWatchingLevel(player));
        source.sendFeedback(() -> Text.literal(
                "被注视值：" + level
                        + "\n阶段：" + formatPhase(data.currentPhase)
                        + "\n接收器信息：" + data.receiverMessagesReceived
                        + "\nWatcher 目击：" + data.watcherSightings
                        + "\n当前阶段停留：" + formatTicks(data.getTimeInPhaseTicks(progressionManager.getProgressionTick(player)))
        ), false);
        return level;
    }

    private static int setWatchingLevel(ServerCommandSource source, WatchingLevelManager watchingLevelManager, HorrorProgressionManager progressionManager, int value) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        watchingLevelManager.setWatchingLevel(player, value);
        progressionManager.syncWatchingLevel(player);
        int level = (int) Math.round(watchingLevelManager.getWatchingLevel(player));
        source.sendFeedback(() -> Text.literal("被注视值已设置为 " + level), true);
        return level;
    }

    private static int getPhase(ServerCommandSource source, HorrorProgressionManager progressionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerHorrorData data = progressionManager.getData(player);
        source.sendFeedback(() -> Text.literal("阶段：" + formatPhase(data.currentPhase)
                + "\n当前阶段停留：" + formatTicks(data.getTimeInPhaseTicks(progressionManager.getProgressionTick(player)))
                + "\n接收器信息：" + data.receiverMessagesReceived
                + "\n接收器打开次数：" + data.receiverOpenedCount
                + "\nWatcher 目击：" + data.watcherSightings), false);
        return data.currentPhase.getNumber();
    }

    private static int phaseDebug(ServerCommandSource source, HorrorProgressionManager progressionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        HorrorProgressionManager.PhaseDebugInfo info = progressionManager.getPhaseDebugInfo(player);
        sendDebugLine(source, "阶段推进诊断");
        sendDebugLine(source, debugEntry("当前阶段", "currentPhase", formatPhase(info.currentPhase())));
        sendDebugLine(source, debugEntry("目标阶段", "nextPhase", info.nextPhase() == null ? "无" : formatPhase(info.nextPhase())));
        sendDebugLine(source, debugEntry("阶段系统启用", "enablePhaseSystem", formatBoolean(info.phaseSystemEnabled())));
        sendDebugLine(source, debugEntry("阶段门槛启用", "enableProgressionGates", formatBoolean(info.progressionGatesEnabled())));
        sendDebugLine(source, debugEntry("能否进入下一阶段", "canAdvanceNextPhase", formatBoolean(info.canAdvance())));
        for (HorrorProgressionManager.PhaseRequirement requirement : info.requirements()) {
            String prefix = requirement.satisfied() ? "[达标] " : "[未达标] ";
            sendDebugLine(source, prefix + requirement.label() + "：当前 " + requirement.current() + "，需要 " + requirement.required());
        }

        return info.canAdvance() ? 1 : 0;
    }

    private static int setPhase(ServerCommandSource source, HorrorProgressionManager progressionManager, int value) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        HorrorPhase phase = HorrorPhase.fromNumber(value);
        progressionManager.setPhase(player, phase, progressionManager.getProgressionTick(player));
        source.sendFeedback(() -> Text.literal("阶段已设置为 " + formatPhase(phase)), true);
        return phase.getNumber();
    }

    private static int setPhaseForPlayer(ServerCommandSource source, HorrorProgressionManager progressionManager, ServerPlayerEntity player, int value) {
        HorrorPhase phase = HorrorPhase.fromNumber(value);
        progressionManager.setPhase(player, phase, progressionManager.getProgressionTick(player));
        source.sendFeedback(() -> Text.literal(player.getGameProfile().name() + " 的阶段已设置为 " + formatPhase(phase)), true);
        return phase.getNumber();
    }

    private static int showContext(
            ServerCommandSource source,
            HorrorProgressionManager progressionManager,
            ReceiverManager receiverManager,
            PlayerContextDetector contextDetector,
            AvoidanceManager avoidanceManager,
            GroupDreadManager groupDreadManager,
            HorrorDirectorManager directorManager,
            ServerPlayerEntity player
    ) {
        PlayerHorrorData data = progressionManager.getData(player);
        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        sendDebugLine(source, debugEntry("玩家", "player", player.getGameProfile().name()));
        sendDebugLine(source, debugEntry("上下文", "context", snapshot.describe()));
        sendDebugLine(source, debugEntry("当前位置光照", "lightLevel", snapshot.lightLevel()));
        sendDebugLine(source, debugEntry("附近玩家数", "nearbyPlayers", snapshot.nearbyPlayers()));
        sendDebugLine(source, debugEntry("离地时间", "airborneTicks", formatTicks(snapshot.airborneTicks())));
        sendDebugLine(source, debugEntry("骑乘时间", "mountedTicks", formatTicks(snapshot.mountedTicks())));
        sendDebugLine(source, debugEntry("AFK/静止时间", "idleTicks", formatTicks(snapshot.idleTicks())));
        sendDebugLine(source, debugEntry("稳定区域停留", "stableAreaTicks", formatTicks(snapshot.stableAreaTicks())));
        sendDebugLine(source, debugEntry("未读 Receiver", "unreadReceiverMessages", snapshot.unreadReceiverMessages()));
        sendDebugLine(source, debugEntry("逃避值", "avoidanceScore", Math.round(avoidanceManager.getAvoidance(player))));
        sendDebugLine(source, debugEntry("个人压力", "individualDread", Math.round(data.individualDread)));
        sendDebugLine(source, debugEntry("团队压力", "groupDread", Math.round(groupDreadManager.getGroupDread(player))));
        sendDebugLine(source, debugEntry("导演状态", "director", directorManager.getStatusLine(player, source.getServer().getTicks())));
        sendDebugLine(source, debugEntry("Receiver 保存消息", "storedReceiverMessages", receiverManager.getMessageCount(player)));
        return (int) Math.round(avoidanceManager.getAvoidance(player));
    }

    private static int debugPlayer(
            ServerCommandSource source,
            HorrorProgressionManager progressionManager,
            ReceiverManager receiverManager,
            PlayerContextDetector contextDetector,
            AvoidanceManager avoidanceManager,
            GroupDreadManager groupDreadManager,
            HorrorDirectorManager directorManager,
            ServerPlayerEntity player
    ) {
        PlayerHorrorData data = progressionManager.getData(player);
        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        long serverTick = source.getServer().getTicks();
        long progressionTick = progressionManager.getProgressionTick(player);
        int unreadReceiverMessages = Math.max(0, receiverManager.getMessageCount(player) - data.lastReceiverOpenedMessageCount);

        sendDebugLine(source, "玩家恐怖导演调试");
        sendDebugLine(source, debugEntry("玩家", "player", player.getGameProfile().name()));
        sendDebugLine(source, debugEntry("当前阶段", "currentPhase", formatPhase(data.currentPhase)));
        sendDebugLine(source, debugEntry("当前阶段停留", "timeInCurrentPhase", formatTicks(data.getTimeInPhaseTicks(progressionTick))));
        sendDebugLine(source, debugEntry("上下文", "context", snapshot.describe()));
        sendDebugLine(source, debugEntry("导演状态", "director", directorManager.getStatusLine(player, serverTick)));
        sendDebugLine(source, debugEntry("逃避值", "avoidanceScore", Math.round(avoidanceManager.getAvoidance(player))));
        sendDebugLine(source, debugEntry("个人压力", "individualDread", Math.round(data.individualDread)));
        sendDebugLine(source, debugEntry("团队压力", "groupDread", Math.round(groupDreadManager.getGroupDread(player))));
        sendDebugLine(source, debugEntry("未读 Receiver", "unreadReceiverMessages", unreadReceiverMessages));
        sendDebugLine(source, debugEntry("Receiver 压力", "unreadSignalPressure", Math.round(data.unreadSignalPressure)));
        sendDebugLine(source, debugEntry("空中事件次数", "skyborneEvents", data.skyborneEvents));
        sendDebugLine(source, debugEntry("逃避触发事件", "avoidanceTriggeredEvents", data.avoidanceTriggeredEvents));
        sendDebugLine(source, debugEntry("团队压力事件", "groupDreadEvents", data.groupDreadEvents));
        sendDebugLine(source, debugEntry("基地入侵事件", "baseIntrusionEvents", data.baseIntrusionEvents));
        sendDebugLine(source, debugEntry("AFK 回归事件", "afkReleasedEvents", data.afkReleasedEvents));
        sendDebugLine(source, debugEntry("下界快速进入事件", "netherRushEvents", data.netherRushEvents));
        return 1;
    }

    private static int showAvoidance(ServerCommandSource source, AvoidanceManager avoidanceManager, ServerPlayerEntity player) {
        int score = (int) Math.round(avoidanceManager.getAvoidance(player));
        source.sendFeedback(() -> Text.literal(player.getGameProfile().name() + " 的逃避值：" + score), false);
        return score;
    }

    private static int setAvoidance(ServerCommandSource source, AvoidanceManager avoidanceManager, ServerPlayerEntity player, double value) {
        avoidanceManager.setAvoidance(player, value);
        int score = (int) Math.round(avoidanceManager.getAvoidance(player));
        source.sendFeedback(() -> Text.literal(player.getGameProfile().name() + " 的逃避值已设置为 " + score), true);
        return score;
    }

    private static int forceDirectorEvent(ServerCommandSource source, HorrorDirectorManager directorManager, ServerPlayerEntity player, String eventType) {
        if (!ItConfigManager.getConfig().horrorForceEventCommandEnabled) {
            source.sendError(Text.literal("强制事件指令已在配置中关闭。"));
            return 0;
        }

        boolean triggered = directorManager.forceEvent(player, eventType);
        if (!triggered) {
            source.sendError(Text.literal("未能触发导演事件：" + eventType));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已对 " + player.getGameProfile().name() + " 触发导演事件：" + eventType), true);
        return 1;
    }

    private static int setTestMode(ServerCommandSource source, boolean enabled) {
        ItConfig config = ItConfigManager.getConfig();
        config.horrorDirectorTestMode = enabled;
        config.horrorTestModeAcceleratedProgression = enabled;
        ItConfigManager.save();
        source.sendFeedback(() -> Text.literal(enabled ? "It 测试模式已开启。" : "It 测试模式已关闭。"), true);
        return enabled ? 1 : 0;
    }

    private static int resetPhase(ServerCommandSource source, HorrorProgressionManager progressionManager, ReceiverManager receiverManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        progressionManager.resetProgression(player);
        receiverManager.clearMessages(player);
        source.sendFeedback(() -> Text.literal("阶段与恐怖进度已重置。"), true);
        return 1;
    }

    private static int triggerFakeChat(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerFakeChat(player)) {
            source.sendError(Text.literal("配置文件已关闭神秘人私聊。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已发送一次神秘人私聊事件。"), false);
        return 1;
    }

    private static int triggerCaveSound(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerCaveSound(player)) {
            source.sendError(Text.literal("配置文件已关闭洞穴脚步声。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已播放一次洞穴脚步声事件。"), false);
        return 1;
    }

    private static int triggerMiningEcho(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerMiningEcho(player)) {
            source.sendError(Text.literal("配置文件已关闭挖掘回声。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已播放一次挖掘回声事件。"), false);
        return 1;
    }

    private static int triggerHandDrop(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerHandDrop(player)) {
            source.sendError(Text.literal("手中物品掉落异常未触发：当前没有可安全丢落的非 Receiver 手持物品，或玩家正处于强事件/创造/旁观状态。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次手中物品掉落异常。"), false);
        return 1;
    }

    private static int triggerInventoryOpen(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerInventoryOpen(player)) {
            source.sendError(Text.literal("背包打开异常未触发：玩家正处于强事件/创造/旁观状态，或当前已有其他界面打开。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次背包打开异常。"), false);
        return 1;
    }

    private static int triggerForcedChat(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerForcedChat(player)) {
            source.sendError(Text.literal("强制聊天栏异常未触发：玩家正处于旁观状态或已经死亡。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次强制聊天栏异常。"), false);
        return 1;
    }

    private static int triggerEerieSound(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerEerieSound(player)) {
            source.sendError(Text.literal("诡异声音事件未触发：玩家正处于强事件或旁观状态。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已播放一次诡异声音事件。"), false);
        return 1;
    }

    private static int triggerEerieSoundLaugh(ServerCommandSource source, HorrorEventManager horrorEventManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!horrorEventManager.triggerEerieSoundLaugh(player)) {
            source.sendError(Text.literal("诡异笑声事件未触发：玩家正处于强事件或旁观状态。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已播放一次诡异笑声事件。"), false);
        return 1;
    }

    private static int triggerFamiliarSound(ServerCommandSource source, HorrorEventManager horrorEventManager, FamiliarSoundEventType type) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (type == FamiliarSoundEventType.CHEST && !horrorEventManager.hasNearbyContainerForFakeChest(player)) {
            source.sendError(Text.literal("附近没有可用容器，无法播放假箱子声。"));
            return 0;
        }

        boolean success = type == null ? horrorEventManager.triggerFamiliarSound(player) : horrorEventManager.triggerFamiliarSound(player, type);
        if (!success) {
            source.sendError(Text.literal("熟悉行为假声事件未触发：玩家正处于强事件、旁观状态或条件不足。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发熟悉行为假声事件。"), false);
        return 1;
    }

    private static int triggerSeparation(ServerCommandSource source, MultiplayerDreadManager manager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        boolean singlePlayerTest = source.getServer().getPlayerManager().getPlayerList().size() <= 1;
        if (!manager.triggerSeparationWarning(player, true)) {
            source.sendError(Text.literal("分离警告未触发。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(singlePlayerTest ? "已触发分离警告。当前为单人测试。" : "已触发分离警告。"), false);
        return 1;
    }

    private static int triggerTeammateFootsteps(ServerCommandSource source, MultiplayerDreadManager manager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        boolean singlePlayerTest = source.getServer().getPlayerManager().getPlayerList().size() <= 1;
        if (!manager.triggerFakeTeammateFootsteps(player, true)) {
            source.sendError(Text.literal("假队友脚步未触发。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(singlePlayerTest ? "已触发假队友脚步。当前为多人机制的单人测试。" : "已触发假队友脚步。"), false);
        return 1;
    }

    private static int triggerTeamGaze(ServerCommandSource source, MultiplayerDreadManager manager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        boolean singlePlayerTest = source.getServer().getPlayerManager().getPlayerList().size() <= 1;
        if (!manager.triggerTeamGaze(player, true)) {
            source.sendError(Text.literal("队伍同步凝视未触发：附近实体不足。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(singlePlayerTest ? "已触发队伍同步凝视。当前为单人测试模式。" : "已触发队伍同步凝视。"), false);
        return 1;
    }

    private static int triggerFakeTab(ServerCommandSource source, MultiplayerDreadManager manager) throws CommandSyntaxException {
        if (!manager.triggerFakeTab(source.getPlayerOrThrow(), true)) {
            source.sendError(Text.literal("假 Tab 玩家列表异常未触发。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发假 Tab 玩家列表异常。"), false);
        return 1;
    }

    private static int triggerFakeAdvancement(ServerCommandSource source, MultiplayerDreadManager manager) throws CommandSyntaxException {
        if (!manager.triggerFakeAdvancement(source.getPlayerOrThrow(), true)) {
            source.sendError(Text.literal("假成就提示未触发。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发假成就提示。"), false);
        return 1;
    }

    private static int triggerMimicPlayer(ServerCommandSource source, MultiplayerDreadManager manager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (source.getServer().getPlayerManager().getPlayerList().size() < 2) {
            source.sendError(Text.literal("伪装玩家事件未触发：需要至少两名在线玩家。"));
            return 0;
        }

        if (!manager.triggerMimicPlayer(player, true)) {
            source.sendError(Text.literal("伪装玩家事件未触发：附近没有安全站立位置，或当前已有伪装玩家。"));
            return 0;
        }

        Vec3d pos = manager.getActiveMimicPlayerPosition(player);
        if (pos == null) {
            source.sendFeedback(() -> Text.literal("已触发伪装玩家事件。"), false);
        } else {
            source.sendFeedback(() -> Text.literal(String.format(
                    "已触发伪装玩家事件，生成坐标：x=%.1f y=%.1f z=%.1f",
                    pos.x,
                    pos.y,
                    pos.z
            )), false);
        }
        return 1;
    }

    private static int clearMimicPlayers(ServerCommandSource source, MultiplayerDreadManager manager) {
        int removed = manager.clearMimicPlayers();
        source.sendFeedback(() -> Text.literal("已清除 " + removed + " 个伪装玩家。"), false);
        return removed;
    }

    private static int triggerFakeRescue(ServerCommandSource source, MultiplayerDreadManager manager, Boolean helpful) throws CommandSyntaxException {
        if (!manager.triggerFakeRescue(source.getPlayerOrThrow(), helpful, true)) {
            source.sendError(Text.literal("假救援信息未触发。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发假救援信息。"), false);
        return 1;
    }

    private static int triggerAnimalDisguise(ServerCommandSource source, AnimalDisguiseManager manager) throws CommandSyntaxException {
        if (!manager.triggerAnimalDisguise(source.getPlayerOrThrow(), true)) {
            source.sendError(Text.literal("伪装动物未生成：附近没有安全站立位置。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已在附近生成一只伪装动物。"), false);
        return 1;
    }

    private static int triggerAnimalDisguiseAggressive(ServerCommandSource source, AnimalDisguiseManager manager) throws CommandSyntaxException {
        if (!manager.triggerAnimalDisguise(source.getPlayerOrThrow(), true, true)) {
            source.sendError(Text.literal("攻击型伪装动物未生成：附近没有安全站立位置。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已在附近生成一只攻击型伪装动物。"), false);
        return 1;
    }

    private static int triggerReceiver(ServerCommandSource source, AnalogHorrorManager analogHorrorManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!analogHorrorManager.forceReceiverMessage(player)) {
            source.sendError(Text.literal("没有可记录的接收器信号。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("接收器已记录一条新信号。"), false);
        return 1;
    }

    private static int triggerReceiverWeather(ServerCommandSource source, AnalogHorrorManager analogHorrorManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!analogHorrorManager.forceWeatherMessage(player)) {
            source.sendError(Text.literal("无法记录天气信号。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("旧式接收器已记录一条天气信号。"), false);
        return 1;
    }

    private static int triggerNetherSignal(ServerCommandSource source, NetherSignalManager netherSignalManager) throws CommandSyntaxException {
        ServerPlayerEntity player = getNetherPlayer(source);
        if (player == null) {
            return 0;
        }

        if (!netherSignalManager.triggerReceiverSignal(player, true)) {
            source.sendError(Text.literal("下界接收器信号未触发：配置关闭或玩家状态无效。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已安排一次下界接收器信号，Receiver 会按延迟写入。"), false);
        return 1;
    }

    private static int triggerNetherGhastCry(ServerCommandSource source, NetherSignalManager netherSignalManager) throws CommandSyntaxException {
        ServerPlayerEntity player = getNetherPlayer(source);
        if (player == null) {
            return 0;
        }

        if (!netherSignalManager.triggerPhantomGhastCry(player, true)) {
            source.sendError(Text.literal("幽灵恶魂哭声未触发：配置关闭或玩家状态无效。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已向目标玩家播放一次幽灵恶魂哭声。"), false);
        return 1;
    }

    private static int triggerNetherSoulSand(ServerCommandSource source, NetherSignalManager netherSignalManager) throws CommandSyntaxException {
        ServerPlayerEntity player = getNetherPlayer(source);
        if (player == null) {
            return 0;
        }

        if (!netherSignalManager.hasNearbySoulMaterial(player)) {
            source.sendError(Text.literal("灵魂沙低语未触发：附近没有灵魂沙、灵魂土或灵魂火类方块。"));
            return 0;
        }

        if (!netherSignalManager.triggerSoulSandWhisper(player, true)) {
            source.sendError(Text.literal("灵魂沙低语未触发：配置关闭或玩家状态无效。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已向目标玩家播放一次灵魂沙低语。"), false);
        return 1;
    }

    private static int triggerNetherPortal(ServerCommandSource source, NetherSignalManager netherSignalManager) throws CommandSyntaxException {
        ServerPlayerEntity player = getNetherPlayer(source);
        if (player == null) {
            return 0;
        }

        if (!netherSignalManager.hasNearbyPortal(player)) {
            source.sendError(Text.literal("传送门异常未触发：附近没有下界传送门方块。"));
            return 0;
        }

        if (!netherSignalManager.triggerPortalAnomaly(player, true)) {
            source.sendError(Text.literal("传送门异常未触发：配置关闭或玩家状态无效。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次传送门返回路径异常，Receiver 会按延迟写入。"), false);
        return 1;
    }

    private static ServerPlayerEntity getNetherPlayer(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!NetherSignalManager.isInNether(player)) {
            source.sendError(Text.literal("该 Nether 事件需要玩家位于下界。"));
            return null;
        }

        return player;
    }

    private static int triggerFakeJoin(ServerCommandSource source, ManifestationManager manifestationManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!manifestationManager.triggerFakePlayerVisit(player, player.getEntityWorld().getServer().getTicks(), true)) {
            source.sendError(Text.literal("无法触发假玩家进入事件。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次假玩家进入/离开事件。"), false);
        return 1;
    }

    private static int triggerFakeJoinChase(ServerCommandSource source, ManifestationManager manifestationManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!manifestationManager.triggerFakePlayerChaseVisit(player, player.getEntityWorld().getServer().getTicks(), true)) {
            source.sendError(Text.literal("无法触发追猎联动假玩家事件，可能已经处于追猎中。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次追猎联动假玩家事件。"), false);
        return 1;
    }

    private static int triggerSleepBlock(ServerCommandSource source, ManifestationManager manifestationManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!manifestationManager.triggerSleepInterference(player, true)) {
            source.sendError(Text.literal("无法触发睡觉干扰。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次睡觉干扰提示。"), false);
        return 1;
    }

    private static int triggerAnimalStare(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        boolean success = worldAnomalyManager.forceAnimalStare(player, player.getEntityWorld().getServer().getTicks());
        if (!success) {
            int required = Math.max(1, ItConfigManager.getConfig().animalStareMinAnimals);
            int found = worldAnomalyManager.getEligibleAnimalCount(player);
            source.sendError(Text.literal("附近动物数量不足，需要至少 " + required + " 只。当前可用：" + found + " 只。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已强制附近多只合适动物短暂盯着玩家。"), false);
        return 1;
    }

    private static int triggerAnimalStareAlias(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        source.sendFeedback(() -> Text.literal("该指令已合并，请使用 /it event animalstare"), false);
        return triggerAnimalStare(source, worldAnomalyManager);
    }

    private static int triggerDoorAnomaly(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        return reportAnomaly(
                source,
                worldAnomalyManager.forceOpenDoor(source.getPlayerOrThrow()),
                "已打开附近一扇门。",
                "附近没有可打开的关闭状态门。"
        );
    }

    private static int triggerRemoveAnomaly(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        return reportAnomaly(
                source,
                worldAnomalyManager.forceRemoveSmallObject(source.getPlayerOrThrow()),
                "已移除附近一个小物件。",
                "附近没有可安全移除的火把或花盆。"
        );
    }

    private static int triggerFlowerAnomaly(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        return reportAnomaly(
                source,
                worldAnomalyManager.forcePottedFlower(source.getPlayerOrThrow()),
                "已在附近放置一盆花。",
                "附近没有适合放置花盆的空气方块。"
        );
    }

    private static int triggerSignAnomaly(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        return reportAnomaly(
                source,
                worldAnomalyManager.forceSign(source.getPlayerOrThrow()),
                "已在附近生成异常告示牌。",
                "附近没有适合放置告示牌的位置。"
        );
    }

    private static int triggerCrossAnomaly(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        return reportAnomaly(
                source,
                worldAnomalyManager.forceNetherrackCross(source.getPlayerOrThrow()),
                "已在附近生成地狱岩十字架。",
                "附近没有适合生成十字架的空位。"
        );
    }

    private static int triggerBaseAnomaly(ServerCommandSource source, WorldAnomalyManager worldAnomalyManager) throws CommandSyntaxException {
        return reportAnomaly(
                source,
                worldAnomalyManager.forceBaseAnomaly(source.getPlayerOrThrow()),
                "已触发一次随机基地异常。",
                "附近没有可安全触发的基地异常位置。"
        );
    }

    private static int reportAnomaly(ServerCommandSource source, boolean success, String successMessage, String failureMessage) {
        if (!success) {
            source.sendError(Text.literal(failureMessage));
            return 0;
        }

        source.sendFeedback(() -> Text.literal(successMessage), false);
        return 1;
    }

    private static int triggerInterference(ServerCommandSource source, ManifestationManager manifestationManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!manifestationManager.triggerInterference(player, player.getEntityWorld().getServer().getTicks(), true)) {
            source.sendError(Text.literal("配置文件已关闭第五阶段干扰。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次第五阶段干扰。"), false);
        return 1;
    }

    private static int triggerJumpscare(ServerCommandSource source, JumpscareManager jumpscareManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!jumpscareManager.triggerJumpscare(player, player.getEntityWorld().getServer().getTicks(), true)) {
            source.sendError(Text.literal("配置文件已关闭 jumpscare。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次 jumpscare。"), false);
        return 1;
    }

    private static int triggerFaceScare(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItNetwork.sendFaceScare(player, Math.max(35, ItConfigManager.getConfig().faceScareDurationTicks), 1.0F, true);
        ItMod.getReceiverManager().scheduleMessage(player, ReceiverMessageType.MANIFESTATION, HorrorPhase.MANIFESTATION, "突脸记录已写入。", 40);
        source.sendFeedback(() -> Text.literal("已触发一次全屏突脸效果。"), false);
        return 1;
    }

    private static int triggerHuntFaceScare(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ItNetwork.sendHuntFaceScare(player, Math.max(35, ItConfigManager.getConfig().chaseFaceScareDurationTicks), 1.0F, true);
        ItMod.getReceiverManager().scheduleMessage(player, ReceiverMessageType.CHASE, HorrorPhase.MANIFESTATION, "追猎突脸记录已写入。", 40);
        source.sendFeedback(() -> Text.literal("已触发一次追猎突脸效果。"), false);
        return 1;
    }

    private static int triggerViewDistanceAnomaly(ServerCommandSource source, ClientDistortionManager clientDistortionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!clientDistortionManager.forceViewDistanceDrop(player, player.getEntityWorld().getServer().getTicks())) {
            source.sendError(Text.literal("无法触发视距异常。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次视距异常。"), false);
        return 1;
    }

    private static int triggerLegacyTextureAnomaly(ServerCommandSource source, ClientDistortionManager clientDistortionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!clientDistortionManager.forceLegacyTexture(player, player.getEntityWorld().getServer().getTicks())) {
            source.sendError(Text.literal("无法触发旧版材质异常。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次旧版材质雪花异常。"), false);
        return 1;
    }

    private static int triggerMonochromeAnomaly(ServerCommandSource source, ClientDistortionManager clientDistortionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!clientDistortionManager.forceMonochrome(player, player.getEntityWorld().getServer().getTicks())) {
            source.sendError(Text.literal("无法触发黑白画面异常。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次黑白画面异常。"), false);
        return 1;
    }

    private static int triggerChase(ServerCommandSource source, ChaseManager chaseManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!chaseManager.triggerChase(player, player.getEntityWorld().getServer().getTicks(), true)) {
            source.sendError(Text.literal("当前玩家已经处于追猎事件中。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发一次追猎事件预警。"), false);
        return 1;
    }

    private static int stopChase(ServerCommandSource source, ChaseManager chaseManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!chaseManager.stopChase(player, player.getEntityWorld().getServer().getTicks())) {
            source.sendError(Text.literal("当前没有正在进行的追猎事件。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已停止当前追猎事件。"), true);
        return 1;
    }

    private static int chaseStatus(ServerCommandSource source, ChaseManager chaseManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        ChaseState state = chaseManager.getState(player);
        sendDebugLine(source, chaseManager.getStatusLine(player, currentTick));
        if (state != null) {
            sendDebugLine(source, "追猎预警：" + formatBoolean(state.warning));
            sendDebugLine(source, "追猎激活：" + formatBoolean(state.active));
            sendDebugLine(source, "追猎者 UUID：" + state.chaserEntityId);
            sendDebugLine(source, "强光逃脱 ticks：" + state.brightLightTicks);
            sendDebugLine(source, "靠近队友 ticks：" + state.nearPlayerTicks);
        }
        return 1;
    }

    private static int resetChaseCooldown(ServerCommandSource source, ChaseManager chaseManager) throws CommandSyntaxException {
        chaseManager.resetCooldown(source.getPlayerOrThrow());
        source.sendFeedback(() -> Text.literal("追猎冷却已重置。"), true);
        return 1;
    }

    private static int triggerCaveStalker(ServerCommandSource source, CaveStalkerManager caveStalkerManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!caveStalkerManager.triggerCaveStalker(player, player.getEntityWorld().getServer().getTicks(), true)) {
            source.sendError(Text.literal("当前玩家已经处于 Cave Stalker 事件中。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已触发 Cave Stalker / 矿洞潜行者预警。"), false);
        return 1;
    }

    private static int triggerTunnelWatcher(ServerCommandSource source, WatcherSpawnManager watcherSpawnManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!watcherSpawnManager.forceSpawnTunnelWatcher(player)) {
            source.sendError(Text.literal("没有找到合适的矿道位置生成 Tunnel Watcher。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已尝试生成一次 Tunnel Watcher / 矿道窥视者。"), false);
        return 1;
    }

    private static int stopCaveStalker(ServerCommandSource source, CaveStalkerManager caveStalkerManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!caveStalkerManager.stopCaveStalker(player, player.getEntityWorld().getServer().getTicks())) {
            source.sendError(Text.literal("当前没有正在进行的 Cave Stalker 事件。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已停止当前 Cave Stalker 事件。"), true);
        return 1;
    }

    private static int caveStalkerStatus(ServerCommandSource source, CaveStalkerManager caveStalkerManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        CaveStalkerState state = caveStalkerManager.getState(player);
        sendDebugLine(source, caveStalkerManager.getStatusLine(player, currentTick));
        if (state != null) {
            sendDebugLine(source, "Cave Stalker phase: " + state.phase);
            sendDebugLine(source, "hasEntity: " + formatBoolean(state.stalkerEntityId != null));
            sendDebugLine(source, "waitingForReceiverOpen: " + formatBoolean(state.isWaitingForReceiver()));
            sendDebugLine(source, "waitingForLookBack: " + formatBoolean(state.phase.name().contains("LOOK_BACK")));
            sendDebugLine(source, "blockedTicks: " + state.blockedTicks);
            sendDebugLine(source, "nearPlayerTicks: " + state.nearPlayerTicks);
            sendDebugLine(source, "escapeDistanceTicks: " + state.escapeDistanceTicks);
        }
        return 1;
    }

    private static int resetCaveStalkerCooldown(ServerCommandSource source, CaveStalkerManager caveStalkerManager) throws CommandSyntaxException {
        caveStalkerManager.resetCooldown(source.getPlayerOrThrow());
        source.sendFeedback(() -> Text.literal("Cave Stalker 冷却已重置。"), true);
        return 1;
    }

    private static int giveReceiver(ServerCommandSource source, ReceiverManager receiverManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        receiverManager.giveReceiver(player, true);
        source.sendFeedback(() -> Text.literal("已给予旧式接收器。"), true);
        return 1;
    }

    private static int receiverStatus(ServerCommandSource source, ReceiverManager receiverManager, HorrorProgressionManager progressionManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerHorrorData data = progressionManager.getData(player);
        source.sendFeedback(() -> Text.literal("拥有旧式接收器：" + receiverManager.hasReceiver(player)
                + "\n曾获得接收器：" + data.hasReceivedReceiver
                + "\n已保存信号：" + receiverManager.getMessageCount(player)
                + "\n进度记录信号数：" + data.receiverMessagesReceived), false);
        return 1;
    }

    private static int clearReceiver(ServerCommandSource source, ReceiverManager receiverManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        receiverManager.clearMessages(player);
        source.sendFeedback(() -> Text.literal("接收器信号已清空。"), true);
        return 1;
    }

    private static int debug(ServerCommandSource source, WatchingLevelManager watchingLevelManager, HorrorProgressionManager progressionManager, ReceiverManager receiverManager, ManifestationManager manifestationManager, JumpscareManager jumpscareManager, ChaseManager chaseManager, CaveStalkerManager caveStalkerManager, AnimalDisguiseManager animalDisguiseManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        PlayerHorrorData data = progressionManager.getData(player);
        long progressionTick = progressionManager.getProgressionTick(player);
        long serverTick = player.getEntityWorld().getServer().getTicks();
        ItConfig config = ItConfigManager.getConfig();
        sendDebugLine(source, "It 调试信息");
        sendDebugLine(source, debugEntry("玩家", "player", player.getGameProfile().name()));
        sendDebugLine(source, debugEntry("玩家 UUID", "playerUuid", data.playerId));
        sendDebugLine(source, debugEntry("被注视值记录", "watchingLevelStored", data.watchingLevel));
        sendDebugLine(source, debugEntry("被注视值实时", "watchingLevelLive", (int) Math.round(watchingLevelManager.getWatchingLevel(player))));
        sendDebugLine(source, debugEntry("当前阶段", "currentPhase", formatPhase(data.currentPhase)));
        sendDebugLine(source, debugEntry("阶段编号", "phaseNumber", data.currentPhase.getNumber()));
        sendDebugLine(source, debugEntry("当前阶段停留", "timeInCurrentPhase", formatTicks(data.getTimeInPhaseTicks(progressionTick))));
        sendDebugLine(source, debugEntry("总游玩时间", "totalPlayTicks", formatTicks(data.totalPlayTicks)));
        sendDebugLine(source, debugEntry("洞穴脚步事件", "caveFootstepEvents", data.caveFootstepEvents));
        sendDebugLine(source, debugEntry("挖掘回声事件", "miningEchoEvents", data.miningEchoEvents));
        sendDebugLine(source, debugEntry("假聊天事件", "fakeChatEvents", data.fakeChatEvents));
        sendDebugLine(source, debugEntry("手中物品掉落异常", "handDropAnomalies", data.handDropAnomalies));
        sendDebugLine(source, debugEntry("背包打开异常", "inventoryOpenAnomalies", data.inventoryOpenAnomalies));
        sendDebugLine(source, debugEntry("神秘人私聊次数", "mysteriousContactMessages", data.mysteriousContactMessages));
        sendDebugLine(source, debugEntry("诡异声音事件", "eerieSoundEvents", data.eerieSoundEvents));
        sendDebugLine(source, debugEntry("动物注视事件", "animalStareEvents", data.animalStareEvents));
        sendDebugLine(source, debugEntry("熟悉行为假声事件", "familiarSoundEvents", data.familiarSoundEvents));
        sendDebugLine(source, debugEntry("分离警告次数", "separationWarnings", data.separationWarnings));
        sendDebugLine(source, debugEntry("假队友脚步事件", "fakeTeammateFootstepEvents", data.fakeTeammateFootstepEvents));
        sendDebugLine(source, debugEntry("队伍同步凝视事件", "teamGazeEvents", data.teamGazeEvents));
        sendDebugLine(source, debugEntry("假 Tab 事件", "fakeTabEvents", data.fakeTabEvents));
        sendDebugLine(source, debugEntry("假成就提示事件", "fakeAdvancementEvents", data.fakeAdvancementEvents));
        sendDebugLine(source, debugEntry("假救援信息次数", "fakeRescueMessages", data.fakeRescueMessages));
        sendDebugLine(source, debugEntry("伪装动物生成次数", "animalDisguiseEvents", data.animalDisguiseEvents));
        sendDebugLine(source, debugEntry("伪装动物击杀次数", "animalDisguiseKilledEvents", data.animalDisguiseKilledEvents));
        sendDebugLine(source, debugEntry("伪装动物攻击动物次数", "animalDisguiseAnimalAttacks", data.animalDisguiseAnimalAttacks));
        sendDebugLine(source, debugEntry("伪装动物受害动物数", "animalDisguiseVictims", data.animalDisguiseVictims));
        sendDebugLine(source, debugEntry("伪装动物反噬次数", "animalDisguiseRetaliations", data.animalDisguiseRetaliations));
        sendDebugLine(source, debugEntry("Watcher 目击次数", "watcherSightings", data.watcherSightings));
        sendDebugLine(source, debugEntry("接收器消息总数", "receiverMessagesReceived", data.receiverMessagesReceived));
        sendDebugLine(source, debugEntry("接收器打开次数", "receiverOpenedCount", data.receiverOpenedCount));
        sendDebugLine(source, debugEntry("接收器已保存消息", "storedReceiverMessages", receiverManager.getMessageCount(player)));
        sendDebugLine(source, debugEntry("当前拥有接收器", "hasReceiverNow", formatBoolean(receiverManager.hasReceiver(player))));
        sendDebugLine(source, debugEntry("曾获得接收器", "hasReceivedReceiver", formatBoolean(data.hasReceivedReceiver)));
        sendDebugLine(source, debugEntry("Jumpscare 次数", "jumpscareEvents", data.jumpscareEvents));
        sendDebugLine(source, debugEntry("第五阶段干扰次数", "phaseFiveInterferenceEvents", data.phaseFiveInterferenceEvents));
        sendDebugLine(source, debugEntry("追猎事件次数", "chaseEvents", data.chaseEvents));
        sendDebugLine(source, debugEntry("追猎逃脱次数", "chaseEscapes", data.chaseEscapes));
        sendDebugLine(source, debugEntry("追猎被抓次数", "chaseCaughtEvents", data.chaseCaughtEvents));
        sendDebugLine(source, debugEntry("上次追猎时间", "lastChaseGameTime", data.lastChaseGameTime));
        sendDebugLine(source, debugEntry("当前追猎状态", "currentChaseState", chaseManager.getStatusLine(player, serverTick)));
        sendDebugLine(source, debugEntry("Cave Stalker 事件次数", "caveStalkerEvents", data.caveStalkerEvents));
        sendDebugLine(source, debugEntry("Cave Stalker 逃脱次数", "caveStalkerEscapes", data.caveStalkerEscapes));
        sendDebugLine(source, debugEntry("Cave Stalker 被抓次数", "caveStalkerCaughtEvents", data.caveStalkerCaughtEvents));
        sendDebugLine(source, debugEntry("Cave Stalker 陷阱触发次数", "caveStalkerTrapTriggers", data.caveStalkerTrapTriggers));
        sendDebugLine(source, debugEntry("Cave Stalker 彩蛋次数", "caveStalkerEasterEggs", data.caveStalkerEasterEggs));
        sendDebugLine(source, debugEntry("上次 Cave Stalker 时间", "lastCaveStalkerGameTime", data.lastCaveStalkerGameTime));
        sendDebugLine(source, debugEntry("当前 Cave Stalker 状态", "currentCaveStalkerState", caveStalkerManager.getStatusLine(player, serverTick)));
        sendDebugLine(source, debugEntry("Cave Stalker 冷却", "caveStalkerCooldown", formatTicks(Math.max(0L, caveStalkerManager.getNextCaveStalkerTick(player) - serverTick))));
        sendDebugLine(source, debugEntry("地下累计时间", "undergroundTicks", formatTicks(data.undergroundTicks)));
        sendDebugLine(source, debugEntry("黑暗独处累计时间", "darkAloneTicks", formatTicks(data.darkAloneTicks)));
        sendDebugLine(source, debugEntry("夜晚独处累计时间", "nightAloneTicks", formatTicks(data.nightAloneTicks)));
        sendDebugLine(source, debugEntry("已看见 Watcher", "hasSeenWatcher", formatBoolean(data.hasSeenWatcher)));
        sendDebugLine(source, debugEntry("已触发显现", "hasTriggeredManifestation", formatBoolean(data.hasTriggeredManifestation)));
        sendDebugLine(source, debugEntry("下次干扰冷却", "nextInterferenceCooldown", formatTicks(Math.max(0L, manifestationManager.getNextInterferenceTick(player) - serverTick))));
        sendDebugLine(source, debugEntry("下次 Jumpscare 冷却", "nextJumpscareCooldown", formatTicks(Math.max(0L, jumpscareManager.getNextJumpscareTick(player) - serverTick))));
        sendDebugLine(source, debugEntry("伪装动物状态", "animalDisguiseStatus", animalDisguiseManager.getStatusLine(player)));
        sendDebugLine(source, debugEntry("配置：阶段系统", "enablePhaseSystem", formatBoolean(config.enablePhaseSystem)));
        sendDebugLine(source, debugEntry("配置：接收器", "enableReceiver", formatBoolean(config.enableReceiver)));
        sendDebugLine(source, debugEntry("配置：第五阶段干扰", "enablePhaseFiveInterference", formatBoolean(config.enablePhaseFiveInterference)));
        sendDebugLine(source, debugEntry("配置：Jumpscare", "enableJumpscares", formatBoolean(config.enableJumpscares)));
        sendDebugLine(source, debugEntry("配置：FaceScare", "enableFaceScare", formatBoolean(config.enableFaceScare)));
        sendDebugLine(source, debugEntry("配置：追猎事件", "enableChaseEvents", formatBoolean(config.enableChaseEvents)));
        sendDebugLine(source, debugEntry("配置：视距异常", "enableViewDistanceAnomalies", formatBoolean(config.enableViewDistanceAnomalies)));
        sendDebugLine(source, debugEntry("配置：旧版材质异常", "enableLegacyTextureAnomalies", formatBoolean(config.enableLegacyTextureAnomalies)));
        sendDebugLine(source, debugEntry("配置：多人真实名假聊天", "fakeChatUseRealPlayerNames", formatBoolean(config.fakeChatUseRealPlayerNames)));
        sendDebugLine(source, debugEntry("配置：降低闪烁效果", "reduceFlashingEffects", formatBoolean(config.reduceFlashingEffects)));
        sendDebugLine(source, debugEntry("配置：禁用快速闪烁", "disableRapidFlashes", formatBoolean(config.disableRapidFlashes)));
        return 1;
    }

    private static int resetDebugData(ServerCommandSource source, HorrorProgressionManager progressionManager, ReceiverManager receiverManager, ChaseManager chaseManager, CaveStalkerManager caveStalkerManager, MultiplayerDreadManager multiplayerDreadManager, AnimalDisguiseManager animalDisguiseManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        long serverTick = player.getEntityWorld().getServer().getTicks();
        chaseManager.stopChase(player, serverTick);
        chaseManager.resetCooldown(player);
        caveStalkerManager.stopCaveStalker(player, serverTick);
        caveStalkerManager.resetCooldown(player);
        multiplayerDreadManager.remove(player);
        animalDisguiseManager.remove(player);
        progressionManager.resetProgression(player, false);
        receiverManager.clearMessages(player);
        receiverManager.clearSignalBlocked(player.getUuid());
        source.sendFeedback(() -> Text.literal("调试数据已清零，进度恢复为新世界初始状态。"), true);
        return 1;
    }

    private static int showConfig(ServerCommandSource source) {
        ItConfig config = ItConfigManager.getConfig();
        sendDebugLine(source, "It 配置");
        for (Field field : ItConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                sendDebugLine(source, configEntry(field.getName(), field.get(config)));
            } catch (IllegalAccessException ignored) {
                sendDebugLine(source, configEntry(field.getName(), "<无法读取>"));
            }
        }
        return 1;
    }

    private static int setConfigValue(ServerCommandSource source, String key, String rawValue) {
        Field field = resolveConfigField(key);
        if (field == null) {
            source.sendError(Text.literal("未知配置项：" + key + "。请先使用 /it config get 查看可用名称。"));
            return 0;
        }

        Object value;
        try {
            value = parseConfigValue(field.getType(), rawValue);
        } catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("无法解析配置值：" + rawValue + "。目标类型：" + field.getType().getSimpleName()));
            return 0;
        }

        try {
            field.set(ItConfigManager.getConfig(), value);
        } catch (IllegalAccessException exception) {
            source.sendError(Text.literal("无法写入配置项：" + field.getName()));
            return 0;
        }

        ItConfigManager.save();
        if ("enableTestCommands".equals(field.getName())) {
            refreshCommandTrees(source);
        }
        source.sendFeedback(() -> Text.literal("It 配置: " + configLabel(field.getName()) + "=" + formatConfigValue(value)), true);
        return 1;
    }

    private static Field resolveConfigField(String key) {
        String normalized = key.trim();
        String fieldName = switch (normalized.toLowerCase()) {
            case "watcher" -> "enableWatcher";
            case "cavesounds" -> "enableCaveSounds";
            case "miningecho" -> "enableMiningEchoSounds";
            case "fakechat" -> "enableFakeChat";
            case "fakechatnames" -> "fakeChatUseRealPlayerNames";
            case "debug" -> "debugMode";
            default -> normalized;
        };

        try {
            Field field = ItConfig.class.getField(fieldName);
            return Modifier.isStatic(field.getModifiers()) ? null : field;
        } catch (NoSuchFieldException exception) {
            return null;
        }
    }

    private static Object parseConfigValue(Class<?> type, String rawValue) {
        String value = rawValue.trim();
        if (type == boolean.class || type == Boolean.class) {
            String lower = value.toLowerCase();
            return switch (lower) {
                case "true", "1", "on", "enable", "enabled", "yes", "y", "开", "开启", "是" -> true;
                case "false", "0", "off", "disable", "disabled", "no", "n", "关", "关闭", "否" -> false;
                default -> throw new IllegalArgumentException("Invalid boolean");
            };
        }

        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        }

        if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        }

        if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }

        if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        }

        if (type == String.class) {
            return rawValue;
        }

        throw new IllegalArgumentException("Unsupported type");
    }

    private static int setCaveSounds(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().enableCaveSounds = enabled;
        return saveAndReport(source, "cavesounds", enabled);
    }

    private static int setWatcher(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().enableWatcher = enabled;
        return saveAndReport(source, "watcher", enabled);
    }

    private static int setMiningEcho(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().enableMiningEchoSounds = enabled;
        return saveAndReport(source, "miningecho", enabled);
    }

    private static int setFakeChat(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().enableFakeChat = enabled;
        return saveAndReport(source, "fakechat", enabled);
    }

    private static int setFakeChatNames(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().fakeChatUseRealPlayerNames = enabled;
        return saveAndReport(source, "fakechatnames", enabled);
    }

    private static int setDebugMode(ServerCommandSource source, boolean enabled) {
        ItConfigManager.getConfig().debugMode = enabled;
        return saveAndReport(source, "debug", enabled);
    }

    private static int saveAndReport(ServerCommandSource source, String name, boolean enabled) {
        ItConfigManager.save();
        source.sendFeedback(() -> Text.literal("It 配置: " + configLabel(name) + "=" + formatBoolean(enabled)), true);
        return 1;
    }

    private static int spawnWatcher(ServerCommandSource source, WatcherSpawnManager watcherSpawnManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!watcherSpawnManager.forceSpawnNear(player)) {
            source.sendError(Text.literal("没有找到安全位置生成 Watcher。"));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("已强制生成一个 Watcher。"), true);
        return 1;
    }

    private static int clearWatchers(ServerCommandSource source, WatcherSpawnManager watcherSpawnManager) {
        int removed = watcherSpawnManager.clearWatchers(source.getServer());
        source.sendFeedback(() -> Text.literal("已清理 " + removed + " 个 Watcher。"), true);
        return removed;
    }

    private static int clearAnimalDisguises(ServerCommandSource source, AnimalDisguiseManager manager) {
        int removed = manager.clearDisguises(source.getServer());
        source.sendFeedback(() -> Text.literal("已清理伪装动物：" + removed), true);
        return removed;
    }

    private static int animalDisguiseStatus(ServerCommandSource source, AnimalDisguiseManager manager) throws CommandSyntaxException {
        AnimalDisguiseManager.AnimalDisguiseStatus status = manager.status(source.getPlayerOrThrow());
        source.sendFeedback(() -> Text.literal("伪装动物数量：" + status.activeCount()
                + "，剩余冷却：" + formatTicks(status.cooldownRemainingTicks())
                + "，状态：" + status.details()), false);
        return 1;
    }

    private static int watcherDebug(ServerCommandSource source, WatcherSpawnManager watcherSpawnManager) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        WatcherSpawnManager.TunnelWatcherDebugInfo info = watcherSpawnManager.getTunnelWatcherDebugInfo(player);
        sendDebugLine(source, "Tunnel Watcher 调试");
        sendDebugLine(source, debugEntry("当前阶段", "phase", formatPhase(info.phase)));
        sendDebugLine(source, debugEntry("当前位置光照", "lightLevel", info.lightLevel + " / max " + info.maxLightLevel));
        sendDebugLine(source, debugEntry("配置启用", "enabled", formatBoolean(info.enabled)));
        sendDebugLine(source, debugEntry("阶段达标", "phaseAllowed", formatBoolean(info.phaseAllowed)));
        sendDebugLine(source, debugEntry("矿洞环境", "caveLike", formatBoolean(info.caveLike)));
        sendDebugLine(source, debugEntry("低光照", "lowLight", formatBoolean(info.lowLight)));
        sendDebugLine(source, debugEntry("强事件中", "strongEventActive", formatBoolean(info.strongEventActive)));
        sendDebugLine(source, debugEntry("冷却结束", "cooldownReady", formatBoolean(info.cooldownReady)));
        sendDebugLine(source, debugEntry("剩余冷却", "cooldownRemaining", formatTicks(info.cooldownTicks)));
        sendDebugLine(source, debugEntry("当前可尝试", "canTry", formatBoolean(info.canTry)));
        sendDebugLine(source, debugEntry("最近失败原因", "lastFailureReason", info.lastFailureReason));
        return 1;
    }

    private static String formatTicks(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes + "分 " + remainingSeconds + "秒";
    }

    private static void sendDebugLine(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal(text), false);
    }

    private static String debugEntry(String chinese, String english, Object value) {
        return "【" + chinese + "】（" + english + "）：" + value;
    }

    private static String configEntry(String chinese, String english, boolean value) {
        return debugEntry(chinese, english, formatBoolean(value));
    }

    private static String configEntry(String english, Object value) {
        return debugEntry(configChineseName(english), english, formatConfigValue(value));
    }

    private static String configLabel(String name) {
        Field field = resolveConfigField(name);
        String english = field == null ? name : field.getName();
        return "【" + configChineseName(english) + "】（" + english + "）";
    }

    private static String configChineseName(String english) {
        return switch (english) {
            case "enableWatchingLevel" -> "启用被注视值";
            case "enableWatcher" -> "启用 Watcher";
            case "enableCaveSounds" -> "启用洞穴脚步声";
            case "enableMiningEchoSounds" -> "启用挖掘回声";
            case "enableFakeChat" -> "启用假聊天";
            case "fakeChatUseRealPlayerNames" -> "多人假聊天使用真实玩家名";
            case "enableMysteriousContact" -> "启用神秘人私聊";
            case "mysteriousContactMinPhase" -> "神秘人私聊最低阶段";
            case "mysteriousContactHelpfulChance" -> "神秘人私聊帮助信息概率";
            case "mysteriousContactCooldownSeconds" -> "神秘人私聊冷却秒数";
            case "enableBaseAnomalies" -> "启用基础异常";
            case "watcherSpawnChanceMultiplier" -> "Watcher 生成概率倍率";
            case "watcherPreferVisibleSpawnPositions" -> "Watcher 优先可见生成点";
            case "watcherPossibleLineOfSightWeight" -> "Watcher 可能视线权重";
            case "watcherFrontArcPreference" -> "Watcher 前方角度偏好";
            case "enableTunnelWatcherSpawns" -> "启用 Tunnel Watcher 矿道窥视生成";
            case "tunnelWatcherMinPhase" -> "Tunnel Watcher 最低阶段";
            case "phase3TunnelWatcherMinDistance" -> "三阶段 Tunnel Watcher 最小距离";
            case "phase3TunnelWatcherMaxDistance" -> "三阶段 Tunnel Watcher 最大距离";
            case "phase4TunnelWatcherMinDistance" -> "四阶段 Tunnel Watcher 最小距离";
            case "phase4TunnelWatcherMaxDistance" -> "四阶段 Tunnel Watcher 最大距离";
            case "phase5TunnelWatcherMinDistance" -> "五阶段 Tunnel Watcher 最小距离";
            case "phase5TunnelWatcherMaxDistance" -> "五阶段 Tunnel Watcher 最大距离";
            case "tunnelWatcherSpawnChanceMultiplier" -> "Tunnel Watcher 生成概率倍率";
            case "tunnelWatcherRequiresLowLight" -> "Tunnel Watcher 要求低光照";
            case "tunnelWatcherMaxLightLevel" -> "Tunnel Watcher 最大光照等级";
            case "tunnelWatcherRequirePossibleLineOfSight" -> "Tunnel Watcher 要求可能视线";
            case "tunnelWatcherCooldownSeconds" -> "Tunnel Watcher 冷却秒数";
            case "tunnelWatcherMissCooldownMinSeconds" -> "Tunnel Watcher 未命中最小冷却秒数";
            case "tunnelWatcherMissCooldownMaxSeconds" -> "Tunnel Watcher 未命中最大冷却秒数";
            case "enableTunnelWatcherSpawnSound" -> "启用 Tunnel Watcher 轻提示音";
            case "eventChanceMultiplier" -> "事件概率总倍率";
            case "enableHandDropAnomaly" -> "启用手中物品掉落异常";
            case "handDropMinPhase" -> "手中物品掉落最低阶段";
            case "handDropChanceMultiplier" -> "手中物品掉落概率倍率";
            case "handDropCooldownSeconds" -> "手中物品掉落冷却秒数";
            case "handDropDropWholeStackChance" -> "手中物品掉落整组概率";
            case "handDropPreventNearLava" -> "手中物品掉落避开危险方块";
            case "handDropReceiverDelayTicks" -> "手中物品掉落接收器延迟 ticks";
            case "enableInventoryOpenAnomaly" -> "启用背包打开异常";
            case "inventoryOpenMinPhase" -> "背包打开最低阶段";
            case "inventoryOpenChanceMultiplier" -> "背包打开概率倍率";
            case "inventoryOpenCooldownSeconds" -> "背包打开冷却秒数";
            case "inventoryOpenReceiverDelayTicks" -> "背包打开接收器延迟 ticks";
            case "enableEerieSoundEvents" -> "启用诡异声音事件";
            case "eerieSoundMinPhase" -> "诡异声音最低阶段";
            case "eerieSoundChanceMultiplier" -> "诡异声音概率倍率";
            case "eerieSoundCooldownSeconds" -> "诡异声音冷却秒数";
            case "eerieSoundVolume" -> "诡异声音音量";
            case "eerieSoundReceiverDelayTicks" -> "诡异声音接收器延迟 ticks";
            case "eerieSoundAllowBehindPlayer" -> "诡异声音允许从身后播放";
            case "eerieSoundLaughWeight" -> "诡异声音笑声权重";
            case "caveFootstepChanceMultiplier" -> "洞穴脚步概率倍率";
            case "miningEchoChanceMultiplier" -> "挖掘回声概率倍率";
            case "enableEventWeightBalancing" -> "启用事件权重平衡";
            case "maxWatchingLevel" -> "最大被注视值";
            case "debugMode" -> "调试模式";
            case "enableTestCommands" -> "启用测试指令";
            case "enablePhaseSystem" -> "启用阶段系统";
            case "enableProgressionGates" -> "启用阶段门槛";
            case "phaseProgressionCheckIntervalTicks" -> "阶段推进检查间隔";
            case "phase2MinPlayTimeSeconds" -> "二阶段最短游玩秒数";
            case "phase3MinTimeInPhaseSeconds" -> "三阶段最短停留秒数";
            case "phase4MinTimeInPhaseSeconds" -> "四阶段最短停留秒数";
            case "phase5MinTimeInPhaseSeconds" -> "五阶段最短停留秒数";
            case "phase2RequiredReceiverMessages" -> "二阶段所需接收器信息";
            case "phase2RequiredReceiverOpens" -> "二阶段所需接收器打开次数";
            case "phase3RequiredReceiverMessages" -> "三阶段所需接收器信息";
            case "phase3RequiredReceiverOpens" -> "三阶段所需接收器打开次数";
            case "phase3RequiredSmallEventScore" -> "三阶段所需小事件分数";
            case "phase4RequiredReceiverMessages" -> "四阶段所需接收器信息";
            case "phase4RequiredReceiverOpens" -> "四阶段所需接收器打开次数";
            case "phase4RequiredFakeChatsOrWatcherSightings" -> "四阶段所需假聊天或 Watcher 目击";
            case "phase4RequiredEventScore" -> "四阶段所需事件分数";
            case "phase5RequiredReceiverMessages" -> "五阶段所需接收器信息";
            case "phase5RequiredReceiverOpens" -> "五阶段所需接收器打开次数";
            case "phase5RequiredWatcherSightings" -> "五阶段所需 Watcher 目击";
            case "phase5RequiredEventScore" -> "五阶段所需事件分数";
            case "enableReceiver" -> "启用旧式接收器";
            case "giveReceiverOnFirstJoin" -> "首次进入给予接收器";
            case "receiverKeepAfterDeath" -> "死亡后补发接收器";
            case "enableReceiverMessages" -> "启用接收器信息";
            case "enableReceiverGui" -> "启用接收器菜单";
            case "receiverMessageChanceMultiplier" -> "接收器信息概率倍率";
            case "maxReceiverMessages" -> "接收器最大保存信息";
            case "enableDelayedReceiverMessages" -> "启用接收器延迟记录";
            case "receiverEventMessageMinDelayTicks" -> "接收器事件记录最小延迟 ticks";
            case "receiverEventMessageMaxDelayTicks" -> "接收器事件记录最大延迟 ticks";
            case "receiverCannotBeDropped" -> "接收器不可丢弃";
            case "receiverAutoRestoreIfMissing" -> "接收器缺失自动补发";
            case "enableReceiverPhaseCooldown" -> "启用阶段接收器冷静期";
            case "receiverPhaseCooldownTicks" -> "阶段接收器冷静期 ticks";
            case "enableBroadcastEvents" -> "启用广播事件";
            case "enableFakeSystemErrors" -> "启用假系统错误";
            case "enablePersonalizedSignals" -> "启用个性化信号";
            case "enableWorldAnomalyEvents" -> "启用世界异常";
            case "enableAnimalStareEvents" -> "启用动物凝视";
            case "enableAnimalStareEvent" -> "启用动物注视事件";
            case "animalStareMinAnimals" -> "动物注视最少动物数";
            case "animalStareRadius" -> "动物注视半径";
            case "animalStareHighChance" -> "动物注视高成功概率";
            case "animalStareDurationTicks" -> "动物注视持续 ticks";
            case "animalStareMaxAnimals" -> "动物注视最大动物数";
            case "animalStareIgnoreNamedAnimals" -> "动物注视忽略命名动物";
            case "animalStareReceiverDelayTicks" -> "动物注视接收器延迟 ticks";
            case "enableRandomSignAnomalies" -> "启用随机告示牌异常";
            case "enableNetherrackCrossAnomalies" -> "启用地狱岩十字架异常";
            case "worldAnomalyChanceMultiplier" -> "世界异常概率倍率";
            case "enableNetherSignalEvents" -> "启用 Nether Signal 下界异常";
            case "netherSignalMinPhase" -> "下界接收器信号最低阶段";
            case "netherSignalChanceMultiplier" -> "下界异常概率倍率";
            case "netherReceiverSignalCooldownSeconds" -> "下界接收器信号冷却秒数";
            case "netherPhantomGhastCooldownSeconds" -> "幽灵恶魂哭声冷却秒数";
            case "netherSoulWhisperCooldownSeconds" -> "灵魂沙低语冷却秒数";
            case "netherPortalAnomalyCooldownSeconds" -> "传送门异常冷却秒数";
            case "netherSignalReceiverDelayTicks" -> "下界 Receiver 延迟 ticks";
            case "netherPhantomGhastRequiresNoNearbyGhast" -> "幽灵恶魂要求附近无真实恶魂";
            case "netherPhantomGhastCheckRadius" -> "幽灵恶魂真实恶魂检查半径";
            case "enableReceiverForNetherMinorSounds" -> "下界小声音直接写 Receiver";
            case "netherPortalSearchRadius" -> "传送门异常搜索半径";
            case "netherPortalAnomalyMinPhase" -> "传送门异常最低阶段";
            case "enableNetherPortalDistortion" -> "启用传送门接收器短干扰";
            case "netherPortalDistortionDurationTicks" -> "传送门接收器干扰持续 ticks";
            case "netherPortalDistortionIntensity" -> "传送门接收器干扰强度";
            case "enablePhaseFiveInterference" -> "启用第五阶段干扰";
            case "enablePhaseFiveNoise" -> "启用第五阶段噪音";
            case "enablePhaseFiveScreenFlicker" -> "启用第五阶段屏幕闪烁";
            case "enablePhaseFiveReceiverCorruption" -> "启用接收器视觉失真";
            case "enablePhaseFiveSleepInterference" -> "启用睡觉干扰（Phase 3+，兼容旧配置名）";
            case "enablePhaseFiveFakeJoinMessages" -> "启用第五阶段假玩家消息";
            case "phaseFiveInterferenceCooldownSeconds" -> "第五阶段干扰冷却秒数";
            case "phaseFiveNoiseVolume" -> "第五阶段噪音音量";
            case "phaseFiveFlickerDurationTicks" -> "第五阶段闪烁持续 ticks";
            case "phaseFiveFlickerIntensity" -> "第五阶段闪烁强度";
            case "enableJumpscares" -> "启用 Jumpscare";
            case "jumpscareOnlyInPhaseFive" -> "Jumpscare 仅限第五阶段";
            case "jumpscareChanceMultiplier" -> "Jumpscare 概率倍率";
            case "jumpscareCooldownSeconds" -> "Jumpscare 冷却秒数";
            case "jumpscareRequiresInterferenceEvents" -> "Jumpscare 所需干扰次数";
            case "enableJumpscareOverlay" -> "启用 Jumpscare 覆盖层";
            case "jumpscareOverlayDurationTicks" -> "Jumpscare 覆盖层持续 ticks";
            case "jumpscareDarknessDurationTicks" -> "Jumpscare 黑暗持续 ticks";
            case "enableJumpscareSound" -> "启用 Jumpscare 声音";
            case "jumpscareSoundVolume" -> "Jumpscare 声音音量";
            case "enableJumpscareDarkness" -> "启用 Jumpscare 黑暗效果";
            case "enableFaceScare" -> "启用 FaceScare";
            case "faceScareChanceMultiplier" -> "FaceScare 概率倍率";
            case "faceScareCooldownSeconds" -> "FaceScare 冷却秒数";
            case "faceScareDurationTicks" -> "FaceScare 持续 ticks";
            case "enableChaseEvents" -> "启用追猎事件";
            case "chaseOnlyInPhaseFive" -> "追猎仅限第五阶段";
            case "chaseChanceMultiplier" -> "追猎概率倍率";
            case "chaseCooldownSeconds" -> "追猎冷却秒数";
            case "chaseWarningTicks" -> "追猎预警 ticks";
            case "chaseDurationTicks" -> "追猎持续 ticks";
            case "chaseEntitySpeed" -> "追猎实体速度";
            case "chaseDamage" -> "追猎伤害";
            case "chaseCanKillPlayer" -> "追猎允许致死";
            case "chaseRequiresWatcherSightings" -> "追猎所需 Watcher 目击";
            case "chaseRequiresReceiverMessages" -> "追猎所需接收器信息";
            case "chaseRequiresWatchingLevel" -> "追猎所需被注视值";
            case "chaseEndsNearOtherPlayers" -> "靠近其他玩家结束追猎";
            case "chaseEndsInBrightLight" -> "自然天光结束追猎（不计火把）";
            case "chaseEndsOnSafeSurface" -> "安全地表结束追猎";
            case "chaseLightEscapeLevel" -> "追猎逃脱光照等级";
            case "chaseBrightLightEscapeTicks" -> "强光逃脱持续 ticks";
            case "chaseSafePlayerDistance" -> "队友安全距离";
            case "chaseNearPlayerEscapeTicks" -> "靠近队友逃脱 ticks";
            case "enableChaseReceiverDistanceMessages" -> "启用追猎接收器距离提示";
            case "chaseDistanceMessageIntervalTicks" -> "追猎距离信息间隔 ticks";
            case "enableChaseFootsteps" -> "启用追猎脚步声";
            case "chaseFootstepVolume" -> "追猎脚步声音量";
            case "enableChaseOverlay" -> "启用追猎覆盖层";
            case "enableChaseFaceScareOnCatch" -> "被追猎抓到触发 FaceScare";
            case "chaseFaceScareDurationTicks" -> "追猎 FaceScare 持续 ticks";
            case "chaseRespectReduceFlashingEffects" -> "追猎遵循降低闪烁设置";
            case "enableViewDistanceAnomalies" -> "启用视距异常";
            case "viewDistanceAnomalyChanceMultiplier" -> "视距异常概率倍率";
            case "viewDistanceAnomalyCooldownSeconds" -> "视距异常冷却秒数";
            case "viewDistanceAnomalyDurationTicks" -> "视距异常持续 ticks";
            case "viewDistanceAnomalyChunks" -> "视距异常目标区块";
            case "enableLegacyTextureAnomalies" -> "启用旧版材质异常";
            case "legacyTextureAnomalyChanceMultiplier" -> "旧版材质异常概率倍率";
            case "legacyTextureAnomalyCooldownSeconds" -> "旧版材质异常冷却秒数";
            case "legacyTextureAnomalyDurationTicks" -> "旧版材质异常持续 ticks";
            case "legacyTextureSnowIntensity" -> "旧版材质雪花强度";
            case "reduceFlashingEffects" -> "降低闪烁效果";
            case "disableRapidFlashes" -> "禁用快速闪烁";
            case "maxOverlayOpacity" -> "最大覆盖层不透明度";
            case "enableMonochromeAnomalies" -> "启用黑白画面异常";
            case "monochromeAnomalyChanceMultiplier" -> "黑白画面异常概率倍率";
            case "monochromeAnomalyCooldownSeconds" -> "黑白画面异常冷却秒数";
            case "monochromeAnomalyDurationTicks" -> "黑白画面异常持续 ticks";
            case "monochromeAnomalyIntensity" -> "黑白画面异常强度";
            case "enableFamiliarSoundEvents" -> "启用熟悉行为假声";
            case "familiarSoundMinPhase" -> "熟悉行为假声最低阶段";
            case "familiarSoundChanceMultiplier" -> "熟悉行为假声概率倍率";
            case "familiarSoundCooldownSeconds" -> "熟悉行为假声总冷却秒数";
            case "enableFakeChestSounds" -> "启用假箱子声";
            case "fakeChestSoundRequiresNearbyContainer" -> "假箱子声要求附近容器";
            case "fakeChestSoundRadius" -> "假箱子声容器搜索半径";
            case "fakeChestSoundCooldownSeconds" -> "假箱子声冷却秒数";
            case "enableFakeBlockPlaceSounds" -> "启用假放方块声";
            case "fakeBlockPlaceSoundCooldownSeconds" -> "假放方块声冷却秒数";
            case "enableFakeEatingSounds" -> "启用假吃东西声";
            case "fakeEatingSoundCooldownSeconds" -> "假吃东西声冷却秒数";
            case "fakeEatingSoundBurpChance" -> "假吃东西追加打嗝概率";
            case "familiarSoundReceiverDelayTicks" -> "熟悉行为假声 Receiver 延迟 ticks";
            case "enableSeparationWarning" -> "启用分离警告";
            case "separationWarningMinPhase" -> "分离警告最低阶段";
            case "separationWarningDistance" -> "分离距离";
            case "separationWarningMinTicks" -> "分离持续 ticks";
            case "separationWarningCooldownSeconds" -> "分离警告冷却秒数";
            case "separationWarningNotifyTeammatesChance" -> "分离警告通知队友概率";
            case "enableFakeTeammateFootsteps" -> "启用假队友脚步";
            case "fakeTeammateFootstepsMinPhase" -> "假队友脚步最低阶段";
            case "fakeTeammateFootstepsRequireMultiplayer" -> "假队友脚步要求多人";
            case "fakeTeammateFootstepsCooldownSeconds" -> "假队友脚步冷却秒数";
            case "fakeTeammateFootstepsStepCountMin" -> "假队友脚步最少步数";
            case "fakeTeammateFootstepsStepCountMax" -> "假队友脚步最多步数";
            case "fakeTeammateFootstepsReceiverDelayTicks" -> "假队友脚步 Receiver 延迟 ticks";
            case "enableTeamSynchronizedGaze" -> "启用队伍同步凝视";
            case "teamGazeMinPhase" -> "队伍同步凝视最低阶段";
            case "teamGazePlayerGroupRadius" -> "队伍同步凝视玩家组半径";
            case "teamGazeEntityRadius" -> "队伍同步凝视实体半径";
            case "teamGazeMinEntities" -> "队伍同步凝视最少实体";
            case "teamGazeMaxEntities" -> "队伍同步凝视最多实体";
            case "teamGazeDurationTicks" -> "队伍同步凝视持续 ticks";
            case "teamGazeCooldownSeconds" -> "队伍同步凝视冷却秒数";
            case "teamGazeIncludeVillagers" -> "队伍同步凝视包含村民";
            case "teamGazeIgnoreNamedEntities" -> "队伍同步凝视忽略命名实体";
            case "teamGazeReceiverDelayTicks" -> "队伍同步凝视 Receiver 延迟 ticks";
            case "enableFakeTabListEvent" -> "启用假 Tab 玩家列表";
            case "fakeTabListMinPhase" -> "假 Tab 玩家列表最低阶段";
            case "fakeTabListDurationTicks" -> "假 Tab 玩家列表持续 ticks";
            case "fakeTabListCooldownSeconds" -> "假 Tab 玩家列表冷却秒数";
            case "fakeTabListIntegrateWithFakeJoin" -> "假 Tab 与假加入联动";
            case "fakeTabListUseObfuscatedNameChance" -> "假 Tab 乱码名概率";
            case "enableFakeAdvancementEvent" -> "启用假成就提示";
            case "fakeAdvancementMinPhase" -> "假成就提示最低阶段";
            case "fakeAdvancementCooldownSeconds" -> "假成就提示冷却秒数";
            case "fakeAdvancementUseToastIfPossible" -> "假成就优先使用 toast";
            case "enableFakeRescueMessages" -> "启用假救援信息";
            case "fakeRescueMinPhase" -> "假救援信息最低阶段";
            case "fakeRescueHelpfulChance" -> "假救援帮助信息概率";
            case "fakeRescueCooldownSeconds" -> "假救援信息冷却秒数";
            case "fakeRescueAllowDuringChase" -> "假救援允许追猎中";
            case "fakeRescueAllowDuringCaveStalker" -> "假救援允许 Cave Stalker 中";
            case "enableMimicPlayerEvent" -> "启用伪装玩家事件";
            case "mimicPlayerMinPhase" -> "伪装玩家最低阶段";
            case "mimicPlayerChanceMultiplier" -> "伪装玩家概率倍率";
            case "mimicPlayerCooldownSeconds" -> "伪装玩家冷却秒数";
            case "mimicPlayerDurationTicks" -> "伪装玩家持续 ticks";
            case "mimicPlayerSpawnMinDistance" -> "伪装玩家最小生成距离";
            case "mimicPlayerSpawnMaxDistance" -> "伪装玩家最大生成距离";
            case "mimicPlayerTriggerDistance" -> "伪装玩家触发距离";
            case "mimicPlayerWatcherTransformChance" -> "伪装玩家跳脸概率";
            case "mimicPlayerWatcherLungeTicks" -> "伪装玩家跳脸持续 ticks";
            case "mimicPlayerWatcherLungeDelayTicks" -> "伪装玩家跳脸延迟 ticks";
            case "enableForcedChatEvent" -> "启用强制聊天栏异常";
            case "forcedChatMinPhase" -> "强制聊天栏异常最低阶段";
            case "forcedChatChanceMultiplier" -> "强制聊天栏异常概率倍率";
            case "forcedChatCooldownSeconds" -> "强制聊天栏异常冷却秒数";
            case "forcedChatReceiverDelayTicks" -> "强制聊天栏异常 Receiver 延迟 ticks";
            case "enableAnimalDisguiseEvent" -> "启用伪装动物";
            case "animalDisguiseMinPhase" -> "伪装动物最低阶段";
            case "animalDisguiseCooldownSeconds" -> "伪装动物冷却秒数";
            case "animalDisguiseSpawnChanceMultiplier" -> "伪装动物生成概率倍率";
            case "animalDisguiseSpawnMinDistance" -> "伪装动物最小生成距离";
            case "animalDisguiseSpawnMaxDistance" -> "伪装动物最大生成距离";
            case "animalDisguiseAllowedAnimals" -> "伪装动物允许类型";
            case "enableAnimalDisguiseAggression" -> "启用伪装动物攻击行为";
            case "animalDisguiseAttackNearbyAnimals" -> "伪装动物攻击附近动物";
            case "animalDisguiseAttackNearbyMonsters" -> "伪装动物攻击附近怪物";
            case "animalDisguiseAttackRadius" -> "伪装动物攻击搜索半径";
            case "animalDisguiseAttackDamage" -> "伪装动物攻击伤害";
            case "animalDisguiseAttackCooldownTicks" -> "伪装动物攻击间隔 ticks";
            case "animalDisguiseMaxVictims" -> "伪装动物最大受害动物数";
            case "animalDisguiseMaxAttacks" -> "伪装动物最大攻击次数";
            case "animalDisguiseAvoidNamedAnimals" -> "伪装动物避开命名动物";
            case "animalDisguiseAvoidTamedAnimals" -> "伪装动物避开驯服动物";
            case "animalDisguiseAvoidVillagers" -> "伪装动物避开村民";
            case "animalDisguiseAvoidMounts" -> "伪装动物避开坐骑";
            case "animalDisguiseAggressionReceiverDelayTicks" -> "伪装动物攻击 Receiver 延迟 ticks";
            case "animalDisguiseRetaliationDurationTicks" -> "伪装动物反噬持续 ticks";
            case "animalDisguiseBlindnessSeconds" -> "伪装动物失明秒数";
            case "animalDisguiseNoiseVolume" -> "伪装动物噪音音量";
            case "animalDisguiseStaticIntensity" -> "伪装动物雪花强度";
            case "animalDisguiseReceiverDelayTicks" -> "伪装动物 Receiver 延迟 ticks";
            case "animalDisguiseDespawnSeconds" -> "伪装动物自动清理秒数";
            default -> english;
        };
    }

    private static String formatConfigValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return formatBoolean(booleanValue);
        }

        return String.valueOf(value);
    }

    private static String formatBoolean(boolean value) {
        return value ? "是（true）" : "否（false）";
    }

    private static String formatPhase(HorrorPhase phase) {
        return phase.getNumber() + " / " + switch (phase) {
            case DORMANT -> "沉睡";
            case WATCHING -> "被注视";
            case IMITATING -> "模仿";
            case INTRUSION -> "入侵";
            case MANIFESTATION -> "显现";
        } + "（" + phase.name() + "）";
    }
}
