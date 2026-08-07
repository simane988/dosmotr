package com.g3ck0.seriestracker.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.g3ck0.seriestracker.data.settings.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two places an automatic backup can land.
 *
 * The private folder is the default because it costs no permission and no question on
 * first launch — but it goes away with the app, so it only protects against a mistake
 * inside the app, not against a lost phone. The SAF folder is the real insurance: it can
 * be a folder a cloud client syncs.
 */
@Singleton
class AndroidBackupStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) : AutoBackupStorage {

    override suspend fun openFolder(): BackupFolder {
        val chosen = settings.backupFolderUri.first() ?: return privateFolder()
        // Reading a tree asks the document provider, which is a binder call and a disk
        // read on the other side of it — not something to do on the caller's thread.
        return withContext(Dispatchers.IO) { openTree(chosen) } ?: privateFolder()
    }

    /**
     * Null whenever the tree cannot be written to right now — permission revoked, folder
     * deleted, provider gone. The caller falls back to the private folder rather than
     * failing: a backup somewhere beats no backup at all.
     */
    private fun openTree(folderUri: String): BackupFolder? {
        val uri = Uri.parse(folderUri) ?: return null
        val granted = context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isWritePermission }
        if (!granted) return null
        val tree = DocumentFile.fromTreeUri(context, uri) ?: return null
        if (!tree.isDirectory || !tree.canWrite()) return null
        return DocumentBackupFolder(context, tree)
    }

    private fun privateFolder(): BackupFolder =
        FileBackupFolder(File(context.filesDir, BACKUPS_DIRECTORY))

    override suspend fun useFolder(folderUri: String?) {
        if (folderUri == null) {
            settings.backupFolderUri.first()?.let(::releaseFolder)
            settings.setBackupFolderUri(null)
            return
        }
        // The grant from ACTION_OPEN_DOCUMENT_TREE is persistable, and taking it is what
        // makes the folder survive a restart. A provider that refuses leaves the setting
        // alone, so the next backup goes to the private folder instead of nowhere.
        val taken = runCatching {
            context.contentResolver.takePersistableUriPermission(Uri.parse(folderUri), FOLDER_FLAGS)
        }
        if (taken.isSuccess) settings.setBackupFolderUri(folderUri)
    }

    private fun releaseFolder(folderUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(folderUri), FOLDER_FLAGS)
        }
    }

    private companion object {
        const val FOLDER_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

/** `filesDir/backups` — created on first use, since a folder nobody wrote to has no files. */
internal class FileBackupFolder(private val directory: File) : BackupFolder {

    override val label: String = "Память приложения"

    override val isPrivate: Boolean = true

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        directory.listFiles()?.map { it.name }.orEmpty()
    }

    override suspend fun write(name: String, content: String) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        File(directory, name).writeText(content)
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { File(directory, name).delete() }
    }
}

/** A SAF tree. Everything goes through the provider, so nothing here assumes a real path. */
private class DocumentBackupFolder(
    private val context: Context,
    private val tree: DocumentFile,
) : BackupFolder {

    override val label: String = tree.name ?: "Выбранная папка"

    override val isPrivate: Boolean = false

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        tree.listFiles().mapNotNull { it.name }
    }

    /**
     * An existing file of that name is written over rather than joined by a second one:
     * `createFile` would otherwise hand back `dosmotr-2026-08-06 (1).json` and the day's
     * two backups would both count against the rotation.
     */
    override suspend fun write(name: String, content: String) = withContext(Dispatchers.IO) {
        val file = tree.findFile(name)
            ?: tree.createFile(BACKUP_MIME_TYPE, name)
            ?: error("Не удалось создать файл в выбранной папке")
        context.contentResolver.openOutputStream(file.uri, "wt")
            ?.use { it.write(content.toByteArray()) }
            ?: error("Не удалось открыть файл для записи")
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { tree.findFile(name)?.delete() }
    }
}

internal const val BACKUPS_DIRECTORY = "backups"
private const val BACKUP_MIME_TYPE = "application/json"
