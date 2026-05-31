package com.xm6680.it.client.render;

import com.xm6680.it.ItMod;
import com.xm6680.it.client.render.model.HuntingWatcherEntityModel;
import com.xm6680.it.entity.HuntingWatcherEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renders the temporary hunt manifestation with a separate distorted biped pose.
 */
public class HuntingWatcherEntityRenderer extends BipedEntityRenderer<HuntingWatcherEntity, BipedEntityRenderState, HuntingWatcherEntityModel> {
    private static final Identifier TEXTURE = Identifier.of(ItMod.MOD_ID, "textures/entity/hunting_watcher.png");

    public HuntingWatcherEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new HuntingWatcherEntityModel(context.getPart(EntityModelLayers.ZOMBIE)), 0.32F);
        addFeature(new HuntingWatcherEyesFeatureRenderer(this));
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
        matrices.scale(0.78F, 1.68F, 0.68F);
    }
}
