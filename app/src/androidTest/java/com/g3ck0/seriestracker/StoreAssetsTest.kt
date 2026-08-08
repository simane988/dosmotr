package com.g3ck0.seriestracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.g3ck0.seriestracker.data.backup.BackupRepository
import com.g3ck0.seriestracker.data.backup.BackupRepository.ImportMode
import com.g3ck0.seriestracker.data.local.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The device-side half of `scripts/screenshots.sh`: it fills the real library with the
 * demo data the store screenshots are taken of, and renders the launcher icon at the size
 * Google Play asks for.
 *
 * Neither is a test of the app, which is why both are gated on an instrumentation
 * argument and skipped when it is absent — `connectedDirectDebugAndroidTest` in CI runs
 * this class and does nothing. The script passes the arguments through
 * `am instrument`, deliberately rather than through Gradle: the Gradle task uninstalls
 * both APKs when it finishes, which would take the freshly seeded library with it.
 *
 * Seeding goes through [BackupRepository.importFromJson] rather than through the DAO so
 * that `store/demo-library.json` is exercised by the same parser, the same merge rules and
 * the same status derivation as a backup a user restores by hand. A broken demo file fails
 * here instead of producing screenshots of an empty library.
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetsTest {

    private val arguments = InstrumentationRegistry.getArguments()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun seedsTheDemoLibrary() = runTest {
        val path = arguments.getString(ARG_DEMO_LIBRARY)
        assumeNotNull("no -e $ARG_DEMO_LIBRARY, nothing to seed", path)

        val file = File(path!!)
        assertTrue("demo library not on the device: $file", file.exists())

        // The app's own database, not an in-memory one — the point is what the next launch
        // shows. Opened the way AppModule opens it, migrations included.
        @Suppress("SpreadOperator")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            val result = BackupRepository(context, db, db.trackerDao())
                .importFromJson(file.readText(), ImportMode.REPLACE)
            assertTrue("the demo file added no titles", result.titlesAdded > 0)
        } finally {
            db.close()
        }
    }

    /**
     * The launcher icon is an adaptive one, so there is no PNG in the repository to hand to
     * a store. Drawing both layers over the full square — rather than letting
     * [AdaptiveIconDrawable] apply its mask — is what Play wants: the mask is theirs to
     * apply, and a pre-rounded icon comes out clipped twice.
     */
    @Test
    fun rendersTheLauncherIcon() {
        val path = arguments.getString(ARG_ICON_OUT)
        assumeNotNull("no -e $ARG_ICON_OUT, nothing to render", path)

        val icon: Drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (icon is AdaptiveIconDrawable) {
            listOfNotNull(icon.background, icon.foreground).forEach { layer ->
                layer.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
                layer.draw(canvas)
            }
        } else {
            icon.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
            icon.draw(canvas)
        }

        val out = File(path!!)
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        assertTrue("nothing written to $out", out.length() > 0)
    }

    private companion object {
        const val ARG_DEMO_LIBRARY = "demoLibrary"
        const val ARG_ICON_OUT = "iconOut"

        /** What Google Play asks for; RuStore accepts the same file. */
        const val ICON_SIZE = 512

        /** Ignored for PNG, but the parameter is not optional. */
        const val PNG_QUALITY = 100
    }
}
