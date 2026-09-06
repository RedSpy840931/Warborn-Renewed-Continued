package ru.liko.warbornrenewed.content.armorset;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import ru.liko.warbornrenewed.client.renderer.WarbornArmorItemRenderer;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.warbornrenewed.Warbornrenewed;
import ru.liko.warbornrenewed.registry.ModDataComponents;
import ru.liko.warbornrenewed.config.WarbornConfig;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class WarbornArmorItem extends ArmorItem implements GeoItem {
    // ==================== Vision Capability Tags ====================
    public static final String TAG_NVG = "nvg"; // Night Vision Goggles
    public static final String TAG_THERMAL = "thermal"; // Thermal Vision
    public static final String TAG_DIGITAL = "digital"; // Digital overlay
    public static final String TAG_SIMPLE_NVG = "simple_nvg"; // Simple night vision (no animation)
    public static final String TAG_GOGGLE = "goggle"; // Protective goggles

    // NBT keys for helmet state (stored in DataComponents in 1.21.1)
    public static final String NBT_NVG_DOWN = "nvg_down"; // Is NVG flipped down?
    public static final String NBT_HELMET_OPEN = "helmet_top_open"; // Is helmet visor/top open?

    private final String itemId;
    private final ArmorVisualSpec visuals;
    private final ArmorBonesSpec bones;
    private final List<ArmorAttributeSpec> attributes;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Vision capabilities for this armor piece (stores capability tags like "nvg",
    // "thermal")
    private final List<String> visionCapabilities = new ArrayList<>();

    public WarbornArmorItem(String itemId, Holder<ArmorMaterial> material, Type type, Properties properties,
            ArmorVisualSpec visuals, ArmorBonesSpec bones, List<ArmorAttributeSpec> attributes) {
        super(material, type, properties);
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.bones = Objects.requireNonNull(bones, "bones");
        this.attributes = List.copyOf(attributes);
    }

    /**
     * Add a vision capability to this helmet
     * Called during registration from ArmorPieceDefinition.create()
     */
    public void addVisionCapability(String capability) {
        if (!visionCapabilities.contains(capability)) {
            visionCapabilities.add(capability);
        }
    }

    /**
     * Check if this helmet has a specific vision capability
     */
    public boolean hasVisionCapability(String capability) {
        return visionCapabilities.contains(capability);
    }

    @Override
    public @NotNull Holder<SoundEvent> getEquipSound() {
        return Holder.direct(SoundEvents.EMPTY);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private WarbornArmorRenderer renderer;
            private BlockEntityWithoutLevelRenderer itemRenderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> defaultModel) {
                if (renderer == null) {
                    renderer = new WarbornArmorRenderer(visuals, bones);
                }
                renderer.prepForRender(livingEntity, stack, slot, defaultModel);
                return renderer;
            }

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (itemRenderer == null) {
                    itemRenderer = new WarbornArmorItemRenderer(visuals);
                }
                return itemRenderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Animation controller for NVG/helmet toggle
        // Only add controller if helmet has animation file
        if (getType() == Type.HELMET && visuals.animation() != null) {
            controllers.add(new AnimationController<>(this, "nvg_toggle", 0, state -> {
                // Get ItemStack and Entity from animation state
                ItemStack stack = state.getData(DataTickets.ITEMSTACK);
                Entity rawEntity = state.getData(DataTickets.ENTITY);

                // Stop if not a living entity or is armor stand
                if (!(rawEntity instanceof LivingEntity entity) || entity instanceof ArmorStand) {
                    return PlayState.STOP;
                }

                // Default to down position
                boolean nvgDown = isNVGDown(stack);
                String animationName = nvgDown ? "nvg_down" : "nvg_up";

                // Set animation based on state with HOLD_ON_LAST_FRAME
                // Use thenPlayAndHold to prevent looping
                state.setAnimation(RawAnimation.begin().thenPlayAndHold(animationName));
                return PlayState.CONTINUE;
            }));
        }
    }

    /**
     * Set NVG up/down state
     */
    public void setNVGUp(ItemStack stack, boolean up) {
        setNVGDown(stack, !up);
    }

    /**
     * Check if NVG is up
     */
    public boolean isNVGUp(ItemStack stack) {
        return !isNVGDown(stack);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag isAdvanced) {
        // НЕ вызываем super, чтобы не показывать стандартные атрибуты

        // Материал брони
        ResourceLocation matLoc = getMaterial().unwrapKey()
                .map(net.minecraft.resources.ResourceKey::location)
                .orElse(ResourceLocation.parse("warbornrenewed:unknown"));

        String materialName = matLoc.getPath();
        String materialKey = "material.warbornrenewed." + materialName;
        // Material Name in GOLD
        Component materialDisplayName = Component.translatable(materialKey).withStyle(ChatFormatting.GOLD);

        tooltipComponents.add(Component.empty());
        // Fix for %s issue: Pass the component as an argument to the translatable
        // component
        tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.material", materialDisplayName)
                .withStyle(ChatFormatting.GRAY));

        // REMOVED: Custom display of Defense, Toughness, and Knockback Resistance
        // as per user request to reduce clutter and duplicate info.

        // Кастомные атрибуты
        if (!attributes.isEmpty()) {
            tooltipComponents.add(Component.empty());
            tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.attributes")
                    .withStyle(ChatFormatting.AQUA));

            for (ArmorAttributeSpec spec : attributes) {
                double val = spec.value();
                String descId = spec.attribute().getDescriptionId();

                String valStr;
                ChatFormatting valColor = val > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;

                // Special formatting for Protection Class
                if (descId.contains("protection_class")) {
                    valStr = String.valueOf((int) val);
                    valColor = ChatFormatting.GOLD;

                    // Use designated translation key for Protection Class if available
                    // "tooltip.warbornrenewed.protection_class" -> "Class: %s"
                    tooltipComponents.add(Component.literal("  ")
                            .append(Component.translatable("tooltip.warbornrenewed.protection_class",
                                    Component.literal(valStr).withStyle(valColor))
                                    .withStyle(ChatFormatting.GRAY)));
                    continue;
                }

                if (Math.abs(val) <= 2.0) {
                    valStr = String.format("%+.0f%%", val * 100);
                } else {
                    valStr = String.format("%+.1f", val);
                }

                tooltipComponents.add(Component.literal("  ")
                        .append(Component.translatable(descId).withStyle(ChatFormatting.GRAY))
                        .append(": ")
                        .append(Component.literal(valStr).withStyle(valColor)));
            }
        }

        // Vision Capabilities
        if (getType() == Type.HELMET && hasAnyVisionCapability()) {
            tooltipComponents.add(Component.empty());
            tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.vision_capabilities")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            if (hasVisionCapability(TAG_NVG)) {
                tooltipComponents.add(Component.literal("  ► ")
                        .append(Component.translatable("tooltip.warbornrenewed.vision.nvg"))
                        .withStyle(ChatFormatting.GREEN));
            }
            if (hasVisionCapability(TAG_THERMAL)) {
                tooltipComponents.add(Component.literal("  ► ")
                        .append(Component.translatable("tooltip.warbornrenewed.vision.thermal"))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            // Показываем состояние NVG если есть
            if (hasVisionCapability(TAG_NVG)) {
                boolean nvgDown = isNVGDown(stack);
                Component statusKey = nvgDown
                        ? Component.translatable("tooltip.warbornrenewed.nvg.down").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("tooltip.warbornrenewed.nvg.up").withStyle(ChatFormatting.RED);
                tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.nvg.status", statusKey)
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    // ==================== Vision Capability Methods ====================

    /**
     * Check if this helmet has any vision capabilities
     */
    public boolean hasAnyVisionCapability() {
        return !visionCapabilities.isEmpty();
    }

    /**
     * Check if NVG is currently down (active)
     */
    public static boolean isNVGDown(ItemStack stack) {
        Boolean value = stack.get(ModDataComponents.NVG_DOWN.get());
        return value != null && value;
    }

    /**
     * Toggle NVG state
     */
    public static void toggleNVG(ItemStack stack) {
        boolean current = isNVGDown(stack);
        setNVGDown(stack, !current);
    }

    /**
     * Set NVG state
     */
    public static void setNVGDown(ItemStack stack, boolean down) {
        stack.set(ModDataComponents.NVG_DOWN.get(), down);
    }

    /**
     * Check if helmet visor/top is open
     */
    public static boolean isHelmetOpen(ItemStack stack) {
        Boolean value = stack.get(ModDataComponents.HELMET_OPEN.get());
        return value != null && value;
    }

    /**
     * Toggle helmet open state
     */
    public static void toggleHelmet(ItemStack stack) {
        boolean current = isHelmetOpen(stack);
        setHelmetOpen(stack, !current);
    }

    /**
     * Set helmet open state
     */
    public static void setHelmetOpen(ItemStack stack, boolean open) {
        stack.set(ModDataComponents.HELMET_OPEN.get(), open);
    }

    public String getItemId() {
        return itemId;
    }

    public ArmorVisualSpec getVisuals() {
        return visuals;
    }

    @Nullable
    public ResourceLocation getNvgShader() {
        return visuals.nvgShader();
    }

    public ArmorBonesSpec getBones() {
        return bones;
    }

    // ==================== Durability Control ====================

    @Override
    public boolean isDamageable(ItemStack stack) {
        // Check config to determine if armor should be damageable
        return WarbornConfig.COMMON.armorIsDamageable.get();
    }

    // ==================== Skin Variant System ====================

    /**
     * Cycle through available texture variants (skins)
     */
    public void cycleVariant(ItemStack stack) {
        if (visuals.variants().isEmpty())
            return;

        List<String> keys = new ArrayList<>(visuals.variants().keySet());
        Collections.sort(keys); // Ensure deterministic order

        // Add default (empty string) as the first option
        List<String> allVariants = new ArrayList<>();
        allVariants.add("");
        allVariants.addAll(keys);

        // TODO: Implement variant storage with DataComponents
    }

    /**
     * Get the current variant name
     */
    public String getVariant(ItemStack stack) {
        String variant = stack.get(ModDataComponents.ARMOR_VARIANT.get());
        return variant != null ? variant : "";
    }
}
