package ru.projectst.rpgcore.client;

import java.util.Map;

final class ScenesBerserker {

    private ScenesBerserker() {
    }

    private static final Map<String, FxScenes.MarkScene> MARKS = Map.ofEntries(
            Map.entry("berserker_quake_mark", e -> SceneKit.mark(e, SceneKit.MarkDecor.CRACK)));

    static FxScenes.Set scenes() {
        return new FxScenes.Set(Map.of(), MARKS, Map.of(), Map.of(), Map.of(), Map.of());
    }
}
