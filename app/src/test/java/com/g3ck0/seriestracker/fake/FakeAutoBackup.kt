package com.g3ck0.seriestracker.fake

import com.g3ck0.seriestracker.data.backup.AutoBackupScheduler
import com.g3ck0.seriestracker.data.backup.AutoBackupStorage
import com.g3ck0.seriestracker.data.backup.BackupFolder
import com.g3ck0.seriestracker.data.backup.BackupSource

/**
 * The three things the automatic backup talks to, in memory.
 *
 * The point of the interfaces is exactly this: rotation and the scheduling rules are
 * decisions, and a decision should not need a document provider, a WorkManager and a
 * database to be checked. What the real implementations do is covered by the instrumented
 * `AutoBackupFileTest` and `AutoBackupSchedulerTest`.
 */
class FakeBackupSource(
    var titles: Int = 1,
    var json: String = """{"version":1,"titles":[]}""",
    var fileName: String = "dosmotr-2026-08-06.json",
) : BackupSource {

    override fun suggestedFileName(): String = fileName

    override suspend fun titleCount(): Int = titles

    override suspend fun exportToJson(): String = json
}

/** A folder that keeps its files in a map, name to content. */
class FakeBackupFolder(
    override val label: String = "Память приложения",
    override val isPrivate: Boolean = true,
    files: Map<String, String> = emptyMap(),
) : BackupFolder {

    val files: MutableMap<String, String> = files.toMutableMap()

    /** Set to make the next write throw, the way a folder that has gone away would. */
    var failOnWrite: Boolean = false

    override suspend fun list(): List<String> = files.keys.toList()

    override suspend fun write(name: String, content: String) {
        if (failOnWrite) error("Папка недоступна")
        files[name] = content
    }

    override suspend fun delete(name: String) {
        files.remove(name)
    }
}

class FakeAutoBackupStorage(var folder: FakeBackupFolder = FakeBackupFolder()) : AutoBackupStorage {

    var chosenFolder: String? = null

    override suspend fun openFolder(): BackupFolder = folder

    override suspend fun useFolder(folderUri: String?) {
        chosenFolder = folderUri
    }
}

class FakeAutoBackupScheduler : AutoBackupScheduler {

    var scheduled: Boolean = false
        private set

    var scheduleCalls: Int = 0
        private set

    var cancelCalls: Int = 0
        private set

    override fun schedule() {
        scheduled = true
        scheduleCalls++
    }

    override fun cancel() {
        scheduled = false
        cancelCalls++
    }
}
