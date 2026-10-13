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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
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

public record TintedShieldRenderer(SpriteGetter materials, ShieldModel model, SpriteId material, int tint)
        implements SpecialModelRenderer<Void> {

    @Override
    public @Nullable Void extractArgument(ItemStack stack) {
        // Tint is fixed per item at bake time; the stack carries no colour components.
        return null;
    }

    @Override
    public void submit(
            @Nullable Void argument,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            int overlay,
            boolean hasFoil,
            int outlineColor
    ) {
        // 26.x: the (1, -1, -1) flip that 1.21.11 applied here now comes from the item model
        // JSON's "transformation" (present in our shield item files and now honoured), as in
        // vanilla's ShieldSpecialRenderer. Applying it here as well would flip the shield twice.
        poseStack.pushPose();
        SpriteId material = this.material;

        // Handle/boss: untinted, as in vanilla.
        collector.submitModelPart(
                this.model.handle(),
                poseStack,
                this.model.renderType(material.atlasLocation()),
                light,
                overlay,
                this.materials.get(material),
                -1,
                outlineColor
        );

        // Plate: its own texture untinted (tint -1), or the flat base tinted with the baked colour.
        collector.submitModelPart(
                this.model.plate(),
                poseStack,
                // Foil is now baked into the render type (vanilla uses entitySolidGlint for shields).
                hasFoil ? RenderTypes.entitySolidGlint(material.atlasLocation()) : this.model.renderType(material.atlasLocation()),
                light,
                overlay,
                this.materials.get(material),
                this.tint,
                outlineColor
        );

        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
        PoseStack poseStack = new PoseStack();
        this.model.root().getExtentsForGui(poseStack, consumer);
    }

    public record Unbaked(Optional<Identifier> texture, int color) implements SpecialModelRenderer.Unbaked<Void> {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("texture").forGetter(Unbaked::texture),
                Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Unbaked::color)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext ctx) {
            ShieldModel model = new ShieldModel(ctx.entityModelSet().bakeLayer(ModelLayers.SHIELD));
            if (this.texture.isPresent()) {
                return new TintedShieldRenderer(ctx.sprites(), model,
                        new SpriteId(Sheets.SHIELD_SHEET, this.texture.get()), -1);
            }
            // JSON supplies RGB only; force full alpha to make a valid ARGB tint.
            int argb = 0xFF000000 | (this.color & 0x00FFFFFF);
            return new TintedShieldRenderer(ctx.sprites(), model, Sheets.SHIELD_BASE, argb);
        }
    }
}
