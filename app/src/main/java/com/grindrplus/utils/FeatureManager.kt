package com.grindrplus.utils

import com.grindrplus.core.Config
import com.grindrplus.core.FeatureDefinitions
import com.grindrplus.manager.settings.FeatureState

data class Feature(val name: String, var state: FeatureState) {
    /**
     * Convenience constructor for legacy call sites that still use boolean.
     */
    constructor(name: String, enabled: Boolean) : this(
        name,
        FeatureState.fromLegacyBoolean(enabled)
    )

    val isEnabled: Boolean get() = state == FeatureState.ON
}

class FeatureManager {
    private val features = mutableMapOf<String, Feature>()

    fun add(feature: Feature) {
        features[feature.name] = feature
    }

    fun loadFromDefinitions() {
        for (def in FeatureDefinitions.ALL) {
            val configKey = FeatureDefinitions.configKey(def.name)
            val rawValue = Config.get(configKey, def.defaultState.configValue)
            val state = when (rawValue) {
                // Legacy migration: old configs stored booleans
                is Boolean -> FeatureState.fromLegacyBoolean(rawValue)
                is String -> FeatureState.fromConfig(rawValue)
                else -> def.defaultState
            }
            add(Feature(def.name, state))
        }
    }

    /**
     * Returns true only if [featureName] is managed AND its state is ON.
     */
    fun isEnabled(featureName: String): Boolean {
        return features[featureName]?.isEnabled ?: false
    }

    /**
     * Returns true if [featureName] is managed AND its state is NOT DEFAULT.
     * When state is DEFAULT, the hook should let Grindr's own value pass through.
     */
    fun isManaged(featureName: String): Boolean {
        val feature = features[featureName] ?: return false
        return feature.state != FeatureState.DEFAULT
    }

    /**
     * For flags where isManaged() is true, returns the boolean value to force.
     */
    fun getForcedValue(featureName: String): Boolean {
        return features[featureName]?.isEnabled ?: false
    }
}