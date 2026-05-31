package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.logd
import com.grindrplus.core.loge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.setObjectField
import com.grindrplus.core.Config

class DisableUpdates : Hook(
    "Disable updates",
    "Disable forced updates"
) {

    private val appUpdateInfo = "com.google.android.play.core.appupdate.AppUpdateInfo"
    private val appUpdateZzm = "com.google.android.play.core.appupdate.zzm" // search for 'requestUpdateInfo(%s)'
	private val appUpgradeManager = "rz.v" // search for 'Uri.parse("market://details?id=com.grindrapp.android");'
    private val appConfiguration = "com.grindrapp.android.platform.config.AppConfiguration"
    private var versionCode: Int = 0
    private var versionName: String = ""

    override fun init() {
        findClass(appUpdateInfo)
            .hook("updateAvailability", HookStage.BEFORE) { param ->
                param.setResult(1)
            }

        findClass(appUpdateInfo)
            .hook("isUpdateTypeAllowed", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

        findClass(appUpgradeManager) // showDeprecatedVersionDialog()
			// search for '.setMessage(R.string.deprecation_message);'
            .hook("b", HookStage.BEFORE) { param ->
                param.setResult(null)
            }

        findClass(appUpdateZzm) // requestUpdateInfo()
            .hook("zza", HookStage.BEFORE) { param ->
                param.setResult(null)
            }

        readConfigAndUpdate()
    }

    private fun readConfigAndUpdate() {
        val spoofedName = Config.get("spoofed_version_name", "26.9.1") as String
        val spoofedCodeStr = Config.get("spoofed_version_code", "163471") as String
        val spoofedCode = spoofedCodeStr.toIntOrNull() ?: 0

        if (spoofedName.isNotEmpty() && spoofedCode > 0) {
            versionName = spoofedName
            versionCode = spoofedCode
            updateVersionInfo()
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toInt() }
        val parts2 = v2.split(".").map { it.toInt() }
        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = if (i < parts1.size) parts1[i] else 0
            val part2 = if (i < parts2.size) parts2[i] else 0
            if (part1 != part2) return part1.compareTo(part2)
        }
        return 0
    }

    private fun updateVersionInfo() {
        val currentVersion = GrindrPlus.context.packageManager.getPackageInfo(
            GrindrPlus.context.packageName,
            0
        ).versionName.toString()

        if (compareVersions(versionName, currentVersion) > 0) {
            findClass(appConfiguration).hookConstructor(HookStage.AFTER) { param ->
                setObjectField(param.thisObject(), "d", "$versionName.$versionCode")
            }

            findClass(GrindrPlus.userAgent).hookConstructor(HookStage.AFTER) { param ->
                param.thisObject().javaClass.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val value = field.get(param.thisObject())
                    if (value is String && value.startsWith("grindr3/")) {
                        field.set(param.thisObject(), "grindr3/$versionName.$versionCode;$versionCode;")
                        return@forEach
                    }
                }
            }
        } else {
            logd("Current version is up-to-date: $versionName ($versionCode)")
        }
    }
}