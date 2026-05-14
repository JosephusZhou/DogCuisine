package com.dogcuisine.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dogcuisine.App
import com.dogcuisine.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRestoreActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, BackupRestoreActivity::class.java)
        }
    }

    private lateinit var ioExecutor: ExecutorService
    private lateinit var loadingDialogHelper: LoadingDialogHelper
    private lateinit var restorePicker: ActivityResultLauncher<Array<String>>
    private lateinit var systemBarDelegate: ComposeSystemBarDelegate.Controller

    private var isWorking by mutableStateOf(false)
    private var backupStatus by mutableStateOf("")
    private var restoreStatus by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemBarDelegate = ComposeSystemBarDelegate.install(this)

        val app = App.getInstance()
        ioExecutor = app.ioExecutor()
        loadingDialogHelper = LoadingDialogHelper(this)

        restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            systemBarDelegate.apply()
            if (uri != null) {
                startRestore(uri)
            }
        }

        setContent {
            DogCuisineTheme {
                BackupRestoreScreen(
                    isWorking = isWorking,
                    backupStatus = backupStatus,
                    restoreStatus = restoreStatus,
                    onBack = {
                        if (!isWorking) {
                            finish()
                        }
                    },
                    onBackupClick = { startBackup() },
                    onRestoreClick = { pickRestoreFile() }
                )
            }
        }
    }

    private fun pickRestoreFile() {
        if (isWorking) return
        restorePicker.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    private fun startBackup() {
        if (isWorking) return
        setLoading(true)
        ioExecutor.execute {
            try {
                val path = createBackupZip()
                runOnUiThread {
                    setLoading(false)
                    backupStatus = getString(R.string.backup_done_status, path)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        getString(R.string.backup_failed_toast, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startRestore(uri: Uri) {
        if (isWorking) return
        setLoading(true)
        ioExecutor.execute {
            try {
                val tempZip = copyUriToCache(uri)
                restoreFromZip(tempZip)
                runOnUiThread {
                    setLoading(false)
                    restoreStatus = getString(R.string.restore_done_status)
                    setResult(RESULT_OK)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(
                        this,
                        getString(R.string.restore_failed_toast, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createBackupZip(): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        val fileName = "dogcuisine_backup_${ts}.zip"

        val dbFile = getDatabasePath("dogcuisine.db")
        val imagesDir = File(filesDir, "images")

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException(getString(R.string.backup_create_dir_failed))

        val out = contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException(getString(R.string.backup_write_dir_failed))

        out.use { outStream ->
            ZipOutputStream(BufferedOutputStream(outStream)).use { zos ->
                if (dbFile.exists()) {
                    zipFile(zos, dbFile, "db/dogcuisine.db")
                }
                if (imagesDir.exists()) {
                    zipDir(zos, imagesDir, "images")
                }
            }
        }

        val done = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        contentResolver.update(uri, done, null, null)

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, fileName).absolutePath
    }

    private fun restoreFromZip(zipFile: File) {
        App.getInstance().getDatabase().close()

        val tempDir = File(cacheDir, "backup_restore")
        if (tempDir.exists()) deleteRecursively(tempDir)
        tempDir.mkdirs()

        unzipToDir(zipFile, tempDir)

        val dbSrc = File(tempDir, "db/dogcuisine.db")
        val dbDest = getDatabasePath("dogcuisine.db")
        if (dbSrc.exists()) {
            copyFile(dbSrc, dbDest)
        }

        val imagesSrc = File(tempDir, "images")
        val imagesDest = File(filesDir, "images")
        if (imagesDest.exists()) deleteRecursively(imagesDest)
        if (imagesSrc.exists()) {
            copyDir(imagesSrc, imagesDest)
        }

        App.getInstance().reloadDatabase()
    }

    private fun zipFile(zos: ZipOutputStream, file: File, entryName: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                val len = input.read(buffer)
                if (len == -1) break
                zos.write(buffer, 0, len)
            }
        }
        zos.closeEntry()
    }

    private fun zipDir(zos: ZipOutputStream, dir: File, baseName: String) {
        val files = dir.listFiles() ?: return
        files.forEach { file ->
            val entryName = "$baseName/${file.name}"
            if (file.isDirectory) {
                zipDir(zos, file, entryName)
            } else {
                zipFile(zos, file, entryName)
            }
        }
    }

    private fun unzipToDir(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            val buffer = ByteArray(4096)
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    val parent = out.parentFile
                    if (parent != null && !parent.exists()) parent.mkdirs()
                    BufferedOutputStream(FileOutputStream(out)).use { os ->
                        while (true) {
                            val len = zis.read(buffer)
                            if (len == -1) break
                            os.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val out = File(cacheDir, "restore.zip")
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalStateException(getString(R.string.backup_read_failed))
        input.use { source ->
            FileOutputStream(out).use { os ->
                val buffer = ByteArray(4096)
                while (true) {
                    val len = source.read(buffer)
                    if (len == -1) break
                    os.write(buffer, 0, len)
                }
            }
        }
        return out
    }

    private fun copyFile(src: File, dest: File) {
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        BufferedInputStream(FileInputStream(src)).use { input ->
            BufferedOutputStream(FileOutputStream(dest)).use { output ->
                val buffer = ByteArray(4096)
                while (true) {
                    val len = input.read(buffer)
                    if (len == -1) break
                    output.write(buffer, 0, len)
                }
            }
        }
    }

    private fun copyDir(srcDir: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        val files = srcDir.listFiles() ?: return
        files.forEach { file ->
            val dest = File(destDir, file.name)
            if (file.isDirectory) {
                copyDir(file, dest)
            } else {
                copyFile(file, dest)
            }
        }
    }

    private fun deleteRecursively(file: File?) {
        if (file == null || !file.exists()) return
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                children.forEach { child -> deleteRecursively(child) }
            }
        }
        file.delete()
    }

    private fun setLoading(loading: Boolean) {
        isWorking = loading
        if (loading) {
            loadingDialogHelper.show()
        } else {
            loadingDialogHelper.dismiss()
        }
    }

    override fun onDestroy() {
        loadingDialogHelper.dismiss()
        super.onDestroy()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreScreen(
    isWorking: Boolean,
    backupStatus: String,
    restoreStatus: String,
    onBack: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dogCuisineBackgroundBrush())
    ) {
        Scaffold(
            containerColor = DogCuisineColors.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.backup_restore_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(R.string.backup_restore_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_gold),
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionCard(
                    title = stringResource(R.string.backup_local_title),
                    description = stringResource(R.string.backup_local_desc),
                    actionText = stringResource(R.string.backup_local_action),
                    status = backupStatus,
                    enabled = !isWorking,
                    onAction = onBackupClick
                )
                ActionCard(
                    title = stringResource(R.string.restore_local_title),
                    description = stringResource(R.string.restore_local_desc),
                    actionText = stringResource(R.string.restore_local_action),
                    status = restoreStatus,
                    enabled = !isWorking,
                    onAction = onRestoreClick
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    description: String,
    actionText: String,
    status: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAction,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Text(actionText, color = MaterialTheme.colorScheme.secondary)
            }
            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
