package com.terrainlab.mixin;

import com.terrainlab.client.VillagerMoodClient;
import com.terrainlab.village.VillagerGestureAccess;
import com.terrainlab.village.VillagerMoodService;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a posture for a villager's most pressing discomfort ({@link VillagerMoodService#primary}) after
 * vanilla's {@code setupAnim}, using the mood mask copied onto the render state. Vanilla resets the
 * parts each frame, so offsets don't accumulate. Skipped while walking, asleep or head-shaking, and
 * halved while trading.
 */
@Mixin(VillagerModel.class)
public abstract class VillagerModelMoodMixin {

    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart rightLeg;
    @Shadow @Final private ModelPart leftLeg;
    @Shadow @Final private ModelPart arms;

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V",
            at = @At("TAIL"),
            require = 1
    )
    private void terrainlab$moodBodyLanguage(VillagerRenderState state, CallbackInfo ci) {
        Integer flags = state.getRenderData(VillagerMoodClient.MOOD_FLAGS);

        if (flags == null
                || flags == 0
                || state.isUnhappy
                || state.hasPose(Pose.SLEEPING)) {

            return;
        }

        // Standing still only; the offsets fight the walk cycle.
        if (state.walkAnimationSpeed > 0.05F) {
            return;
        }
        // Halved while the player is trading with them.
        Boolean trading = state.getRenderData(VillagerMoodClient.NEAR_OPEN_TRADE);
        float s = Boolean.TRUE.equals(trading) ? 0.5F : 1.0F;

        ModelPart body = ((Model<?>) (Object) this).root().getChild("body");
        float t = state.ageInTicks;

        // States with a mood voice get subtle postures; cold and hot (no voice) get fuller ones.
        switch (VillagerMoodService.primary(flags)) {
            case VillagerMoodService.FEARFUL -> {
                // Flinch: raised shoulders, tremble, glances side to side.
                float tremble = Mth.sin(t * 4.1F) * 0.06F * s;
                this.head.yRot += 0.35F * s * Mth.sin(t * 0.3F);
                this.head.xRot += 0.1F * s;
                this.head.x += tremble;
                body.x += tremble;
                this.arms.x += tremble;
                this.arms.y -= 0.4F * s;
            }
            case VillagerMoodService.INJURED -> {
                // Favouring one side (+x in model space): the folded arms slide over to clutch the flank, a slight
                // lean and head tilt toward it, weight off the right leg, and an occasional wince.
                // (The villager's arms are one rigid part, so "one arm on the side" is approximated.)
                float wince = Mth.clamp((Mth.sin(t * 0.07F) - 0.85F) / 0.15F, 0.0F, 1.0F);
                this.arms.x += 1.3F * s;
                this.arms.y += 0.8F * s;
                this.arms.yRot += 0.3F * s;
                this.arms.zRot += 0.12F * s;
                body.zRot += 0.05F * s;
                this.head.x += 0.5F * s;
                this.head.zRot += 0.1F * s;
                this.head.xRot += (0.15F + 0.12F * wince) * s;
                this.rightLeg.xRot -= 0.12F * s;
                this.rightLeg.zRot += 0.04F * s;
            }
            case VillagerMoodService.COLD -> {
                // Shivering hunch, arms hugged tight to the chest.
                float shiver = Mth.sin(t * 3.3F) * 0.22F * s;
                this.head.x += shiver;
                body.x += shiver;
                this.arms.x += shiver;
                this.head.y += 1.0F * s;
                this.head.xRot += 0.25F * s;
                body.xRot += 0.08F * s;
                this.arms.xRot -= 0.25F * s;
                this.arms.y -= 0.5F * s;
            }
            case VillagerMoodService.HOT -> {
                // Wilting: head tipped back and to one side, arms loose, slow sway.
                float sway = Mth.sin(t * 0.06F);
                this.head.xRot -= 0.35F * s;
                this.head.zRot += (0.2F + 0.06F * sway) * s;
                body.zRot += 0.03F * sway * s;
                this.arms.xRot += 0.45F * s;
                this.arms.y += 1.5F * s;
            }
            case VillagerMoodService.TIRED -> {
                // Yawn (head back for 30 ticks) each time the yawn voice plays.
                float yawn = terrainlab$gestureAge(state, VillagerGestureAccess.YAWN);
                if (yawn >= 0.0F && yawn < 30.0F) {
                    this.head.xRot -= 0.5F * s * Mth.sin(yawn / 30.0F * Mth.PI);
                }
            }
            case VillagerMoodService.HUNGRY -> {
                // Arms lowered towards the belly, head down a little.
                this.arms.xRot += 0.3F * s;
                this.arms.y += 1.5F * s;
                this.head.xRot += 0.2F * s;
            }
            case VillagerMoodService.TRAPPED -> {
                // Stamp (10 ticks) each time the grumble voice plays, plus an occasional head-shake.
                float stamp = terrainlab$gestureAge(state, VillagerGestureAccess.STAMP);
                if (stamp >= 0.0F && stamp < 10.0F) {
                    this.rightLeg.xRot -= 0.55F * s * Mth.sin(stamp / 10.0F * Mth.PI);
                }
                if (Mth.sin(t * 0.04F) > 0.8F) {
                    this.head.yRot += 0.25F * s * Mth.sin(t * 0.6F);
                }
            }
            default -> {
            }
        }
    }

    /** Ticks since the given gesture started, or -1 if the last gesture was a different one. */
    @org.spongepowered.asm.mixin.Unique
    private static float terrainlab$gestureAge(VillagerRenderState state, int gesture) {
        Integer last = state.getRenderData(VillagerMoodClient.GESTURE);
        Integer start = state.getRenderData(VillagerMoodClient.GESTURE_START);
        if (last == null || start == null || last != gesture) {
            return -1.0F;
        }
        return state.ageInTicks - start;
    }
}
