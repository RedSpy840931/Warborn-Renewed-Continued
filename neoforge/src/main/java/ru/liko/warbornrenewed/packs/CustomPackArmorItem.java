package ru.liko.warbornrenewed.packs;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import ru.liko.warbornrenewed.client.renderer.PackItemRenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import ru.liko.warbornrenewed.platform.Services;
import ru.liko.warbornrenewed.registry.ModDataComponents;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.model.GeoModel;

import java.util.List;
import java.util.function.Consumer;

public class CustomPackArmorItem extends ArmorItem implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CustomPackArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Here you can add custom animation controllers based on pack logic if needed
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GeoArmorRenderer<CustomPackArmorItem> renderer;
            private BlockEntityWithoutLevelRenderer itemRenderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> defaultModel) {
                if (renderer == null) {
                    renderer = new PackRenderer();
                }
                renderer.prepForRender(livingEntity, stack, slot, defaultModel);
                return renderer;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) {
                    itemRenderer = new PackItemRenderer();
                }
                return itemRenderer;
            }
        });
    }

    private static class PackRenderer extends GeoArmorRenderer<CustomPackArmorItem> {
        public PackRenderer() {
            super(new PackModel());
            ((PackModel) this.getGeoModel()).renderer = this;
        }

        public ItemStack getCurrentStack() {
            return this.currentStack;
        }

        @Override
        public void actuallyRender(com.mojang.blaze3d.vertex.PoseStack poseStack, CustomPackArmorItem animatable, software.bernie.geckolib.cache.object.BakedGeoModel model, net.minecraft.client.renderer.RenderType renderType,
                                 net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
                                 int packedLight, int packedOverlay, int colour) {
            ItemStack stack = this.currentStack;
            if (stack != null && ru.liko.warbornrenewed.platform.Services.ITEM_DATA.hasArmorColor(stack)) {
                int customColor = ru.liko.warbornrenewed.platform.Services.ITEM_DATA.getArmorColor(stack);
                int alpha = colour & 0xFF000000;
                colour = alpha | (customColor & 0x00FFFFFF);
            }
            super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick,
                    packedLight, packedOverlay, colour);
        }
    }

    private static final ResourceLocation FALLBACK_MODEL = ResourceLocation.parse("warbornrenewed:geo/default_armor.geo.json");
    private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation.parse("warbornrenewed:textures/armor/default.png");
    private static final ResourceLocation FALLBACK_ANIMATION = ResourceLocation.parse("warbornrenewed:animations/default.animation.json");

    private static class PackModel extends GeoModel<CustomPackArmorItem> {
        public PackRenderer renderer;

        private ArmorDef getDefFromStack() {
            if (renderer == null) return null;
            ItemStack stack = renderer.getCurrentStack();
            if (stack == null || stack.isEmpty()) return null;
            String packId = Services.ITEM_DATA.getArmorPackId(stack);
            if (packId == null || packId.isEmpty()) return null;
            return WarbornPackManager.getArmorDef(packId);
        }

        /**
         * Builds a ResourceLocation ensuring the path has the required prefix and suffix.
         * E.g. ensurePath("example_pack:armor/alfa", "geo/", ".geo.json")
         *   -> "example_pack:geo/armor/alfa.geo.json"
         */
        private ResourceLocation buildResource(String raw, String requiredPrefix, String requiredSuffix) {
            ResourceLocation loc = ResourceLocation.parse(raw);
            String path = loc.getPath();
            if (!path.startsWith(requiredPrefix)) {
                path = requiredPrefix + path;
            }
            if (!path.endsWith(requiredSuffix)) {
                path = path + requiredSuffix;
            }
            return ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), path);
        }

        @Override
        public ResourceLocation getModelResource(CustomPackArmorItem animatable) {
            ArmorDef def = getDefFromStack();
            if (def != null && def.getModelId() != null) {
                try {
                    return buildResource(def.getModelId(), "geo/", ".geo.json");
                } catch (Exception e) {
                    // Invalid ResourceLocation, fall through to default
                }
            }
            return FALLBACK_MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(CustomPackArmorItem animatable) {
            ArmorDef def = getDefFromStack();
            if (def != null) {
                try {
                    if (def.getTextureId() != null && !def.getTextureId().isEmpty()) {
                        return buildResource(def.getTextureId(), "textures/", ".png");
                    } else if (def.getModelId() != null) {
                        // Derive texture path from model path
                        return buildResource(def.getModelId(), "textures/", ".png");
                    }
                } catch (Exception e) {
                    // Invalid ResourceLocation, fall through to default
                }
            }
            return FALLBACK_TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(CustomPackArmorItem animatable) {
            ArmorDef def = getDefFromStack();
            if (def != null) {
                try {
                    if (def.getAnimationId() != null && !def.getAnimationId().isEmpty()) {
                        return buildResource(def.getAnimationId(), "animations/", ".animation.json");
                    }
                } catch (Exception e) {
                    // Invalid ResourceLocation, fall through to default
                }
            }
            return FALLBACK_ANIMATION;
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = Services.ITEM_DATA.getArmorPackId(stack);
        if (id != null && !id.isEmpty()) {
            ArmorDef def = WarbornPackManager.getArmorDef(id);
            if (def != null) {
                String translationKey = "item.warbornrenewed.pack." + id.replace(":", ".");
                return Component.translatableWithFallback(translationKey, def.getDisplayName("en_us"));
            }
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag isAdvanced) {
        String id = Services.ITEM_DATA.getArmorPackId(stack);
        ArmorDef def = null;
        if (id != null && !id.isEmpty()) {
            def = WarbornPackManager.getArmorDef(id);
            if (def != null) {
                tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.pack_id", id)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        String materialName;
        if (def != null && def.getMaterial() != null && !def.getMaterial().isEmpty()) {
            materialName = def.getMaterial();
        } else {
            materialName = getMaterial().unwrapKey()
                    .map(net.minecraft.resources.ResourceKey::location)
                    .orElse(ResourceLocation.parse("warbornrenewed:unknown"))
                    .getPath();
        }

        String materialKey = "material.warbornrenewed." + materialName;
        Component materialDisplayName = Component.translatable(materialKey).withStyle(ChatFormatting.GOLD);
        tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.material", materialDisplayName)
            .withStyle(ChatFormatting.GRAY));

        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
    }

    // Custom stats getters intended to be used by mixins / modifiers events
    public int getDefense(ItemStack stack) {
        String id = Services.ITEM_DATA.getArmorPackId(stack);
        if (id != null && !id.isEmpty()) {
            ArmorDef def = WarbornPackManager.getArmorDef(id);
            if (def != null) {
                return def.getDefense();
            }
        }
        return this.getDefense();
    }

    public float getToughness(ItemStack stack) {
        String id = Services.ITEM_DATA.getArmorPackId(stack);
        if (id != null && !id.isEmpty()) {
            ArmorDef def = WarbornPackManager.getArmorDef(id);
            if (def != null) {
                return def.getToughness();
            }
        }
        return this.getToughness();
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        String id = Services.ITEM_DATA.getArmorPackId(stack);
        if (id != null && !id.isEmpty()) {
            ArmorDef def = WarbornPackManager.getArmorDef(id);
            if (def != null && def.getDurability() > 0) {
                return def.getDurability();
            }
        }
        return super.getMaxDamage(stack);
    }
}
