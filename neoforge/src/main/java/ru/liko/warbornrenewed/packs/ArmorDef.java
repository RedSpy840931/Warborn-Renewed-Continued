package ru.liko.warbornrenewed.packs;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class ArmorDef {

    @SerializedName("id")
    private String id;

    @SerializedName("type")
    private String type = "helmet";

    @SerializedName("model_id")
    private String modelId;

    @SerializedName("texture_id")
    private String textureId;

    @SerializedName("animation_id")
    private String animationId;

    @SerializedName("material")
    private String material;

    @SerializedName("icon")
    private String icon;

    /**
     * Отображаемое имя предмета (по умолчанию, если нет мультиязычной версии).
     * Если указано — используется напрямую в игре.
     */
    @SerializedName("name")
    private String name;

    /**
     * Мультиязычные имена предмета.
     * Ключ — код локали (например "ru_ru", "en_us").
     * Приоритет: names[текущая_локаль] > name > id
     */
    @SerializedName("names")
    private Map<String, String> names;

    @SerializedName("defense")
    private int defense = 0;

    @SerializedName("toughness")
    private float toughness = 0.0f;

    @SerializedName("knockback_resistance")
    private float knockbackResistance = 0.0f;

    @SerializedName("durability")
    private int durability = 0;

    public ArmorDef() {
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getTextureId() {
        return textureId;
    }

    public void setTextureId(String textureId) {
        this.textureId = textureId;
    }

    public String getAnimationId() {
        return animationId;
    }

    public void setAnimationId(String animationId) {
        this.animationId = animationId;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getNames() {
        return names;
    }

    public void setNames(Map<String, String> names) {
        this.names = names;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
    /**
     * Получить отображаемое имя для указанной локали.
     * Приоритет: names[locale] -> name -> id
     */
    public String getDisplayName(String locale) {
        // 1. Ищем в мультиязычных именах
        if (names != null && locale != null) {
            String localized = names.get(locale.toLowerCase());
            if (localized != null && !localized.isEmpty()) {
                return localized;
            }
        }
        // 2. Используем поле name
        if (name != null && !name.isEmpty()) {
            return name;
        }
        // 3. Fallback на id
        return id;
    }

    public int getDefense() {
        return defense;
    }

    public void setDefense(int defense) {
        this.defense = defense;
    }

    public float getToughness() {
        return toughness;
    }

    public void setToughness(float toughness) {
        this.toughness = toughness;
    }

    public float getKnockbackResistance() {
        return knockbackResistance;
    }

    public void setKnockbackResistance(float knockbackResistance) {
        this.knockbackResistance = knockbackResistance;
    }

    public int getDurability() {
        return durability;
    }

    public void setDurability(int durability) {
        this.durability = durability;
    }
}
