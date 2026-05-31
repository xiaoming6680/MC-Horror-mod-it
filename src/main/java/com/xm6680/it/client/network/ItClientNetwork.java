package com.xm6680.it.client.network;

import com.xm6680.it.client.chase.ChaseOverlayRenderer;
import com.xm6680.it.client.chase.HuntFaceScareOverlayRenderer;
import com.xm6680.it.client.cavestalker.CaveStalkerFaceScareOverlayRenderer;
import com.xm6680.it.client.cavestalker.CaveStalkerOverlayRenderer;
import com.xm6680.it.client.effect.ClientViewDistanceEffect;
import com.xm6680.it.client.effect.AnimalDisguiseStaticOverlayRenderer;
import com.xm6680.it.client.effect.FirstPersonLockManager;
import com.xm6680.it.client.effect.LegacyTextureOverlayRenderer;
import com.xm6680.it.client.effect.MonochromeOverlayRenderer;
import com.xm6680.it.client.effect.ReceiverDistortionOverlayRenderer;
import com.xm6680.it.client.gui.ForcedChatScreen;
import com.xm6680.it.client.gui.ReceiverScreen;
import com.xm6680.it.client.jumpscare.FaceScareOverlayRenderer;
import com.xm6680.it.client.jumpscare.JumpscareOverlayRenderer;
import com.xm6680.it.client.jumpscare.MimicFaceScareOverlayRenderer;
import com.xm6680.it.client.render.ManifestationOverlayRenderer;
import com.xm6680.it.network.ItNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

public final class ItClientNetwork {
    private ItClientNetwork() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.OpenReceiverPayload.ID, (payload, context) -> ReceiverScreen.open(payload));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ReceiverMessagesSyncPayload.ID, (payload, context) -> ReceiverScreen.sync(payload));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ManifestationOverlayPayload.ID, (payload, context) ->
                ManifestationOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.reduceFlashes()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.JumpscarePayload.ID, (payload, context) ->
                JumpscareOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.playSound(), payload.soundVolume()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.FaceScarePayload.ID, (payload, context) ->
                FaceScareOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.playSound()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.MimicFaceScarePayload.ID, (payload, context) ->
                MimicFaceScareOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.playSound()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.HuntFaceScarePayload.ID, (payload, context) ->
                HuntFaceScareOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.playSound()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.CaveStalkerFaceScarePayload.ID, (payload, context) ->
                CaveStalkerFaceScareOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.playSound()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.FirstPersonLockPayload.ID, (payload, context) -> {
            if (payload.active()) {
                FirstPersonLockManager.start(payload.durationTicks());
            } else {
                FirstPersonLockManager.stop();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.CaveStalkerOverlayPayload.ID, (payload, context) -> {
            if (payload.active()) {
                CaveStalkerOverlayRenderer.start(payload.durationTicks(), payload.intensity());
            } else {
                CaveStalkerOverlayRenderer.stop();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ChaseOverlayPayload.ID, (payload, context) -> {
            if (payload.active()) {
                ChaseOverlayRenderer.start(payload.durationTicks(), payload.intensity());
            } else {
                ChaseOverlayRenderer.stop();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ChaseReceiverSignalPayload.ID, (payload, context) -> {
            if (payload.active()) {
                ChaseOverlayRenderer.startReceiverSignal(payload.durationTicks());
            } else {
                ChaseOverlayRenderer.stopReceiverSignal();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ChaseDistanceHintPayload.ID, (payload, context) ->
                ChaseOverlayRenderer.showDistanceHint(payload.text(), payload.color(), payload.durationTicks()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ViewDistancePayload.ID, (payload, context) ->
                ClientViewDistanceEffect.start(payload.chunks(), payload.durationTicks()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.LegacyTexturePayload.ID, (payload, context) ->
                LegacyTextureOverlayRenderer.start(payload.durationTicks(), payload.intensity()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.MonochromePayload.ID, (payload, context) ->
                MonochromeOverlayRenderer.start(payload.durationTicks(), payload.intensity()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ReceiverDistortionPayload.ID, (payload, context) ->
                ReceiverDistortionOverlayRenderer.start(payload.durationTicks(), payload.intensity()));
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.OpenInventoryPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new InventoryScreen(client.player));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.ForcedChatPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player != null) {
                    client.setScreen(new ForcedChatScreen(
                            payload.lines(),
                            payload.charIntervalTicks(),
                            payload.linePauseTicks(),
                            payload.closeDelayTicks()
                    ));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.FakeAdvancementPayload.ID, (payload, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                Text title = Text.literal(payload.title());
                Text description = Text.literal("【" + payload.description() + "】");
                if (payload.useToast()) {
                    SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, title, description);
                } else if (client.inGameHud != null) {
                    client.inGameHud.getChatHud().addMessage(Text.literal(payload.title() + "【" + payload.description() + "】"));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ItNetwork.AnimalDisguiseRetaliationPayload.ID, (payload, context) ->
                AnimalDisguiseStaticOverlayRenderer.start(payload.durationTicks(), payload.intensity(), payload.reduceFlashes()));
    }
}
