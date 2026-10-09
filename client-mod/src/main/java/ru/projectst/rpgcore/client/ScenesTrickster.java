package ru.projectst.rpgcore.client;

import java.util.Map;

final class ScenesTrickster {

    private ScenesTrickster() {
    }

    private static final Map<String, FxScenes.MarkScene> MARKS = Map.ofEntries(
            Map.entry("trickster_shuffle_mark", e -> SceneKit.mark(e, SceneKit.MarkDecor.PLAIN)));

    static FxScenes.Set scenes() {
        return new FxScenes.Set(Map.of(), MARKS, Map.of(), Map.of(), Map.of(), Map.of());
    }
}
