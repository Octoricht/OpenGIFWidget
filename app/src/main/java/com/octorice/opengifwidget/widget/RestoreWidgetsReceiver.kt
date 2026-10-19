package com.octorice.opengifwidget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Some phones (Xiaomi, Samsung, Huawei, OnePlus...) stop apps aggressively to save battery.
 * If a widget lost its picture because of that, this puts it back: Android calls it right after
 * the phone starts and right after the app is updated, and it redraws every widget.
 */
class RestoreWidgetsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Redrawing reads settings and sends frame addresses: quick, but not instant,
                // so keep the receiver alive until it's done.
                val pending = goAsync()
                Thread {
                    try {
                        GifWidgetProvider.updateAll(context.applicationContext)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }
    }
}
