package com.xm6680.it.client.render;

import com.xm6680.it.ItMod;
import com.xm6680.it.client.render.model.HuntingWatcherEntityModel;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.Identifier;

public class CaveStalkerEyesFeatureRenderer extends EyesFeatureRenderer<BipedEntityRenderState, HuntingWatcherEntityModel> {
    private static final RenderLayer EYES = RenderLayers.eyes(Identifier.of(ItMod.MOD_ID, "textures/entity/cave_stalker_eyes.png"));

    public CaveStalkerEyesFeatureRenderer(FeatureRendererContext<BipedEntityRenderState, HuntingWatcherEntityModel> context) {
        super(context);
    }

    @Override
    public RenderLayer getEyesTexture() {
        return EYES;
    }
}
