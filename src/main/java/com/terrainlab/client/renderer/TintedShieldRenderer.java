package com.terrainlab.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Shield special renderer for the wood shields, using vanilla's shield mesh. With a "texture"
 * (a full shield texture in the shield atlas, e.g. "terrainlab:entity/shield/spruce_wood") it draws
 * that texture untinted, keeping its wood grain, iron rim and rivets. Without one it falls back to
 * tinting vanilla's flat pattern base with "color". Banner patterns are not supported. The handle
 * is never tinted, as in vanilla's ShieldSpecialRenderer.
 */

public record TintedShieldRenderer(MaterialSet materials, ShieldModel model, Material material, int tint)
        implements SpecialModelRenderer<Void> {

    @Override
    public @Nullable Void extractArgument(ItemStack stack) {
        // Tint is fixed per item at bake time; the stack carries no colour components.
        return null;
    }

    @Override
    public void submit(
            @Nullable Void argument,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            boolean hasFoil,
            int outlineColor
    ) {
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        Material material = this.material;

        // Handle/boss: untinted, as in vanilla.
        collector.submitModelPart(
                this.model.handle(),
                poseStack,
                this.model.renderType(material.atlasLocation()),
                light,
                overlay,
                this.materials.get(material),
                false,
                false,
                -1,
                null,
                outlineColor
        );

        // Plate: its own texture untinted (tint -1), or the flat base tinted with the baked colour.
        collector.submitModelPart(
                this.model.plate(),
                poseStack,
                this.model.renderType(material.atlasLocation()),
                light,
                overlay,
                this.materials.get(material),
                false,
                hasFoil,
                this.tint,
                null,
                outlineColor
        );

        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        PoseStack poseStack = new PoseStack();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        this.model.root().getExtentsForGui(poseStack, consumer);
    }

    public record Unbaked(Optional<Identifier> texture, int color) implements SpecialModelRenderer.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("texture").forGetter(Unbaked::texture),
                Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Unbaked::color)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext ctx) {
            ShieldModel model = new ShieldModel(ctx.entityModelSet().bakeLayer(ModelLayers.SHIELD));
            if (this.texture.isPresent()) {
                return new TintedShieldRenderer(ctx.materials(), model,
                        new Material(Sheets.SHIELD_SHEET, this.texture.get()), -1);
            }
            // JSON supplies RGB only; force full alpha to make a valid ARGB tint.
            int argb = 0xFF000000 | (this.color & 0x00FFFFFF);
            return new TintedShieldRenderer(ctx.materials(), model, ModelBakery.SHIELD_BASE, argb);
        }
    }
}
