package io.th0rgal.oraxen.pack.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.th0rgal.oraxen.items.OraxenMeta;
import io.th0rgal.oraxen.utils.Utils;
import org.bukkit.Material;

import java.util.List;

public class ModelDefinitionGenerator {
    private static final float[] BOW_PULL_THRESHOLDS = new float[]{0.65f, 0.9f};
    private static final float[] CROSSBOW_PULL_THRESHOLDS = new float[]{0.58f, 1.0f};

    private final OraxenMeta oraxenMeta;
    private final Material material;

    public ModelDefinitionGenerator(OraxenMeta oraxenMeta, Material material) {
        this.oraxenMeta = oraxenMeta;
        this.material = material;
    }

    public JsonObject toJSON() {
        JsonObject root = new JsonObject();
        root.add("model", getItemDefinitionModel());
        return root;
    }

    private JsonObject getItemDefinitionModel() {
        if (material == Material.CROSSBOW && hasCrossbowStates()) {
            return createCondition("minecraft:using_item", getCrossbowIdleModel(), getCrossbowPullingModel());
        }
        if (oraxenMeta.hasPullingModels()) {
            return createCondition(
                    "minecraft:using_item",
                    createModelReference(getBaseModelPath()),
                    createRangeDispatch("minecraft:use_duration", oraxenMeta.getPullingModels(), BOW_PULL_THRESHOLDS, true, 0.05f)
            );
        }
        if (material == Material.FISHING_ROD && oraxenMeta.hasCastModel()) {
            return createCondition("minecraft:fishing_rod/cast",
                    createModelReference(getBaseModelPath()), createModelReference(oraxenMeta.getCastModel()));
        }
        if (material == Material.SHIELD && oraxenMeta.hasBlockingModel()) {
            return createCondition("minecraft:using_item", createModelReference(getBaseModelPath()),
                    createModelReference(oraxenMeta.getBlockingModel()));
        }
        return createModelReference(getBaseModelPath());
    }

    private JsonObject getCrossbowPullingModel() {
        if (!oraxenMeta.hasPullingModels()) {
            return createModelReference(getBaseModelPath());
        }

        return createRangeDispatch("minecraft:crossbow/pull", oraxenMeta.getPullingModels(),
                CROSSBOW_PULL_THRESHOLDS, false, 0f);
    }

    private JsonObject getCrossbowIdleModel() {
        JsonArray cases = new JsonArray();

        if (oraxenMeta.hasChargedModel()) {
            cases.add(createSelectCase("arrow", createModelReference(oraxenMeta.getChargedModel())));
        }
        if (oraxenMeta.hasFireworkModel()) {
            cases.add(createSelectCase("rocket", createModelReference(oraxenMeta.getFireworkModel())));
        }

        if (cases.isEmpty()) {
            return createModelReference(getBaseModelPath());
        }

        JsonObject select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:charge_type");
        select.add("cases", cases);
        select.add("fallback", createModelReference(getBaseModelPath()));
        return select;
    }

    private boolean hasCrossbowStates() {
        return oraxenMeta.hasPullingModels() || oraxenMeta.hasChargedModel() || oraxenMeta.hasFireworkModel();
    }

    private JsonObject createCondition(String property, JsonObject onFalse, JsonObject onTrue) {
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "minecraft:condition");
        condition.addProperty("property", property);
        condition.add("on_false", onFalse);
        condition.add("on_true", onTrue);
        return condition;
    }

    private JsonObject createRangeDispatch(String property, List<String> models, float[] thresholds,
                                           boolean useLegacyThresholds, float scale) {
        JsonObject rangeDispatch = new JsonObject();
        rangeDispatch.addProperty("type", "minecraft:range_dispatch");
        rangeDispatch.addProperty("property", property);

        if (scale != 0f) rangeDispatch.addProperty("scale", scale);

        rangeDispatch.add("fallback", createModelReference(models.getFirst()));

        JsonArray entries = new JsonArray();
        for (int i = 1; i < models.size(); i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("threshold", getPullThreshold(i, models.size(), thresholds, useLegacyThresholds));
            entry.add("model", createModelReference(models.get(i)));
            entries.add(entry);
        }
        rangeDispatch.add("entries", entries);
        return rangeDispatch;
    }

    private float getPullThreshold(int index, int modelCount, float[] thresholds, boolean useLegacyThresholds) {
        if (modelCount <= 1) {
            return 0f;
        }
        if (modelCount == thresholds.length + 1) {
            return thresholds[index - 1];
        }
        if (modelCount == 2) {
            return thresholds[0];
        }
        if (useLegacyThresholds) {
            return Math.min(Utils.customRound(((float) (index + 1) / modelCount), 0.05f),
                    thresholds[thresholds.length - 1]);
        }

        float step = thresholds[thresholds.length - 1] / (modelCount - 1);
        return Math.min(step * index, thresholds[thresholds.length - 1]);
    }

    private JsonObject createSelectCase(String when, JsonObject model) {
        JsonObject selectCase = new JsonObject();
        selectCase.addProperty("when", when);
        selectCase.add("model", model);
        return selectCase;
    }

    private JsonObject createModelReference(String model) {
        JsonObject modelReference = new JsonObject();
        modelReference.addProperty("type", "minecraft:model");
        modelReference.addProperty("model", model);
        return modelReference;
    }

    private String getBaseModelPath() {
        return oraxenMeta.getGeneratedModelPath() + oraxenMeta.getModelName();
    }
}
