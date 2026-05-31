package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import com.grindrplus.ui.Utils
import com.grindrplus.utils.Feature
import com.grindrplus.utils.FeatureManager
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.getObjectField

class FeatureGranting : Hook(
    "Feature granting",
    "Grant all Grindr features"
) {
    private val isFeatureFlagEnabled = "q30.f" // search for 'implements IsFeatureFlagEnabled {'
    private val upsellsV8Model = "com.grindrapp.android.model.UpsellsV8"
    private val insertsModel = "com.grindrapp.android.model.Inserts"
    private val settingDistanceVisibilityViewModel =
        "com.grindrapp.android.ui.settings.distance.a\$e" // search for 'UiState(distanceVisibility='
    private val featureModel = "com.grindrapp.android.usersession.model.Feature"
    private val tapModel = "com.grindrapp.android.taps.model.Tap"
    private val tapInboxModel = "com.grindrapp.android.taps.data.model.TapsInboxEntity"
    private val alertParams = "P" // search for 'AlertController.AlertParams' in androidx.appcompat.app.AlertDialog
    private val featureManager = FeatureManager()

    override fun init() {
        initFeatures()

		// search for 'Assignment.Flag'
        findClass(isFeatureFlagEnabled).hook("a", HookStage.BEFORE) { param ->
            val flag = param.args()[0]
            val key = runCatching { callMethod(flag, "getKey") as String }.getOrNull()
            val label = runCatching { callMethod(flag, "toString") as String }.getOrNull()
            val managed = listOfNotNull(key, label).firstOrNull { featureManager.isManaged(it) }

            if (managed != null) {
                param.setResult(featureManager.getForcedValue(managed))
            }
        }

        findClass(featureModel).hook("isGranted", HookStage.BEFORE) { param ->
            val disallowedFeatures = setOf("DisableScreenshot")
            val feature = callMethod(param.thisObject(), "toString") as String
            param.setResult(feature !in disallowedFeatures)
        }

        findClass(settingDistanceVisibilityViewModel)
            .hookConstructor(HookStage.BEFORE) { param ->
                param.setArg(4, false) // hidePreciseDistance
            }

        listOf(upsellsV8Model, insertsModel).forEach { model ->
            findClass(model)
                .hook("getMpuFree", HookStage.BEFORE) { param ->
                    param.setResult(0)
                }

            findClass(model)
                .hook("getMpuXtra", HookStage.BEFORE) { param ->
                    param.setResult(0)
                }
        }

        listOf(tapModel, tapInboxModel).forEach { model ->
            findClass(model).hook("isViewable", HookStage.BEFORE) { param ->
                param.setResult(true)
            }
        }

        val boostAlertStringId = Utils.getId(
            "incognito_while_boosting_confilct_warning_message",
            "string",
            GrindrPlus.context
        )

        val boostAlertString = GrindrPlus.context.resources.getString(boostAlertStringId)

        findClass("androidx.appcompat.app.AlertDialog\$Builder")
            .hook("show", HookStage.BEFORE) { param ->
                val builder = param.thisObject()
                val alertParams = getObjectField(builder, alertParams)
                val messageString = getObjectField(alertParams, "mMessage")

                if (messageString.equals(boostAlertString)) {
                    val dialog = callMethod(builder, "create")
                    val positiveButtonListener = getObjectField(alertParams, "mPositiveButtonListener")

                    val positiveButtonId = XposedHelpers.getStaticIntField(
                        findClass("android.content.DialogInterface"),
                        "BUTTON_POSITIVE"
                    )

                    callMethod(positiveButtonListener, "onClick", dialog, positiveButtonId)

                    param.setResult(dialog)
                }
        }
    }

    private fun initFeatures() {
        featureManager.loadFromDefinitions()
        featureManager.add(Feature("CookieTap", Config.get("enable_cookie_tap", false, true) as Boolean))
        featureManager.add(Feature("VipFlag", Config.get("enable_vip_flag", false, true) as Boolean))
        featureManager.add(Feature("enable-mutual-taps-no-paywall", !(Config.get("enable_interest_section", true, true) as Boolean)))
        featureManager.add(Feature("side-profile-link", Config.get("side_profile_link", false, true) as Boolean))
    }
}