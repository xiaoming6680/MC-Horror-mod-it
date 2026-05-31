package com.xm6680.it.client;

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
import com.xm6680.it.client.jumpscare.JumpscareOverlayRenderer;
import com.xm6680.it.client.jumpscare.FaceScareOverlayRenderer;
import com.xm6680.it.client.jumpscare.MimicFaceScareOverlayRenderer;
import com.xm6680.it.client.network.ItClientNetwork;
import com.xm6680.it.client.render.CaveStalkerEntityRenderer;
import com.xm6680.it.client.render.HuntingWatcherEntityRenderer;
import com.xm6680.it.client.render.ManifestationOverlayRenderer;
import com.xm6680.it.client.render.WatcherEntityRenderer;
import com.xm6680.it.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client entry point for render-only registrations.
 */
public class ItModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.WATCHER, WatcherEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.HUNTING_WATCHER, HuntingWatcherEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CAVE_STALKER, CaveStalkerEntityRenderer::new);
        ItClientNetwork.registerReceivers();
        ManifestationOverlayRenderer.register();
        ChaseOverlayRenderer.register();
        CaveStalkerOverlayRenderer.register();
        HuntFaceScareOverlayRenderer.register();
        CaveStalkerFaceScareOverlayRenderer.register();
        ClientViewDistanceEffect.register();
        LegacyTextureOverlayRenderer.register();
        MonochromeOverlayRenderer.register();
        ReceiverDistortionOverlayRenderer.register();
        AnimalDisguiseStaticOverlayRenderer.register();
        FirstPersonLockManager.register();
        JumpscareOverlayRenderer.register();
        FaceScareOverlayRenderer.register();
        MimicFaceScareOverlayRenderer.register();
    }
}
