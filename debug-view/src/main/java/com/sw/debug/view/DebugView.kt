package com.sw.debug.view

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings

import com.sw.debug.view.base.AbstractDebugModule


internal class DebugView(private val application: Application, private val debugModules: List<AbstractDebugModule<*>>, private val config: Config) {

    private var debugViewService: DebugViewService? = null

    private var debugViewManager: DebugViewManager? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as DebugViewService.LocalBinder
            debugViewService = binder.service
            debugViewService?.setDebugModules(debugModules)
            debugViewService?.setDebugViewManager(debugViewManager)
            debugViewService?.startModules()
        }

        override fun onServiceDisconnected(name: ComponentName) {}
    }

    fun show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(application)) {
                if (!config.isAlwaysShowOverlaySetting) {
                    val sp = application.getSharedPreferences("debug_view_config", Context.MODE_PRIVATE)
                    if (sp.getBoolean("is_showed_overlay_setting", false)) {
                        return
                    }
                    sp.edit().putBoolean("is_showed_overlay_setting", true).apply()
                }
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + application.packageName))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                application.startActivity(intent)
            }
        }
        debugViewManager = DebugViewManager(application, config)
        debugViewManager!!.setDebugModules(debugModules)
        startAndBindDebugService()
    }

    fun uninstall() {
        unbindFromDebugService()
        application.stopService(DebugViewService.createIntent(application))
    }

    private fun startAndBindDebugService() {
        val intent = Intent(application, DebugViewService::class.java)
        application.startService(intent)
        bindToDebugViewService()
    }

    private fun bindToDebugViewService() {
        val bound = application.bindService(DebugViewService.createIntent(application),
                serviceConnection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            throw RuntimeException("Could not bind the DebugOverlayService")
        }
    }

    private fun unbindFromDebugService() {
        if (debugViewService != null) {
            application.unbindService(serviceConnection)
            debugViewService = null
        }
    }


    internal class Config(val bgColor: Int, val viewWidth: Int, val logMaxLines: Int, val isAlwaysShowOverlaySetting: Boolean)
}
