package com.xm6680.it.client.render;

import com.xm6680.it.ItMod;
import com.xm6680.it.entity.WatcherEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Renders the Watcher as a tall, thin, black player-shaped silhouette.
 */
public class WatcherEntityRenderer extends BipedEntityRenderer<WatcherEntity, BipedEntityRenderState, BipedEntityModel<BipedEntityRenderState>> {
    private static final Identifier TEXTURE = Identifier.of(ItMod.MOD_ID, "textures/entity/watcher.png");

    public WatcherEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.ZOMBIE)), 0.15F);
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
        matrices.scale(0.62F, 1.42F, 0.62F);
    }
}
