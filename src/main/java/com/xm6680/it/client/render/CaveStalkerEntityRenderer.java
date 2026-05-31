package com.xm6680.it.client.render;

import com.xm6680.it.ItMod;
import com.xm6680.it.client.render.model.HuntingWatcherEntityModel;
import com.xm6680.it.entity.CaveStalkerEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class CaveStalkerEntityRenderer extends BipedEntityRenderer<CaveStalkerEntity, BipedEntityRenderState, HuntingWatcherEntityModel> {
    private static final Identifier TEXTURE = Identifier.of(ItMod.MOD_ID, "textures/entity/hunting_watcher.png");

    public CaveStalkerEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new HuntingWatcherEntityModel(context.getPart(EntityModelLayers.ZOMBIE)), 0.28F);
        addFeature(new CaveStalkerEyesFeatureRenderer(this));
    }

    @Override
    public BipedEntityRenderState createRenderState() {
        return new BipedEntityRenderState();
    }

    @Override
    public Identifier getTexture(BipedEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(BipedEntityRenderState state, MatrixStack matrices) {
        matrices.scale(0.76F, 1.42F, 0.66F);
    }
}
