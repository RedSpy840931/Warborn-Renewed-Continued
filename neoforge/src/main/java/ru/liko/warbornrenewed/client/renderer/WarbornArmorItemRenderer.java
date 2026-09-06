package ru.liko.warbornrenewed.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import ru.liko.warbornrenewed.content.armorset.ArmorVisualSpec;
import ru.liko.warbornrenewed.content.armorset.WarbornArmorItem;
import ru.liko.warbornrenewed.content.armorset.WarbornArmorModel;
import ru.liko.warbornrenewed.platform.Services;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class WarbornArmorItemRenderer extends GeoItemRenderer<WarbornArmorItem> {
    private final ArmorVisualSpec visuals;

    public WarbornArmorItemRenderer(ArmorVisualSpec visuals) {
        super(new WarbornArmorModel(visuals));
        this.visuals = visuals;
    }

    @Override
    public void actuallyRender(PoseStack poseStack, WarbornArmorItem animatable, BakedGeoModel model,
                               RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        ItemStack stack = this.currentItemStack;
        if (stack != null && Services.ITEM_DATA.hasArmorColor(stack)) {
            int customColor = Services.ITEM_DATA.getArmorColor(stack);
            int alpha = colour & 0xFF000000;
            colour = alpha | (customColor & 0x00FFFFFF);
        }
        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}