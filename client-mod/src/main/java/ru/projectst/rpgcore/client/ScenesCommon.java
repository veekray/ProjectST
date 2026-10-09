package ru.projectst.rpgcore.client;

import java.util.Map;

final class ScenesCommon {

    private ScenesCommon() {
    }

    static FxScenes.Set scenes() {
        return new FxScenes.Set(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
