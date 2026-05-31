package com.grindrplus.manager.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grindrplus.core.Config
import com.grindrplus.core.FeatureDefinitions
import com.grindrplus.manager.DATA_URL
import com.grindrplus.manager.settings.SettingsUtils.testMapsApiKey
import com.grindrplus.manager.utils.AppIconManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class SettingsViewModel(
    private val context: Context,
) : ViewModel() {
    private val hookHideList = setOf(
        "Status Dialog",
        "Feature granting",
    )

    private val _settingGroups = MutableStateFlow<List<SettingGroup>>(emptyList())
    val settingGroups: StateFlow<List<SettingGroup>> = _settingGroups

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _showApiKeyTestDialog = MutableStateFlow(false)
    val showApiKeyTestDialog: StateFlow<Boolean> = _showApiKeyTestDialog

    private val _apiKeyTestTitle = MutableStateFlow("")
    val apiKeyTestTitle: StateFlow<String> = _apiKeyTestTitle

    private val _apiKeyTestMessage = MutableStateFlow("")
    val apiKeyTestMessage: StateFlow<String> = _apiKeyTestMessage

    private val _apiKeyTestRawResponse = MutableStateFlow("")
    val apiKeyTestRawResponse: StateFlow<String> = _apiKeyTestRawResponse

    private val _apiKeyTestLoading = MutableStateFlow(false)
    val apiKeyTestLoading: StateFlow<Boolean> = _apiKeyTestLoading

    fun dismissApiKeyTestDialog() {
        _showApiKeyTestDialog.value = false
    }

    private fun showApiKeyTestDialog(
        isLoading: Boolean,
        title: String,
        message: String,
        rawResponse: String
    ) {
        _apiKeyTestLoading.value = isLoading
        _apiKeyTestTitle.value = title
        _apiKeyTestMessage.value = message
        _apiKeyTestRawResponse.value = rawResponse
        _showApiKeyTestDialog.value = true
    }

    private var isInitialLoad = true

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            if (isInitialLoad) {
                _isLoading.value = true
            }

            try {
                Config.initialize(Config.getCurrentPackage())
                val hooks = Config.getHooksSettings()
                val hookSettings = hooks
                    .filterNot { (hookName, _) -> hookName in hookHideList }
                    .map { (hookName, pair) ->
                        SwitchSetting(
                            id = hookName,
                            title = hookName,
                            description = pair.first,
                            isChecked = pair.second,
                            onCheckedChange = { viewModelScope.launch { Config.setHookEnabled(hookName, it) } }
                        )
                    }

                val tasks = Config.getTasksSettings()
                val taskSettings = tasks.map { (taskId, pair) ->
                    SwitchSetting(
                        id = taskId,
                        title = taskId,
                        description = pair.first,
                        isChecked = pair.second,
                            onCheckedChange = { viewModelScope.launch { Config.setTaskEnabled(taskId, it) } }
                    )
                } + tasks.map { (taskId, _) ->
                    ButtonSetting(
                        id = "trigger_${taskId.lowercase().replace(" ", "_")}",
                        title = "Trigger $taskId Now",
                        onClick = {
                            val intent = android.content.Intent("com.grindrplus.TRIGGER_TASK").apply {
                                setPackage("com.grindrapp.android")
                                putExtra("taskId", taskId)
                            }
                            context.sendBroadcast(intent)
                        }
                    )
                } + listOf(
                    TextSetting(
                        id = "always_online_interval_mins",
                        title = "Always Online interval (mins)",
                        description = "How often to fetch cascade to stay online (default: 5)",
                        value = (Config.get("always_online_interval_mins", 5) as Number).toString(),
                        onValueChange = {
                            val value = it.toIntOrNull() ?: 5
                            viewModelScope.launch { Config.put("always_online_interval_mins", value) }
                        },
                        keyboardType = KeyboardType.Number,
                        validator = { input ->
                            val value = input.toIntOrNull()
                            when {
                                value == null || value <= 0 -> "Must be a positive number"
                                else -> null
                            }
                        }
                )) 
                val experimentalUiSettings = listOf(
                    SwitchSetting(
                        id = "home_navigation_v2",
                        title = "Home Navigation 2.0",
                        description = "Enable the new navigation bar (This causes the duplicate bars?)",
                        isChecked = Config.get("home_navigation_v2", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("home_navigation_v2", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "discover_v2",
                        title = "Discover UI",
                        description = "Enable the Discover tab UI; server-side feature. Not working.",
                        isChecked = Config.get("discover_v2", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("discover_v2", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "side_profile_link",
                        title = "Side Profile Link",
                        description = "Enable the new side profile drawer. Unsure of what it does.",
                        isChecked = Config.get("side_profile_link", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("side_profile_link", it) }
                        }
                    )
                )

                val otherSettings = mutableListOf(
                    TextSetting(
                        id = "spoofed_version_name",
                        title = "Spoofed Version Name",
                            description = "Simulate a specific Grindr version (default: 26.9.1). Clear to disable spoofing.",
                            value = Config.get("spoofed_version_name", "26.9.1") as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("spoofed_version_name", it) }
                        }
                    ),
                    TextSetting(
                        id = "spoofed_version_code",
                        title = "Spoofed Version Code",
                            description = "Version code corresponding to the name (default: 163471).",
                            value = Config.get("spoofed_version_code", "163471") as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("spoofed_version_code", it) }
                        },
                        keyboardType = KeyboardType.Number
                    ),
                    TextSetting(
                        id = "command_prefix",
                        title = "Command Prefix",
                        description = "Change the command prefix (default: /)",
                        value = Config.get("command_prefix", "/") as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("command_prefix", it) }
                        },
                        validator = { input ->
                            when {
                                input.isBlank() -> "Invalid command prefix"
                                input.length > 1 -> "Command prefix must be a single character"
                                !input.matches(Regex("[^a-zA-Z0-9]")) -> "Command prefix must be a special character"
                                else -> null
                            }
                        }
                    ),
                    TextSetting(
                        id = "date_format",
                        title = "Date Format",
                        description = "Format for displaying dates in the app (default: MM/dd/yyyy)",
                        value = Config.get("date_format", "MM/dd/yyyy") as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("date_format", it) }
                        },
                        validator = { input ->
                            when {
                                input.isBlank() -> "Date format cannot be empty"
                                !input.contains("MM") && !input.contains("M") -> "Format must include month (M or MM)"
                                !input.contains("dd") && !input.contains("d") -> "Format must include day (d or dd)"
                                !input.contains("yyyy") && !input.contains("yy") -> "Format must include year (yy or yyyy)"
                                else -> null
                            }
                        }
                    ),
                    TextSetting(
                        id = "online_indicator",
                        title = "Online indicator duration (mins)",
                        description = "Control when the green dot disappears after inactivity",
                        value = (Config.get("online_indicator", 5) as Number).toString(),
                        onValueChange = {
                            val value = it.toIntOrNull() ?: 5
                            viewModelScope.launch { Config.put("online_indicator", value) }
                        },
                        keyboardType = KeyboardType.Number,
                        validator = { input ->
                            val value = input.toIntOrNull()
                            if (value == null || value <= 0) "Duration must be a positive number" else null
                        }
                    ),
                    SwitchSetting(
                        id = "show_bmi_in_profile",
                        title = "Show BMI in Profile",
                        description = "Display BMI in the profile section",
                        isChecked = Config.get("show_bmi_in_profile", true) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("show_bmi_in_profile", it) }
                        }
                    ),
                    TextSetting(
                        id = "favorites_grid_columns",
                        title = "Favorites grid columns",
                        description = "Number of columns in the favorites grid (default: 3)",
                        value = Config.get("favorites_grid_columns", 3).toString(),
                        onValueChange = {
                            val value = it.toIntOrNull() ?: 3
                            viewModelScope.launch { Config.put("favorites_grid_columns", value) }
                        },
                        keyboardType = KeyboardType.Number,
                        validator = { input ->
                            val value = input.toIntOrNull()
                            if (value == null || value <= 0) "Number of columns must be a positive number" else null
                        }
                    ),
                    TextSettingWithButtons(
                        id = "android_device_id",
                        title = "Android Device ID",
                        description = "Change the Android Device ID",
                        value = Config.get("android_device_id", "") as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("android_device_id", it) }
                        },
                        validator = { input ->
                            when {
                                input.isBlank() -> null
                                input.length != 16 -> "Android Device ID must be 16 characters long"
                                !input.matches(Regex("[0-9a-fA-F]+")) -> "Android Device ID must be a hexadecimal string"
                                else -> null
                            }
                        },
                        buttons = listOf(
                            ButtonAction("Generate") {
                                val uuid = java.util.UUID.randomUUID()
                                val newDeviceId = uuid.toString().replace("-", "").substring(0, 16)
                                Config.put("android_device_id", newDeviceId)
                                Toast.makeText(context, "New device ID generated", Toast.LENGTH_SHORT).show()
                                newDeviceId
                            }
                        )

                    ),
                    SwitchSetting(
                        id = "enable_cookie_tap",
                        title = "Enable Cookie Tap",
                        description = "Enable the ability to send cookie taps to other users (they'll see them)",
                        isChecked = Config.get("enable_cookie_tap", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("enable_cookie_tap", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "enable_vip_flag",
                        title = "Enable Star Section",
                        description = "Enables what looks like a recommendation section next to Browse",
                        isChecked = Config.get("enable_vip_flag", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("enable_vip_flag", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "enable_interest_section",
                        title = "Enable Interest Section",
                        description = "Show interests section on profiles",
                        isChecked = Config.get("enable_interest_section", true) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("enable_interest_section", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "disable_profile_swipe",
                        title = "Disable profile swipe",
                        description = "Disable profile swipe and open profile on click",
                        isChecked = Config.get("disable_profile_swipe", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("disable_profile_swipe", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "force_old_anti_block_behavior",
                        title = "Force old AntiBlock behavior",
                        description = "Use the old AntiBlock behavior (don't use this, required for testing)",
                        isChecked = Config.get("force_old_anti_block_behavior", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("force_old_anti_block_behavior", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "anti_block_use_toasts",
                        title = "Use toasts for AntiBlock hook",
                        description = "Instead of receiving Android notifications, use toasts for block/unblock notifications",
                        isChecked = Config.get("anti_block_use_toasts", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("anti_block_use_toasts", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "reset_database",
                        title = "Reset local database on next start",
                        description = "Will delete all local data on next app start",
                        isChecked = Config.get("reset_database", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("reset_database", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "do_gui_safety_checks",
                        title = "Do GUI safety checks",
                        description = "Prevent graphic glitches when applying GUI based hooks",
                        isChecked = Config.get("do_gui_safety_checks", true) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("do_gui_safety_checks", it) }
                        }
                    )
                )

                val managerSettings = mutableListOf<Setting>(
                    TextSetting(
                        id = "news_fetch_interval_hours",
                        title = "News Fetch Interval (hours)",
                        description = "How often to check for news in the background. Set to 0 to disable automatic fetching (manual only).",
                        value = (Config.get("news_fetch_interval_hours", 6) as Number).toString(),
                        onValueChange = {
                            val value = it.toLongOrNull() ?: 6L
                            viewModelScope.launch { Config.put("news_fetch_interval_hours", value) }
                        },
                        keyboardType = KeyboardType.Number,
                        validator = { input ->
                            val value = input.toLongOrNull()
                            if (value == null || value < 0) "Must be 0 (manual only) or a positive number" else null
                        }
                    ),
                    TextSettingWithButtons(
                        id = "maps_api_key",
                        title = "Maps API Key",
                        description = "Use a custom Maps API Key when using Grindr Plus with LSPatch",
                        value = Config.get("maps_api_key", "") as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("maps_api_key", it) }
                        },
                        validator = { null },
                        buttons = listOf(
                            ButtonAction("Test") {
                                val apiKey = Config.get("maps_api_key", "") as String
                                if (apiKey.isBlank()) {
                                    Toast.makeText(context, "Please enter an API key first", Toast.LENGTH_SHORT).show()
                                } else {
                                    testMapsApiKey(
                                        context,
                                        viewModelScope,
                                        apiKey,
                                        ::showApiKeyTestDialog
                                    )
                                }
                                null
                            }
                        )
                    ),
                    TextSetting(
                        id = "custom_manifest",
                        title = "Custom Manifest URL",
                        description = "Use a custom manifest URL when using Grindr Plus with LSPatch",
                        value = Config.get("custom_manifest", DATA_URL) as String,
                        onValueChange = {
                            viewModelScope.launch { Config.put("custom_manifest", it) }
                        },
                        validator = { null }
                    ),
                    SwitchSetting(
                        id = "analytics",
                        title = "Opt-in analytics",
                        description = "Help improve the app by sending anonymous usage data",
                        isChecked = Config.get("analytics", true) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("analytics", it) }
                        }
                    ),
                    SwitchSetting(
                        id = "discreet_icon",
                        title = "Camouflage app",
                        description = "Hide the app icon and use a different name",
                        isChecked = Config.get("discreet_icon", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch {
                                Config.put("discreet_icon", it)

                                val appIconManager = AppIconManager(context)
                                appIconManager.changeAppIcon(if (it) AppIconManager.DISCREET_ICON else AppIconManager.DEFAULT_ICON)

                                Toast.makeText(
                                    context,
                                    "App icon changed. It may take a moment to update.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ),
                    SwitchSetting(
                        id = "disable_permission_checks",
                        title = "Disable permission checks",
                        description = "Disable permission checks on startup (not recommended)",
                        isChecked = Config.get("disable_permission_checks", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("disable_permission_checks", it) }
                        }
                    )
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    managerSettings += SwitchSetting(
                        id = "material_you",
                        title = "Enable dynamic colors",
                        description = "Use Material You colors for the app\nRestart the app to apply changes",
                        isChecked = Config.get("material_you", false) as Boolean,
                        onCheckedChange = {
                            viewModelScope.launch { Config.put("material_you", it) }
                        }
                    )
                }

                // Read the Feature Granting hook state.
                val featureGrantingEnabled = hooks["Feature granting"]?.second ?: true

                // Build the Feature Granting master toggle.
                val featureGrantingToggle = SwitchSetting(
                    id = "hook_feature_granting",
                    title = "Feature Granting",
                    description = "Grant all Grindr features. Disable to use the app normally.",
                    isChecked = featureGrantingEnabled,
                    onCheckedChange = {
                        viewModelScope.launch {
                            Config.setHookEnabled("Feature granting", it)
                            loadSettings()
                        }
                    }
                )

                // Build per-category sub-groups for feature flags.
                val flagsByCategory = FeatureDefinitions.ALL
                    .sortedWith(compareBy({ it.category }, { it.name }))
                    .groupBy { it.category }

                val featureFlagSubGroups = flagsByCategory.map { (category, defs) ->
                    SettingGroup(
                        id = "feature_flags_${category.lowercase().replace(" ", "_")}",
                        title = "$category (${defs.size})",
                        settings = defs.map { def ->
                            val configKey = FeatureDefinitions.configKey(def.name)
                            val rawValue = Config.get(configKey, def.defaultState.configValue)
                            val currentState = when (rawValue) {
                                is Boolean -> FeatureState.fromLegacyBoolean(rawValue)
                                is String -> FeatureState.fromConfig(rawValue)
                                else -> def.defaultState
                            }
                            TriStateSetting(
                                id = configKey,
                                title = def.name,
                                description = def.description,
                                state = currentState,
                                onStateChange = { newState ->
                                    viewModelScope.launch {
                                        Config.put(configKey, newState.configValue)
                                    }
                                }
                            )
                        },
                        defaultCollapsed = true
                    )
                }

                _settingGroups.value = listOf(
                    SettingGroup(
                        id = "hooks",
                        title = "Manage Hooks",
                        settings = hookSettings,
                        collapsable = false
                    ),
                    SettingGroup(
                        id = "tasks",
                        title = "Manage Tasks",
                        settings = taskSettings,
                        collapsable = false
                    ),
                    SettingGroup(
                        id = "experimental_ui",
                        title = "Experimental UI",
                        settings = experimentalUiSettings,
                        collapsable = false
                    ),
                    SettingGroup(
                        id = "other",
                        title = "Other Settings",
                        settings = otherSettings,
                        collapsable = false
                    ),
                    SettingGroup(
                        id = "manager",
                        title = "Manager Settings",
                        settings = managerSettings,
                        collapsable = false
                    ),
                    SettingGroup(
                        id = "feature_flags_debug",
                        title = "Feature Flags Debug",
                        defaultCollapsed = true,
                        headerSetting = featureGrantingToggle,
                        subGroups = featureFlagSubGroups,
                        isDisabled = !featureGrantingEnabled
                    ),
                )
            } finally {
                _isLoading.value = false
                isInitialLoad = false
            }
        }
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun rememberViewModel(): SettingsViewModel {
    val context = LocalContext.current
    val factory = remember(context) { SettingsViewModelFactory(context) }
    return viewModel(factory = factory)
}