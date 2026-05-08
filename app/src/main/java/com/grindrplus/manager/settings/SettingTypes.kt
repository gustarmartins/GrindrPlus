package com.grindrplus.manager.settings

data class SwitchSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    var isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
) : Setting(id, title)

sealed class Setting(open val id: String, open val title: String)

data class TextSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    var value: String,
    val onValueChange: (String) -> Unit,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val validator: ((String) -> String?)? = null,
) : Setting(id, title)

data class ButtonAction(
    val name: String,
    val action: () -> String?
)

data class TextSettingWithButtons(
    override val id: String,
    override val title: String,
    val description: String? = null,
    var value: String,
    val onValueChange: (String) -> Unit,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val validator: ((String) -> String?)? = null,
    val buttons: List<ButtonAction> = emptyList()
) : Setting(id, title)

data class ButtonSetting(
    override val id: String,
    override val title: String,
    val onClick: () -> Unit,
) : Setting(id, title)

data class TriStateSetting(
    override val id: String,
    override val title: String,
    val description: String? = null,
    var state: FeatureState,
    val onStateChange: (FeatureState) -> Unit,
) : Setting(id, title)

enum class FeatureState(val configValue: String) {
    OFF("off"),
    DEFAULT("default"),
    ON("on");

    companion object {
        fun fromConfig(value: String): FeatureState = when (value) {
            "on" -> ON
            "off" -> OFF
            "default" -> DEFAULT
            else -> DEFAULT
        }
        fun fromLegacyBoolean(value: Boolean): FeatureState = if (value) ON else OFF
    }
}

data class SettingGroup(
    val id: String,
    val title: String,
    val settings: List<Setting> = emptyList(),
    val defaultCollapsed: Boolean = false,
    val collapsable: Boolean = true,
    val subGroups: List<SettingGroup> = emptyList(),
    val headerSetting: Setting? = null,
    val isDisabled: Boolean = false,
)

enum class KeyboardType {
    Text, Number, Email, Password, Phone
}