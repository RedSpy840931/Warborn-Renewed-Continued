package ru.liko.warbornrenewed.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import ru.liko.warbornrenewed.packs.ArmorDef;
import ru.liko.warbornrenewed.packs.CustomPackArmorItem;
import ru.liko.warbornrenewed.packs.WarbornPackManager;
import ru.liko.warbornrenewed.platform.Services;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PackItemRenderer extends GeoItemRenderer<CustomPackArmorItem> {
    private static final Map<ResourceLocation, List<VoxelVertex>> MESH_CACHE = new ConcurrentHashMap<>();

    public PackItemRenderer() {
        super(new PackItemModel());
        ((PackItemModel) this.getGeoModel()).renderer = this;
    }

    public ItemStack getCurrentStack() {
        return this.currentItemStack;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String packId = Services.ITEM_DATA.getArmorPackId(stack);
        ArmorDef def = (packId != null && !packId.isEmpty()) ? WarbornPackManager.getArmorDef(packId) : null;

        // Если задана 2D-иконка, отображаем экструдированную 3D-модель
        if (def != null && def.getIcon() != null && !def.getIcon().trim().isEmpty()) {
            render2DIcon(def.getIcon(), stack, poseStack, bufferSource, transformType, packedLight, packedOverlay);
            return;
        }

        // Иначе отрисовываем полноразмерную 3D-модель GeckoLib
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void render2DIcon(String iconRaw, ItemStack stack, PoseStack poseStack,
                              MultiBufferSource bufferSource, ItemDisplayContext transformType,
                              int packedLight, int packedOverlay) {
        ResourceLocation iconLoc = buildIconResource(iconRaw);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(iconLoc));

        poseStack.pushPose();

        // 1. Отменяем базовое ванильное смещение центра (-0.5F)
        poseStack.translate(0.5F, 0.5F, 0.5F);

        // 2. Инвертируем 3D-смещение из pack_*.json модели
        BakedModel bakedModel = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
        ItemTransform transform = bakedModel.getTransforms().getTransform(transformType);
        if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
            Matrix4f inv = new Matrix4f();
            inv.translate(transform.translation.x(), transform.translation.y(), transform.translation.z());
            inv.rotateXYZ(
                    transform.rotation.x() * ((float) Math.PI / 180F),
                    transform.rotation.y() * ((float) Math.PI / 180F),
                    transform.rotation.z() * ((float) Math.PI / 180F)
            );
            inv.scale(transform.scale.x(), transform.scale.y(), transform.scale.z());
            inv.invert();
            poseStack.mulPose(inv);
        }

        // 3. Применяем стандартную трансформацию для предметов
        applyDefault2DTransform(poseStack, transformType);

        // Поддержка окрашивания красителями
        int color = 0xFFFFFFFF;
        if (Services.ITEM_DATA.hasArmorColor(stack)) {
            int customColor = Services.ITEM_DATA.getArmorColor(stack);
            color = 0xFF000000 | (customColor & 0x00FFFFFF);
        }

        int light = (transformType == ItemDisplayContext.GUI) ? 15728880 : packedLight;

        // В инвентаре отрисовываем плоский квад, в мире и в руках — 1-пиксельную экструзию
        if (transformType == ItemDisplayContext.GUI) {
            renderFlatQuad(poseStack, buffer, color, light, packedOverlay);
        } else {
            renderExtrudedMesh(iconLoc, poseStack, buffer, color, light, packedOverlay);
        }

        poseStack.popPose();
    }

    private void applyDefault2DTransform(PoseStack poseStack, ItemDisplayContext transformType) {
        switch (transformType) {
            case GUI -> {}
            case GROUND -> {
                poseStack.translate(0.0F, 2.0F / 16.0F, 0.0F);
                poseStack.scale(0.5F, 0.5F, 0.5F);
            }
            case FIXED -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                poseStack.scale(1.0F, 1.0F, 1.0F);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                poseStack.translate(0.0F, 3.0F / 16.0F, 1.0F / 16.0F);
                poseStack.scale(0.55F, 0.55F, 0.55F);
            }
            case FIRST_PERSON_RIGHT_HAND -> {
                poseStack.translate(1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(25.0F));
                poseStack.scale(0.68F, 0.68F, 0.68F);
            }
            case FIRST_PERSON_LEFT_HAND -> {
                poseStack.translate(1.13F / 16.0F, 3.2F / 16.0F, 1.13F / 16.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-25.0F));
                poseStack.scale(0.68F, 0.68F, 0.68F);
            }
            case HEAD -> {
                poseStack.translate(0.0F, 13.0F / 16.0F, 7.0F / 16.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            default -> {}
        }
    }

    private void renderFlatQuad(PoseStack poseStack, VertexConsumer buffer, int color, int light, int overlay) {
        Matrix4f pose = poseStack.last().pose();
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buffer.addVertex(pose, -0.5F, -0.5F, 0.0F).setColor(r, g, b, a).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(pose,  0.5F, -0.5F, 0.0F).setColor(r, g, b, a).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(pose,  0.5F,  0.5F, 0.0F).setColor(r, g, b, a).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(pose, -0.5F,  0.5F, 0.0F).setColor(r, g, b, a).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(0.0F, 0.0F, 1.0F);
    }

    private void renderExtrudedMesh(ResourceLocation iconLoc, PoseStack poseStack, VertexConsumer buffer,
                                    int color, int light, int overlay) {
        List<VoxelVertex> mesh = MESH_CACHE.computeIfAbsent(iconLoc, this::generateMeshForIcon);
        Matrix4f pose = poseStack.last().pose();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (VoxelVertex v : mesh) {
            buffer.addVertex(pose, v.x, v.y, v.z)
                    .setColor(r, g, b, a)
                    .setUv(v.u, v.v)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(v.nx, v.ny, v.nz);
        }
    }

    private List<VoxelVertex> generateMeshForIcon(ResourceLocation iconLoc) {
        List<VoxelVertex> vertices = new ArrayList<>();
        float zFront = 0.03125F; // +0.5 / 16 (толщина в 1 пиксель)
        float zBack = -0.03125F; // -0.5 / 16

        BufferedImage image = null;
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(iconLoc);
            if (res.isPresent()) {
                try (InputStream stream = res.get().open()) {
                    image = ImageIO.read(stream);
                }
            }
        } catch (Exception ignored) {}

        if (image == null) {
            addQuad(vertices, -0.5F, -0.5F, zFront, 0.5F, 0.5F, zFront, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F);
            addQuad(vertices, -0.5F, 0.5F, zBack, 0.5F, -0.5F, zBack, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, -1.0F);
            return vertices;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 1. Лицевая сторона (CCW)
        vertices.add(new VoxelVertex(-0.5F, -0.5F, zFront, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F));
        vertices.add(new VoxelVertex( 0.5F, -0.5F, zFront, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F));
        vertices.add(new VoxelVertex( 0.5F,  0.5F, zFront, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F));
        vertices.add(new VoxelVertex(-0.5F,  0.5F, zFront, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F));

        // 2. Обратная сторона (CCW при взгляде сзади)
        vertices.add(new VoxelVertex( 0.5F, -0.5F, zBack, 1.0F, 1.0F, 0.0F, 0.0F, -1.0F));
        vertices.add(new VoxelVertex(-0.5F, -0.5F, zBack, 0.0F, 1.0F, 0.0F, 0.0F, -1.0F));
        vertices.add(new VoxelVertex(-0.5F,  0.5F, zBack, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F));
        vertices.add(new VoxelVertex( 0.5F,  0.5F, zBack, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F));

        // 3. Сканирование прозрачности
        boolean[][] opaque = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                opaque[x][y] = (alpha > 20);
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!opaque[x][y]) continue;

                float px0 = -0.5F + (float) x / width;
                float px1 = -0.5F + (float) (x + 1) / width;
                float py0 = 0.5F - (float) (y + 1) / height;
                float py1 = 0.5F - (float) y / height;

                float u0 = (float) x / width;
                float u1 = (float) (x + 1) / width;
                float v0 = (float) y / height;
                float v1 = (float) (y + 1) / height;

                // Берём срез цвета ровно из центра пикселя, исключая зацеп прозрачных соседей
                float uMid = (u0 + u1) * 0.5F;
                float vMid = (v0 + v1) * 0.5F;

                // Верхнее ребро (+Y)
                if (y == 0 || !opaque[x][y - 1]) {
                    vertices.add(new VoxelVertex(px0, py1, zFront, u0, vMid, 0.0F, 1.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py1, zFront, u1, vMid, 0.0F, 1.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py1, zBack,  u1, vMid, 0.0F, 1.0F, 0.0F));
                    vertices.add(new VoxelVertex(px0, py1, zBack,  u0, vMid, 0.0F, 1.0F, 0.0F));
                }
                // Нижнее ребро (-Y)
                if (y == height - 1 || !opaque[x][y + 1]) {
                    vertices.add(new VoxelVertex(px0, py0, zBack,  u0, vMid, 0.0F, -1.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py0, zBack,  u1, vMid, 0.0F, -1.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py0, zFront, u1, vMid, 0.0F, -1.0F, 0.0F));
                    vertices.add(new VoxelVertex(px0, py0, zFront, u0, vMid, 0.0F, -1.0F, 0.0F));
                }
                // Левое ребро (-X)
                if (x == 0 || !opaque[x - 1][y]) {
                    vertices.add(new VoxelVertex(px0, py0, zBack,  uMid, v1, -1.0F, 0.0F, 0.0F));
                    vertices.add(new VoxelVertex(px0, py0, zFront, uMid, v1, -1.0F, 0.0F, 0.0F));
                    vertices.add(new VoxelVertex(px0, py1, zFront, uMid, v0, -1.0F, 0.0F, 0.0F));
                    vertices.add(new VoxelVertex(px0, py1, zBack,  uMid, v0, -1.0F, 0.0F, 0.0F));
                }
                // Правое ребро (+X)
                if (x == width - 1 || !opaque[x + 1][y]) {
                    vertices.add(new VoxelVertex(px1, py0, zFront, uMid, v1, 1.0F, 0.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py0, zBack,  uMid, v1, 1.0F, 0.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py1, zBack,  uMid, v0, 1.0F, 0.0F, 0.0F));
                    vertices.add(new VoxelVertex(px1, py1, zFront, uMid, v0, 1.0F, 0.0F, 0.0F));
                }
            }
        }

        return vertices;
    }

    private void addQuad(List<VoxelVertex> list, float x0, float y0, float z0, float x1, float y1, float z1,
                         float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        list.add(new VoxelVertex(x0, y0, z0, u0, v1, nx, ny, nz));
        list.add(new VoxelVertex(x1, y0, z0, u1, v1, nx, ny, nz));
        list.add(new VoxelVertex(x1, y1, z1, u1, v0, nx, ny, nz));
        list.add(new VoxelVertex(x0, y1, z1, u0, v0, nx, ny, nz));
    }

    private static class VoxelVertex {
        final float x, y, z, u, v, nx, ny, nz;

        VoxelVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            this.x = x; this.y = y; this.z = z;
            this.u = u; this.v = v;
            this.nx = nx; this.ny = ny; this.nz = nz;
        }
    }

    private ResourceLocation buildIconResource(String raw) {
        ResourceLocation loc = ResourceLocation.parse(raw);
        String path = loc.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return ResourceLocation.fromNamespaceAndPath(loc.getNamespace(), path);
    }

    @Override
    public void actuallyRender(PoseStack poseStack, CustomPackArmorItem animatable, BakedGeoModel model,
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

    private static class PackItemModel extends GeoModel<CustomPackArmorItem> {
        public PackItemRenderer renderer;

        private static final ResourceLocation FALLBACK_MODEL = ResourceLocation.parse("warbornrenewed:geo/default_armor.geo.json");
        private static final ResourceLocation FALLBACK_TEXTURE = ResourceLocation.parse("warbornrenewed:textures/armor/default.png");
        private static final ResourceLocation FALLBACK_ANIMATION = ResourceLocation.parse("warbornrenewed:animations/default.animation.json");

        private ArmorDef getDefFromStack() {
            if (renderer == null) return null;
            ItemStack stack = renderer.getCurrentStack();
            if (stack == null || stack.isEmpty()) return null;
            String packId = Services.ITEM_DATA.getArmorPackId(stack);
            if (packId == null || packId.isEmpty()) return null;
            return WarbornPackManager.getArmorDef(packId);
        }

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
                } catch (Exception ignored) {}
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
                        return buildResource(def.getModelId(), "textures/", ".png");
                    }
                } catch (Exception ignored) {}
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
                } catch (Exception ignored) {}
            }
            return FALLBACK_ANIMATION;
        }
    }
}