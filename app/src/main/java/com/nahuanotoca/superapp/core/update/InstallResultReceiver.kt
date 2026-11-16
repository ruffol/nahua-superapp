package com.nahuanotoca.superapp.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * Receives the result of a PackageInstaller session. The installer needs a mutable PendingIntent so
 * the system can deliver STATUS_PENDING_USER_ACTION with the confirmation activity.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmation) }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(context, "Nahua actualizada", Toast.LENGTH_SHORT).show()
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "instalación cancelada"
                Toast.makeText(context, "No se pudo instalar: " + message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.nahuanotoca.superapp.INSTALL_RESULT"
        const val EXTRA_SESSION_ID = "session_id"
    }
}