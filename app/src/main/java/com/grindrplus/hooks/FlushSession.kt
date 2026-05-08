package com.grindrplus.hooks

import android.widget.Toast
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers

/**
 * Attempt to force a (POST /v8/sessions) after teleporting without dirty workarounds
 * Needed so cascade on Grindr's servers updates you at the new coordinates set
 */
class FlushSession : Hook("Flush session", "Allows instant session refresh after teleporting.") {
    companion object {
        var refreshSessionUseCases: Any? = null
        private const val CLASS_REFRESH_SESSION = "rz.o3"
        //search RefreshSessionUseCases.Param.JoinPreviousOrRun
        private const val CLASS_JOIN_PREVIOUS_OR_RUN = "rz.o3\$a\$b"
        private const val METHOD_EXECUTE = "a"

        fun refresh() {
            val useCases = refreshSessionUseCases
            if (useCases == null) {
                Logger.e("FlushSession: RefreshSessionUseCases not captured yet.")
                return
            }

            GrindrPlus.executeAsync {
                try {
                    val paramClass = GrindrPlus.classLoader.loadClass(CLASS_JOIN_PREVIOUS_OR_RUN)
                    val paramInstance = paramClass
                        .getConstructor(String::class.java)
                        .newInstance("TeleportRefresh")

                    val result = XposedHelpers.callMethod(
                        useCases, METHOD_EXECUTE, paramInstance
                    ) as Boolean

                    Logger.i("FlushSession: refresh result=$result")
                    GrindrPlus.showToast(Toast.LENGTH_SHORT, "Session flushed!")
                } catch (e: Exception) {
                    Logger.e("FlushSession: failed — ${e.message}")
                }
            }
        }
    }

    override fun init() {
        try {
            val clazz = findClass(CLASS_REFRESH_SESSION)
            clazz.hookConstructor(HookStage.AFTER) { param ->
                refreshSessionUseCases = param.thisObject()
                Logger.i("FlushSession: captured RefreshSessionUseCases instance.")
            }
        } catch (e: Exception) {
            Logger.e("FlushSession: failed to hook constructor — ${e.message}")
        }
    }
}