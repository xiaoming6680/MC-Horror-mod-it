package com.xm6680.it.client.render.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;

/**
 * Slightly distorted biped pose for the hunt manifestation.
 */
public class HuntingWatcherEntityModel extends BipedEntityModel<BipedEntityRenderState> {
    public HuntingWatcherEntityModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setAngles(BipedEntityRenderState state) {
        super.setAngles(state);
        head.pitch -= 0.38F;
        head.roll += 0.08F;
        body.pitch = 0.16F;
        rightArm.pitch = -1.35F;
        rightArm.roll = 0.42F;
        leftArm.pitch = -1.25F;
        leftArm.roll = -0.42F;
        rightLeg.pitch -= 0.16F;
        leftLeg.pitch += 0.16F;
    }
}
