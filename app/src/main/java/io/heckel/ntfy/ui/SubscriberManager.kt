package io.heckel.ntfy.ui

import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.heckel.ntfy.msg.SubscriberService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * This class only manages the SubscriberService, i.e. it starts or stops it.
 * It's used in multiple activities.
 */
class SubscriberManager(private val activity: ComponentActivity) {
    fun refreshService(subscriptionIdsWithInstantStatus: Set<Pair<Long, Boolean>>) {
        Log.d(MainActivity.TAG, "Triggering subscriber service refresh")
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val instantSubscriptions = subscriptionIdsWithInstantStatus.toList().filter { (_, instant) -> instant }.size
            if (instantSubscriptions == 0) {
                performActionOnSubscriberService(SubscriberService.Actions.STOP)
            } else {
                performActionOnSubscriberService(SubscriberService.Actions.START)
            }
        }
    }

    private fun performActionOnSubscriberService(action: SubscriberService.Actions) {
        val serviceState = SubscriberService.readServiceState(activity)
        if (serviceState == SubscriberService.ServiceState.STOPPED && action == SubscriberService.Actions.STOP) {
            return
        }
        val intent = Intent(activity, SubscriberService::class.java)
        intent.action = action.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(MainActivity.TAG, "Performing SubscriberService action: ${action.name} (as foreground service, API >= 26)")
            activity.startForegroundService(intent)
        } else {
            Log.d(MainActivity.TAG, "Performing SubscriberService action: ${action.name} (as background service, API >= 26)")
            activity.startService(intent)
        }
    }
}
