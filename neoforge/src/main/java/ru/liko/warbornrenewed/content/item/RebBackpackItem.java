package ru.liko.warbornrenewed.content.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import ru.liko.warbornrenewed.client.renderer.RebBackpackRenderer;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import ru.liko.warbornrenewed.Warbornrenewed;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import ru.liko.warbornrenewed.registry.ModDataComponents;

import java.util.List;

/**
 * Рюкзак РЭБ (радиоэлектронной борьбы) - носимый предмет,
 * создающий зону глушения дронов вокруг игрока.
 * Совместим с модом WRBDrones.
 */
public class RebBackpackItem extends Item implements GeoItem {

    // Тег NBT для состояния включения РЭБ
    public static final String NBT_REB_ENABLED = "reb_enabled";

    // Радиус действия РЭБ рюкзака (меньше чем у стационарного)
    public static final double REB_BACKPACK_RADIUS = 30.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String variantId;
    private final ResourceLocation textureLocation;
    private final ResourceLocation itemTextureLocation;

    public RebBackpackItem(Properties properties, String variantId) {
        super(properties);
        this.variantId = variantId;
        this.textureLocation = Warbornrenewed.id("textures/reb-backpack-" + variantId + ".png");
        this.itemTextureLocation = Warbornrenewed.id("textures/item/reb-backpack-" + variantId + ".png");
    }

    /**
     * Получить идентификатор варианта (камуфляжа)
     */
    public String getVariantId() {
        return variantId;
    }

    /**
     * Получить путь к текстуре модели
     */
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    /**
     * Получить путь к текстуре иконки предмета
     */
    public ResourceLocation getItemTextureLocation() {
        return itemTextureLocation;
    }

    /**
     * Проверить, включен ли РЭБ
     */
    public static boolean isRebEnabled(ItemStack stack) {
        Boolean value = stack.get(ModDataComponents.REB_ENABLED.get());
        return value != null && value;
    }

    /**
     * Установить состояние РЭБ
     */
    public static void setRebEnabled(ItemStack stack, boolean enabled) {
        stack.set(ModDataComponents.REB_ENABLED.get(), enabled);
    }

    /**
     * Переключить состояние РЭБ
     */
    public static void toggleReb(ItemStack stack) {
        setRebEnabled(stack, !isRebEnabled(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);

        // Статус РЭБ
        boolean enabled = isRebEnabled(stack);
        tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.reb.status",
                enabled
                        ? Component.translatable("tooltip.warbornrenewed.reb.enabled")
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("tooltip.warbornrenewed.reb.disabled")
                                .withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));

        // Радиус действия
        tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.reb.radius",
                String.format("%.0f", REB_BACKPACK_RADIUS))
                .withStyle(ChatFormatting.DARK_AQUA));

        // Подсказка о совместимости с WRBDrones
        if (ModList.get().isLoaded("wrbdrones")) {
            tooltipComponents.add(Component.translatable("tooltip.warbornrenewed.reb.wrbdrones_compat")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Пока без анимаций, рюкзак статичный
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }


    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private RebBackpackRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new RebBackpackRenderer(RebBackpackItem.this);
                }
                return renderer;
            }
        });
    }
}
