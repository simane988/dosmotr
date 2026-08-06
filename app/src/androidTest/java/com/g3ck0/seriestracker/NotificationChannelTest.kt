package com.g3ck0.seriestracker

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests run against the real [SeriesTrackerApp], so its `onCreate` has already
 * run by the time this executes — which is the only thing that creates the channel. A
 * notification posted to a channel that does not exist is dropped without a trace, so this
 * is worth an assertion rather than a look in the system settings.
 */
@RunWith(AndroidJUnit4::class)
class NotificationChannelTest {

    @Test
    fun episodesChannelIsCreatedOnStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)

        val channel = manager.getNotificationChannel(SeriesTrackerApp.EPISODES_CHANNEL_ID)

        assertNotNull("the episodes channel is missing", channel)
        assertEquals("Новые серии", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }
}
