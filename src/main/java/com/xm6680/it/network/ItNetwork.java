package com.xm6680.it.network;

import com.xm6680.it.ItMod;
import com.xm6680.it.analog.ReceiverMessage;
import com.xm6680.it.progression.PlayerHorrorData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ItNetwork {
    private static final Map<UUID, Long> MANIFESTATION_OVERLAY_ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> JUMPSCARE_ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> FACE_SCARE_ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> ANIMAL_DISGUISE_RETALIATION_ACTIVE_UNTIL = new HashMap<>();

    private ItNetwork() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(OpenReceiverPayload.ID, OpenReceiverPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReceiverMessagesSyncPayload.ID, ReceiverMessagesSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ManifestationOverlayPayload.ID, ManifestationOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JumpscarePayload.ID, JumpscarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FaceScarePayload.ID, FaceScarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MimicFaceScarePayload.ID, MimicFaceScarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChaseOverlayPayload.ID, ChaseOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChaseReceiverSignalPayload.ID, ChaseReceiverSignalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChaseDistanceHintPayload.ID, ChaseDistanceHintPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HuntFaceScarePayload.ID, HuntFaceScarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CaveStalkerOverlayPayload.ID, CaveStalkerOverlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CaveStalkerFaceScarePayload.ID, CaveStalkerFaceScarePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FirstPersonLockPayload.ID, FirstPersonLockPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ViewDistancePayload.ID, ViewDistancePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LegacyTexturePayload.ID, LegacyTexturePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MonochromePayload.ID, MonochromePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReceiverDistortionPayload.ID, ReceiverDistortionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenInventoryPayload.ID, OpenInventoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ForcedChatPayload.ID, ForcedChatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FakeAdvancementPayload.ID, FakeAdvancementPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnimalDisguiseRetaliationPayload.ID, AnimalDisguiseRetaliationPayload.CODEC);
    }

    public static void sendReceiverScreen(ServerPlayerEntity player, PlayerHorrorData data, List<ReceiverMessage> messages) {
        List<ReceiverMessagePayload> payloadMessages = receiverMessagePayloads(messages);

        OpenReceiverPayload payload = new OpenReceiverPayload(
                data.currentPhase.getNumber(),
                data.currentPhase.getDisplayName(),
                data.watchingLevel,
                signalStrengthBars(player),
                payloadMessages
        );

        if (ServerPlayNetworking.canSend(player, OpenReceiverPayload.ID)) {
            ServerPlayNetworking.send(player, payload);
            return;
        }

        if (messages.isEmpty()) {
            player.sendMessage(Text.literal("没有接收到信号"), false);
        } else {
            for (ReceiverMessage message : messages) {
                player.sendMessage(Text.literal(message.formattedText()), false);
            }
        }
    }

    public static void sendReceiverMessagesSync(ServerPlayerEntity player, PlayerHorrorData data, List<ReceiverMessage> messages) {
        ReceiverMessagesSyncPayload payload = new ReceiverMessagesSyncPayload(
                data.currentPhase.getNumber(),
                data.currentPhase.getDisplayName(),
                data.watchingLevel,
                signalStrengthBars(player),
                receiverMessagePayloads(messages)
        );
        if (ServerPlayNetworking.canSend(player, ReceiverMessagesSyncPayload.ID)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static List<ReceiverMessagePayload> receiverMessagePayloads(List<ReceiverMessage> messages) {
        List<ReceiverMessagePayload> payloadMessages = new ArrayList<>();
        for (ReceiverMessage message : messages) {
            payloadMessages.add(new ReceiverMessagePayload(
                    message.type().ordinal(),
                    message.phase().getNumber(),
                    message.receivedEpochSecond(),
                    message.formattedText()
            ));
        }
        return payloadMessages;
    }

    private static int signalStrengthBars(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        int seaLevel = world.getSeaLevel();
        int y = pos.getY();
        if (world.isSkyVisible(pos.up())) {
            return 5;
        }

        if (y >= seaLevel) {
            return 4;
        }

        int depth = seaLevel - y;
        if (depth < 16) {
            return 4;
        }
        if (depth < 32) {
            return 3;
        }
        if (depth < 48) {
            return 2;
        }
        return 1;
    }

    public static void sendManifestationOverlay(ServerPlayerEntity player, int durationTicks, float intensity, boolean reduceFlashes) {
        markActive(MANIFESTATION_OVERLAY_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, ManifestationOverlayPayload.ID)) {
            ServerPlayNetworking.send(player, new ManifestationOverlayPayload(durationTicks, intensity, reduceFlashes));
        }
    }

    public static void sendJumpscare(ServerPlayerEntity player, int durationTicks, float intensity, boolean playSound, float soundVolume) {
        markActive(JUMPSCARE_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, JumpscarePayload.ID)) {
            ServerPlayNetworking.send(player, new JumpscarePayload(durationTicks, intensity, playSound, soundVolume));
        }
    }

    public static void sendFaceScare(ServerPlayerEntity player, int durationTicks, float intensity, boolean playSound) {
        markActive(FACE_SCARE_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, FaceScarePayload.ID)) {
            ServerPlayNetworking.send(player, new FaceScarePayload(durationTicks, intensity, playSound));
        }
    }

    public static void sendMimicFaceScare(ServerPlayerEntity player, int durationTicks, float intensity, boolean playSound) {
        markActive(FACE_SCARE_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, MimicFaceScarePayload.ID)) {
            ServerPlayNetworking.send(player, new MimicFaceScarePayload(durationTicks, intensity, playSound));
            return;
        }

        sendFaceScare(player, durationTicks, intensity, playSound);
    }

    public static void sendHuntFaceScare(ServerPlayerEntity player, int durationTicks, float intensity, boolean playSound) {
        markActive(FACE_SCARE_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, HuntFaceScarePayload.ID)) {
            ServerPlayNetworking.send(player, new HuntFaceScarePayload(durationTicks, intensity, playSound));
            return;
        }

        sendFaceScare(player, durationTicks, intensity, playSound);
    }

    public static void sendCaveStalkerOverlay(ServerPlayerEntity player, boolean active, int durationTicks, float intensity) {
        if (ServerPlayNetworking.canSend(player, CaveStalkerOverlayPayload.ID)) {
            ServerPlayNetworking.send(player, new CaveStalkerOverlayPayload(active, durationTicks, intensity));
        }
    }

    public static void sendCaveStalkerFaceScare(ServerPlayerEntity player, int durationTicks, float intensity, boolean playSound) {
        markActive(FACE_SCARE_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, CaveStalkerFaceScarePayload.ID)) {
            ServerPlayNetworking.send(player, new CaveStalkerFaceScarePayload(durationTicks, intensity, playSound));
            return;
        }

        sendHuntFaceScare(player, durationTicks, intensity, playSound);
    }

    public static void sendFirstPersonLock(ServerPlayerEntity player, boolean active, int durationTicks) {
        if (ServerPlayNetworking.canSend(player, FirstPersonLockPayload.ID)) {
            ServerPlayNetworking.send(player, new FirstPersonLockPayload(active, durationTicks));
        }
    }

    public static void sendChaseOverlay(ServerPlayerEntity player, boolean active, int durationTicks, float intensity) {
        if (ServerPlayNetworking.canSend(player, ChaseOverlayPayload.ID)) {
            ServerPlayNetworking.send(player, new ChaseOverlayPayload(active, durationTicks, intensity));
        }
    }

    public static void sendChaseReceiverSignal(ServerPlayerEntity player, boolean active, int durationTicks) {
        if (ServerPlayNetworking.canSend(player, ChaseReceiverSignalPayload.ID)) {
            ServerPlayNetworking.send(player, new ChaseReceiverSignalPayload(active, durationTicks));
        }
    }

    public static void sendChaseDistanceHint(ServerPlayerEntity player, String text, int color, int durationTicks) {
        if (ServerPlayNetworking.canSend(player, ChaseDistanceHintPayload.ID)) {
            ServerPlayNetworking.send(player, new ChaseDistanceHintPayload(text, color, durationTicks));
        }
    }

    public static void sendViewDistanceAnomaly(ServerPlayerEntity player, int chunks, int durationTicks) {
        if (ServerPlayNetworking.canSend(player, ViewDistancePayload.ID)) {
            ServerPlayNetworking.send(player, new ViewDistancePayload(chunks, durationTicks));
        }
    }

    public static void sendLegacyTextureAnomaly(ServerPlayerEntity player, int durationTicks, float intensity) {
        if (ServerPlayNetworking.canSend(player, LegacyTexturePayload.ID)) {
            ServerPlayNetworking.send(player, new LegacyTexturePayload(durationTicks, intensity));
        }
    }

    public static void sendMonochromeAnomaly(ServerPlayerEntity player, int durationTicks, float intensity) {
        if (ServerPlayNetworking.canSend(player, MonochromePayload.ID)) {
            ServerPlayNetworking.send(player, new MonochromePayload(durationTicks, intensity));
        }
    }

    public static void sendReceiverDistortion(ServerPlayerEntity player, int durationTicks, float intensity) {
        if (ServerPlayNetworking.canSend(player, ReceiverDistortionPayload.ID)) {
            ServerPlayNetworking.send(player, new ReceiverDistortionPayload(durationTicks, intensity));
        }
    }

    public static void sendOpenInventory(ServerPlayerEntity player) {
        if (ServerPlayNetworking.canSend(player, OpenInventoryPayload.ID)) {
            ServerPlayNetworking.send(player, new OpenInventoryPayload());
        }
    }

    public static void sendForcedChat(ServerPlayerEntity player, List<String> lines, int charIntervalTicks, int linePauseTicks, int closeDelayTicks) {
        if (ServerPlayNetworking.canSend(player, ForcedChatPayload.ID)) {
            ServerPlayNetworking.send(player, new ForcedChatPayload(lines, charIntervalTicks, linePauseTicks, closeDelayTicks));
        }
    }

    public static void sendFakeAdvancement(ServerPlayerEntity player, String title, String description, boolean useToast) {
        if (ServerPlayNetworking.canSend(player, FakeAdvancementPayload.ID)) {
            ServerPlayNetworking.send(player, new FakeAdvancementPayload(title, description, useToast));
            return;
        }

        player.sendMessage(Text.literal(title + "【" + description + "】"), false);
    }

    public static void sendAnimalDisguiseRetaliation(ServerPlayerEntity player, int durationTicks, float intensity, float noiseVolume, boolean reduceFlashes) {
        markActive(ANIMAL_DISGUISE_RETALIATION_ACTIVE_UNTIL, player, durationTicks);
        if (ServerPlayNetworking.canSend(player, AnimalDisguiseRetaliationPayload.ID)) {
            ServerPlayNetworking.send(player, new AnimalDisguiseRetaliationPayload(durationTicks, intensity, noiseVolume, reduceFlashes));
        }
    }

    public static boolean isManifestationOverlayActive(ServerPlayerEntity player, long currentTick) {
        return isActive(MANIFESTATION_OVERLAY_ACTIVE_UNTIL, player, currentTick);
    }

    public static boolean isJumpscareActive(ServerPlayerEntity player, long currentTick) {
        return isActive(JUMPSCARE_ACTIVE_UNTIL, player, currentTick);
    }

    public static boolean isFaceScareActive(ServerPlayerEntity player, long currentTick) {
        return isActive(FACE_SCARE_ACTIVE_UNTIL, player, currentTick);
    }

    public static boolean isAnimalDisguiseRetaliationActive(ServerPlayerEntity player, long currentTick) {
        return isActive(ANIMAL_DISGUISE_RETALIATION_ACTIVE_UNTIL, player, currentTick);
    }

    private static void markActive(Map<UUID, Long> activeUntil, ServerPlayerEntity player, int durationTicks) {
        long currentTick = player.getEntityWorld().getServer().getTicks();
        activeUntil.put(player.getUuid(), currentTick + Math.max(1, durationTicks));
    }

    private static boolean isActive(Map<UUID, Long> activeUntil, ServerPlayerEntity player, long currentTick) {
        UUID uuid = player.getUuid();
        long until = activeUntil.getOrDefault(uuid, 0L);
        if (currentTick < until) {
            return true;
        }

        activeUntil.remove(uuid);
        return false;
    }

    public record ReceiverMessagePayload(int typeOrdinal, int phaseNumber, long receivedEpochSecond, String text) {
        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(typeOrdinal);
            buf.writeVarInt(phaseNumber);
            buf.writeLong(receivedEpochSecond);
            buf.writeString(text, 512);
        }

        private static ReceiverMessagePayload read(RegistryByteBuf buf) {
            return new ReceiverMessagePayload(buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readString(512));
        }
    }

    public record OpenReceiverPayload(int phaseNumber, String phaseName, int watchingLevel, int signalStrengthBars, List<ReceiverMessagePayload> messages) implements CustomPayload {
        public static final CustomPayload.Id<OpenReceiverPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "open_receiver"));
        public static final PacketCodec<RegistryByteBuf, OpenReceiverPayload> CODEC = PacketCodec.of(OpenReceiverPayload::write, OpenReceiverPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(phaseNumber);
            buf.writeString(phaseName, 64);
            buf.writeVarInt(watchingLevel);
            buf.writeVarInt(signalStrengthBars);
            buf.writeVarInt(Math.min(messages.size(), 20));
            for (int i = 0; i < Math.min(messages.size(), 20); i++) {
                messages.get(i).write(buf);
            }
        }

        private static OpenReceiverPayload read(RegistryByteBuf buf) {
            int phaseNumber = buf.readVarInt();
            String phaseName = buf.readString(64);
            int watchingLevel = buf.readVarInt();
            int signalStrengthBars = buf.readVarInt();
            int size = Math.min(buf.readVarInt(), 20);
            List<ReceiverMessagePayload> messages = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                messages.add(ReceiverMessagePayload.read(buf));
            }

            return new OpenReceiverPayload(phaseNumber, phaseName, watchingLevel, signalStrengthBars, messages);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ReceiverMessagesSyncPayload(int phaseNumber, String phaseName, int watchingLevel, int signalStrengthBars, List<ReceiverMessagePayload> messages) implements CustomPayload {
        public static final CustomPayload.Id<ReceiverMessagesSyncPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "receiver_messages_sync"));
        public static final PacketCodec<RegistryByteBuf, ReceiverMessagesSyncPayload> CODEC = PacketCodec.of(ReceiverMessagesSyncPayload::write, ReceiverMessagesSyncPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(phaseNumber);
            buf.writeString(phaseName, 64);
            buf.writeVarInt(watchingLevel);
            buf.writeVarInt(signalStrengthBars);
            buf.writeVarInt(Math.min(messages.size(), 20));
            for (int i = 0; i < Math.min(messages.size(), 20); i++) {
                messages.get(i).write(buf);
            }
        }

        private static ReceiverMessagesSyncPayload read(RegistryByteBuf buf) {
            int phaseNumber = buf.readVarInt();
            String phaseName = buf.readString(64);
            int watchingLevel = buf.readVarInt();
            int signalStrengthBars = buf.readVarInt();
            int size = Math.min(buf.readVarInt(), 20);
            List<ReceiverMessagePayload> messages = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                messages.add(ReceiverMessagePayload.read(buf));
            }

            return new ReceiverMessagesSyncPayload(phaseNumber, phaseName, watchingLevel, signalStrengthBars, messages);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ManifestationOverlayPayload(int durationTicks, float intensity, boolean reduceFlashes) implements CustomPayload {
        public static final CustomPayload.Id<ManifestationOverlayPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "manifestation_overlay"));
        public static final PacketCodec<RegistryByteBuf, ManifestationOverlayPayload> CODEC = PacketCodec.of(ManifestationOverlayPayload::write, ManifestationOverlayPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeBoolean(reduceFlashes);
        }

        private static ManifestationOverlayPayload read(RegistryByteBuf buf) {
            return new ManifestationOverlayPayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record JumpscarePayload(int durationTicks, float intensity, boolean playSound, float soundVolume) implements CustomPayload {
        public static final CustomPayload.Id<JumpscarePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "jumpscare"));
        public static final PacketCodec<RegistryByteBuf, JumpscarePayload> CODEC = PacketCodec.of(JumpscarePayload::write, JumpscarePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeBoolean(playSound);
            buf.writeFloat(soundVolume);
        }

        private static JumpscarePayload read(RegistryByteBuf buf) {
            return new JumpscarePayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readFloat());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FaceScarePayload(int durationTicks, float intensity, boolean playSound) implements CustomPayload {
        public static final CustomPayload.Id<FaceScarePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "face_scare"));
        public static final PacketCodec<RegistryByteBuf, FaceScarePayload> CODEC = PacketCodec.of(FaceScarePayload::write, FaceScarePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeBoolean(playSound);
        }

        private static FaceScarePayload read(RegistryByteBuf buf) {
            return new FaceScarePayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MimicFaceScarePayload(int durationTicks, float intensity, boolean playSound) implements CustomPayload {
        public static final CustomPayload.Id<MimicFaceScarePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "mimic_face_scare"));
        public static final PacketCodec<RegistryByteBuf, MimicFaceScarePayload> CODEC = PacketCodec.of(MimicFaceScarePayload::write, MimicFaceScarePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeBoolean(playSound);
        }

        private static MimicFaceScarePayload read(RegistryByteBuf buf) {
            return new MimicFaceScarePayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChaseOverlayPayload(boolean active, int durationTicks, float intensity) implements CustomPayload {
        public static final CustomPayload.Id<ChaseOverlayPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "chase_overlay"));
        public static final PacketCodec<RegistryByteBuf, ChaseOverlayPayload> CODEC = PacketCodec.of(ChaseOverlayPayload::write, ChaseOverlayPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
        }

        private static ChaseOverlayPayload read(RegistryByteBuf buf) {
            return new ChaseOverlayPayload(buf.readBoolean(), buf.readVarInt(), buf.readFloat());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChaseReceiverSignalPayload(boolean active, int durationTicks) implements CustomPayload {
        public static final CustomPayload.Id<ChaseReceiverSignalPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "chase_receiver_signal"));
        public static final PacketCodec<RegistryByteBuf, ChaseReceiverSignalPayload> CODEC = PacketCodec.of(ChaseReceiverSignalPayload::write, ChaseReceiverSignalPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarInt(durationTicks);
        }

        private static ChaseReceiverSignalPayload read(RegistryByteBuf buf) {
            return new ChaseReceiverSignalPayload(buf.readBoolean(), buf.readVarInt());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ChaseDistanceHintPayload(String text, int color, int durationTicks) implements CustomPayload {
        public static final CustomPayload.Id<ChaseDistanceHintPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "chase_distance_hint"));
        public static final PacketCodec<RegistryByteBuf, ChaseDistanceHintPayload> CODEC = PacketCodec.of(ChaseDistanceHintPayload::write, ChaseDistanceHintPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeString(text, 64);
            buf.writeInt(color);
            buf.writeVarInt(durationTicks);
        }

        private static ChaseDistanceHintPayload read(RegistryByteBuf buf) {
            return new ChaseDistanceHintPayload(buf.readString(64), buf.readInt(), buf.readVarInt());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record HuntFaceScarePayload(int durationTicks, float intensity, boolean playSound) implements CustomPayload {
        public static final CustomPayload.Id<HuntFaceScarePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "hunt_face_scare"));
        public static final PacketCodec<RegistryByteBuf, HuntFaceScarePayload> CODEC = PacketCodec.of(HuntFaceScarePayload::write, HuntFaceScarePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeBoolean(playSound);
        }

        private static HuntFaceScarePayload read(RegistryByteBuf buf) {
            return new HuntFaceScarePayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record CaveStalkerOverlayPayload(boolean active, int durationTicks, float intensity) implements CustomPayload {
        public static final CustomPayload.Id<CaveStalkerOverlayPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "cave_stalker_overlay"));
        public static final PacketCodec<RegistryByteBuf, CaveStalkerOverlayPayload> CODEC = PacketCodec.of(CaveStalkerOverlayPayload::write, CaveStalkerOverlayPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
        }

        private static CaveStalkerOverlayPayload read(RegistryByteBuf buf) {
            return new CaveStalkerOverlayPayload(buf.readBoolean(), buf.readVarInt(), buf.readFloat());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record CaveStalkerFaceScarePayload(int durationTicks, float intensity, boolean playSound) implements CustomPayload {
        public static final CustomPayload.Id<CaveStalkerFaceScarePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "cave_stalker_face_scare"));
        public static final PacketCodec<RegistryByteBuf, CaveStalkerFaceScarePayload> CODEC = PacketCodec.of(CaveStalkerFaceScarePayload::write, CaveStalkerFaceScarePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeBoolean(playSound);
        }

        private static CaveStalkerFaceScarePayload read(RegistryByteBuf buf) {
            return new CaveStalkerFaceScarePayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FirstPersonLockPayload(boolean active, int durationTicks) implements CustomPayload {
        public static final CustomPayload.Id<FirstPersonLockPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "first_person_lock"));
        public static final PacketCodec<RegistryByteBuf, FirstPersonLockPayload> CODEC = PacketCodec.of(FirstPersonLockPayload::write, FirstPersonLockPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeVarInt(durationTicks);
        }

        private static FirstPersonLockPayload read(RegistryByteBuf buf) {
            return new FirstPersonLockPayload(buf.readBoolean(), buf.readVarInt());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ViewDistancePayload(int chunks, int durationTicks) implements CustomPayload {
        public static final CustomPayload.Id<ViewDistancePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "view_distance_anomaly"));
        public static final PacketCodec<RegistryByteBuf, ViewDistancePayload> CODEC = PacketCodec.of(ViewDistancePayload::write, ViewDistancePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(chunks);
            buf.writeVarInt(durationTicks);
        }

        private static ViewDistancePayload read(RegistryByteBuf buf) {
            return new ViewDistancePayload(buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record LegacyTexturePayload(int durationTicks, float intensity) implements CustomPayload {
        public static final CustomPayload.Id<LegacyTexturePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "legacy_texture_anomaly"));
        public static final PacketCodec<RegistryByteBuf, LegacyTexturePayload> CODEC = PacketCodec.of(LegacyTexturePayload::write, LegacyTexturePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
        }

        private static LegacyTexturePayload read(RegistryByteBuf buf) {
            return new LegacyTexturePayload(buf.readVarInt(), buf.readFloat());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record MonochromePayload(int durationTicks, float intensity) implements CustomPayload {
        public static final CustomPayload.Id<MonochromePayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "monochrome_anomaly"));
        public static final PacketCodec<RegistryByteBuf, MonochromePayload> CODEC = PacketCodec.of(MonochromePayload::write, MonochromePayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
        }

        private static MonochromePayload read(RegistryByteBuf buf) {
            return new MonochromePayload(buf.readVarInt(), buf.readFloat());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ReceiverDistortionPayload(int durationTicks, float intensity) implements CustomPayload {
        public static final CustomPayload.Id<ReceiverDistortionPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "receiver_distortion"));
        public static final PacketCodec<RegistryByteBuf, ReceiverDistortionPayload> CODEC = PacketCodec.of(ReceiverDistortionPayload::write, ReceiverDistortionPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
        }

        private static ReceiverDistortionPayload read(RegistryByteBuf buf) {
            return new ReceiverDistortionPayload(buf.readVarInt(), buf.readFloat());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record OpenInventoryPayload() implements CustomPayload {
        public static final CustomPayload.Id<OpenInventoryPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "open_inventory"));
        public static final PacketCodec<RegistryByteBuf, OpenInventoryPayload> CODEC = PacketCodec.of(OpenInventoryPayload::write, OpenInventoryPayload::read);

        private void write(RegistryByteBuf buf) {
        }

        private static OpenInventoryPayload read(RegistryByteBuf buf) {
            return new OpenInventoryPayload();
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ForcedChatPayload(List<String> lines, int charIntervalTicks, int linePauseTicks, int closeDelayTicks) implements CustomPayload {
        public static final CustomPayload.Id<ForcedChatPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "forced_chat"));
        public static final PacketCodec<RegistryByteBuf, ForcedChatPayload> CODEC = PacketCodec.of(ForcedChatPayload::write, ForcedChatPayload::read);

        private void write(RegistryByteBuf buf) {
            int count = Math.min(lines.size(), 8);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                buf.writeString(lines.get(i), 160);
            }
            buf.writeVarInt(charIntervalTicks);
            buf.writeVarInt(linePauseTicks);
            buf.writeVarInt(closeDelayTicks);
        }

        private static ForcedChatPayload read(RegistryByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 8);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                lines.add(buf.readString(160));
            }
            return new ForcedChatPayload(lines, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FakeAdvancementPayload(String title, String description, boolean useToast) implements CustomPayload {
        public static final CustomPayload.Id<FakeAdvancementPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "fake_advancement"));
        public static final PacketCodec<RegistryByteBuf, FakeAdvancementPayload> CODEC = PacketCodec.of(FakeAdvancementPayload::write, FakeAdvancementPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeString(title, 128);
            buf.writeString(description, 128);
            buf.writeBoolean(useToast);
        }

        private static FakeAdvancementPayload read(RegistryByteBuf buf) {
            return new FakeAdvancementPayload(buf.readString(128), buf.readString(128), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AnimalDisguiseRetaliationPayload(int durationTicks, float intensity, float noiseVolume, boolean reduceFlashes) implements CustomPayload {
        public static final CustomPayload.Id<AnimalDisguiseRetaliationPayload> ID = new CustomPayload.Id<>(Identifier.of(ItMod.MOD_ID, "animal_disguise_retaliation"));
        public static final PacketCodec<RegistryByteBuf, AnimalDisguiseRetaliationPayload> CODEC = PacketCodec.of(AnimalDisguiseRetaliationPayload::write, AnimalDisguiseRetaliationPayload::read);

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(durationTicks);
            buf.writeFloat(intensity);
            buf.writeFloat(noiseVolume);
            buf.writeBoolean(reduceFlashes);
        }

        private static AnimalDisguiseRetaliationPayload read(RegistryByteBuf buf) {
            return new AnimalDisguiseRetaliationPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
