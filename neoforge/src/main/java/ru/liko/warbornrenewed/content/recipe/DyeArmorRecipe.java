package ru.liko.warbornrenewed.content.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import ru.liko.warbornrenewed.content.armorset.WarbornArmorItem;
import ru.liko.warbornrenewed.packs.CustomPackArmorItem;

import java.util.ArrayList;
import java.util.List;

public class DyeArmorRecipe extends CustomRecipe {
    public DyeArmorRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static boolean isDyeable(ItemStack stack) {
        return stack.getItem() instanceof WarbornArmorItem || stack.getItem() instanceof CustomPackArmorItem;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack armor = ItemStack.EMPTY;
        List<ItemStack> dyes = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (isDyeable(stack)) {
                    if (!armor.isEmpty()) {
                        return false;
                    }
                    armor = stack;
                } else if (stack.getItem() instanceof DyeItem) {
                    dyes.add(stack);
                } else {
                    return false;
                }
            }
        }
        return !armor.isEmpty() && !dyes.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack armor = ItemStack.EMPTY;
        int colorCount = 0;
        int totalRed = 0;
        int totalGreen = 0;
        int totalBlue = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                if (isDyeable(stack)) {
                    armor = stack.copy();
                    armor.setCount(1);
                    if (ru.liko.warbornrenewed.platform.Services.ITEM_DATA.hasArmorColor(armor)) {
                        int existingColor = ru.liko.warbornrenewed.platform.Services.ITEM_DATA.getArmorColor(armor);
                        int r = (existingColor >> 16) & 0xFF;
                        int g = (existingColor >> 8) & 0xFF;
                        int b = existingColor & 0xFF;
                        totalRed += r;
                        totalGreen += g;
                        totalBlue += b;
                        colorCount++;
                    }
                } else if (stack.getItem() instanceof DyeItem dyeItem) {
                    DyeColor dyeColor = dyeItem.getDyeColor();
                    int color = dyeColor.getTextColor();
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = color & 0xFF;
                    totalRed += r;
                    totalGreen += g;
                    totalBlue += b;
                    colorCount++;
                }
            }
        }

        if (armor.isEmpty() || colorCount == 0) {
            return ItemStack.EMPTY;
        }

        int avgRed = totalRed / colorCount;
        int avgGreen = totalGreen / colorCount;
        int avgBlue = totalBlue / colorCount;

        int maxColorSum = 0;
        if (ru.liko.warbornrenewed.platform.Services.ITEM_DATA.hasArmorColor(armor)) {
            int existingColor = ru.liko.warbornrenewed.platform.Services.ITEM_DATA.getArmorColor(armor);
            int r = (existingColor >> 16) & 0xFF;
            int g = (existingColor >> 8) & 0xFF;
            int b = existingColor & 0xFF;
            maxColorSum += Math.max(r, Math.max(g, b));
        }

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof DyeItem dyeItem) {
                int color = dyeItem.getDyeColor().getTextColor();
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                maxColorSum += Math.max(r, Math.max(g, b));
            }
        }

        float averageMax = (float) maxColorSum / (float) colorCount;
        float maxAverage = (float) Math.max(avgRed, Math.max(avgGreen, avgBlue));

        if (maxAverage > 0.0F) {
            avgRed = (int) ((float) avgRed * averageMax / maxAverage);
            avgGreen = (int) ((float) avgGreen * averageMax / maxAverage);
            avgBlue = (int) ((float) avgBlue * averageMax / maxAverage);
        }

        int newColor = (avgRed << 16) | (avgGreen << 8) | avgBlue;
        ru.liko.warbornrenewed.platform.Services.ITEM_DATA.setArmorColor(armor, newColor);

        return armor;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.hasCraftingRemainingItem()) {
                remaining.set(i, stack.getCraftingRemainingItem());
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ru.liko.warbornrenewed.registry.ModRecipes.DYE_ARMOR_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<DyeArmorRecipe> {
        private static final MapCodec<DyeArmorRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                                .forGetter(CustomRecipe::category))
                .apply(instance, DyeArmorRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, DyeArmorRecipe> STREAM_CODEC = StreamCodec.of(
                (buffer, recipe) -> buffer.writeEnum(recipe.category()),
                buffer -> new DyeArmorRecipe(buffer.readEnum(CraftingBookCategory.class)));

        @Override
        public MapCodec<DyeArmorRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DyeArmorRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}