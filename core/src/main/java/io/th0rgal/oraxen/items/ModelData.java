package io.th0rgal.oraxen.items;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.config.Settings;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.*;

public record ModelData(Material type, int modelData) {

    public static final int STARTING_CMD = 1000;
    public static final Map<Material, Map<String, Integer>> DATAS = new HashMap<>();
    public static final Map<Material, Map<Integer, String>> REVERSED_DATAS = new HashMap<>();

    public ModelData(Material type, String model, int modelData) {
        this(type, modelData);

        Map<String, Integer> usedModelDatas = DATAS.getOrDefault(type, new HashMap<>());
        usedModelDatas.put(model, modelData);
        DATAS.put(type, usedModelDatas);

        updateReversedMap(type, model, modelData);
    }

    public ModelData(Material type, int modelData) {
        this.type = type;
        this.modelData = modelData;
    }

    private void updateReversedMap(Material type, String model, int modelData) {
        Map<Integer, String> reversedMap = REVERSED_DATAS.getOrDefault(type, new HashMap<>());
        reversedMap.put(modelData, model);
        REVERSED_DATAS.put(type, reversedMap);
    }

    public Material getType() {
        return type;
    }

    public int getModelData() {
        return modelData;
    }

    public static String getModelIdentifierFromModelData(Material type, int modelData) {
        return ItemParser.ID_BY_MODEL_DATA.get(new ModelData(type, modelData));
    }

    @Deprecated
    /**
     * @deprecated Use {@link #getModelIdentifierFromModelData(Material, int)} instead for applying models to items.
     */
    public static String getModelNameFromModelData(Material type, int modelData) {
        return REVERSED_DATAS.getOrDefault(type, new HashMap<>()).get(modelData);
    }

    public static NamespacedKey getModelNamespaceFromModelData(Material type, int modelData) {
        String id = getModelIdentifierFromModelData(type, modelData);
        if (id == null) return null;
        return new NamespacedKey(OraxenPlugin.get(), id);
    }

    public static int getModelDataFromNamespace(NamespacedKey namespacedKey, Material type) {
        if (namespacedKey == null || type == null) return -1;

        String modelName = namespacedKey.getKey();
        return getModelDataFromModelName(modelName, type);
    }

    public static int getModelDataFromModelName(String modelName, Material type) {
        if (modelName == null || type == null) return -1;

        Map<String, Integer> modelDatas = DATAS.getOrDefault(type, new HashMap<>());
        return modelDatas.getOrDefault(modelName, -1);
    }

    public static int generateId(String model, Material type) {
        Map<String, Integer> usedModelDatas = new HashMap<>();
        if (!DATAS.containsKey(type) && !getSkippedCustomModelData().contains(STARTING_CMD)) {
            usedModelDatas.put(model, STARTING_CMD);
            DATAS.put(type, usedModelDatas);

            Map<Integer, String> reversedMap = new HashMap<>();
            reversedMap.put(STARTING_CMD, model);
            REVERSED_DATAS.put(type, reversedMap);

            return STARTING_CMD;
        } else
            usedModelDatas = DATAS.getOrDefault(type, new HashMap<>());

        if (usedModelDatas.containsKey(model)) {
            return usedModelDatas.get(model);
        }

        if (usedModelDatas.isEmpty()) {
            int newModelData = STARTING_CMD;
            while (getSkippedCustomModelData().contains(newModelData)) {
                newModelData++;
            }
            usedModelDatas.put(model, newModelData);
            DATAS.put(type, usedModelDatas);

            Map<Integer, String> reversedMap = REVERSED_DATAS.getOrDefault(type, new HashMap<>());
            reversedMap.put(newModelData, model);
            REVERSED_DATAS.put(type, reversedMap);

            return newModelData;
        }

        int currentHighestModelData = Collections.max(usedModelDatas.values());
        for (int i = STARTING_CMD; i < currentHighestModelData; i++) {
            if (!usedModelDatas.containsValue(i)) { // if the id is available
                if (getSkippedCustomModelData().contains(i))
                    continue; // if the id should be skipped
                usedModelDatas.put(model, i);
                DATAS.put(type, usedModelDatas);

                Map<Integer, String> reversedMap = REVERSED_DATAS.getOrDefault(type, new HashMap<>());
                reversedMap.put(i, model);
                REVERSED_DATAS.put(type, reversedMap);

                return i;
            }
        }
        // if no durability was available between the chosen, let's create a new one
        // bigger
        int newHighestModelData = currentHighestModelData + 1;
        if (getSkippedCustomModelData().contains(newHighestModelData)) { // if the id should be skipped
            newHighestModelData = getNextNotSkippedCustomModelData(newHighestModelData);
        }

        usedModelDatas.put(model, newHighestModelData);
        DATAS.put(type, usedModelDatas);

        Map<Integer, String> reversedMap = REVERSED_DATAS.getOrDefault(type, new HashMap<>());
        reversedMap.put(newHighestModelData, model);
        REVERSED_DATAS.put(type, reversedMap);

        return newHighestModelData;
    }

    private static int getNextNotSkippedCustomModelData(int i) {
        List<Integer> sorted = new ArrayList<>(getSkippedCustomModelData());
        sorted.sort(Comparator.naturalOrder());
        return sorted.stream().filter(index -> index > i).toList().get(0);
    }

    private static Set<Integer> getSkippedCustomModelData() {
        Set<Integer> skippedCustomModelData = new HashSet<>();
        for (String s : Settings.SKIPPED_MODEL_DATA_NUMBERS.toStringList()) {
            if (s.contains("-")) {
                String[] s2 = s.split("-");
                int min = Integer.parseInt(s2[0]);
                int max = Integer.parseInt(s2[1]);
                for (int i = min; i <= max; i++)
                    skippedCustomModelData.add(i);
            } else
                skippedCustomModelData.add(Integer.parseInt(s));
        }
        return skippedCustomModelData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelData modelData1 = (ModelData) o;
        return getModelData() == modelData1.getModelData() && getType() == modelData1.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getType(), getModelData());
    }
}