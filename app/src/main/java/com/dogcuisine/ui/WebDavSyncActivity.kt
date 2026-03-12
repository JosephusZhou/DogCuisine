package com.dogcuisine.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dogcuisine.App
import com.dogcuisine.R
import com.dogcuisine.sync.WebDavSyncConfig
import com.dogcuisine.sync.WebDavSyncManager
import java.util.concurrent.ExecutorService
import androidx.activity.compose.setContent

class WebDavSyncActivity : AppCompatActivity() {

    private lateinit var ioExecutor: ExecutorService
    private lateinit var loadingDialogHelper: LoadingDialogHelper

    private var isSyncing by mutableStateOf(false)
    private var webDavUrl by mutableStateOf("")
    private var webDavUser by mutableStateOf("")
    private var webDavPass by mutableStateOf("")
    private var passwordVisible by mutableStateOf(false)
    private var syncStatus by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isSyncing) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        val app = App.getInstance()
        ioExecutor = app.ioExecutor()
        loadingDialogHelper = LoadingDialogHelper(this)
        loadConfig()
        if (syncStatus.isBlank()) {
            syncStatus = getString(R.string.webdav_sync_info)
        }

        setContent {
            DogCuisineTheme {
                WebDavSyncScreen(
                    isSyncing = isSyncing,
                    webDavUrl = webDavUrl,
                    webDavUser = webDavUser,
                    webDavPass = webDavPass,
                    passwordVisible = passwordVisible,
                    syncStatus = syncStatus,
                    onUrlChange = { webDavUrl = it },
                    onUserChange = { webDavUser = it },
                    onPassChange = { webDavPass = it },
                    onPasswordVisibleChange = { passwordVisible = it },
                    onBack = {
                        if (!isSyncing) {
                            finish()
                        }
                    },
                    onUploadClick = { startUpload() },
                    onDownloadClick = { startDownload() }
                )
            }
        }
    }

    private fun startUpload() {
        if (App.getInstance().isAutoWebDavUploading()) {
            Toast.makeText(this, getString(R.string.webdav_uploading_background_toast), Toast.LENGTH_SHORT).show()
            return
        }
        val url = webDavUrl.trim()
        val user = webDavUser.trim()
        val pass = webDavPass
        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.webdav_url_required_toast), Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig(url, user, pass)
        setLoading(true, getString(R.string.webdav_uploading))
        ioExecutor.execute {
            try {
                WebDavSyncManager(this).upload(url, user, pass)
                runOnUiThread {
                    setLoading(false, getString(R.string.webdav_upload_done))
                    Toast.makeText(this, getString(R.string.webdav_upload_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    setLoading(false, getString(R.string.webdav_upload_failed))
                    Toast.makeText(
                        this,
                        getString(R.string.webdav_upload_failed_with_reason, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startDownload() {
        if (App.getInstance().isAutoWebDavUploading()) {
            Toast.makeText(this, getString(R.string.webdav_uploading_background_toast), Toast.LENGTH_SHORT).show()
            return
        }
        val url = webDavUrl.trim()
        val user = webDavUser.trim()
        val pass = webDavPass
        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.webdav_url_required_toast), Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig(url, user, pass)
        setLoading(true, getString(R.string.webdav_restoring))
        ioExecutor.execute {
            try {
                WebDavSyncManager(this).downloadAndRestore(url, user, pass)
                runOnUiThread {
                    setLoading(false, getString(R.string.webdav_restore_done))
                    setResult(RESULT_OK)
                    Toast.makeText(this, getString(R.string.webdav_restore_success), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    setLoading(false, getString(R.string.webdav_restore_failed))
                    Toast.makeText(
                        this,
                        getString(R.string.webdav_restore_failed_with_reason, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun setLoading(loading: Boolean, status: String) {
        isSyncing = loading
        syncStatus = status
        if (loading) {
            loadingDialogHelper.show()
        } else {
            loadingDialogHelper.dismiss()
        }
    }

    private fun loadConfig() {
        val config = WebDavSyncConfig.load(this)
        if (config == null) {
            webDavUrl = ""
            webDavUser = ""
            webDavPass = ""
            return
        }
        webDavUrl = config.url
        webDavUser = config.user
        webDavPass = config.pass
    }

    private fun saveConfig(url: String, user: String, pass: String) {
        getSharedPreferences(WebDavSyncConfig.PREF_SYNC, MODE_PRIVATE)
            .edit()
            .putString(WebDavSyncConfig.KEY_URL, url)
            .putString(WebDavSyncConfig.KEY_USER, user)
            .putString(WebDavSyncConfig.KEY_PASS, pass)
            .apply()
    }

    override fun onDestroy() {
        loadingDialogHelper.dismiss()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSyncScreen(
    isSyncing: Boolean,
    webDavUrl: String,
    webDavUser: String,
    webDavPass: String,
    passwordVisible: Boolean,
    syncStatus: String,
    onUrlChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onPasswordVisibleChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onUploadClick: () -> Unit,
    onDownloadClick: () -> Unit
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
                            Text(stringResource(R.string.webdav_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(R.string.webdav_subtitle),
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = webDavUrl,
                    onValueChange = onUrlChange,
                    enabled = !isSyncing,
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_url_label)) },
                    placeholder = { Text(stringResource(R.string.webdav_url_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = webDavUser,
                    onValueChange = onUserChange,
                    enabled = !isSyncing,
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_username_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = webDavPass,
                    onValueChange = onPassChange,
                    enabled = !isSyncing,
                    singleLine = true,
                    label = { Text(stringResource(R.string.webdav_password_label)) },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(
                            enabled = !isSyncing,
                            onClick = { onPasswordVisibleChange(!passwordVisible) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(
                                if (passwordVisible) {
                                    stringResource(R.string.common_hide)
                                } else {
                                    stringResource(R.string.common_show)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onUploadClick,
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(stringResource(R.string.webdav_upload_button))
                    }
                    OutlinedButton(
                        onClick = onDownloadClick,
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(stringResource(R.string.webdav_restore_button))
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
