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
    private var syncStatus by mutableStateOf("说明：同步会包含数据库和所有图片。")

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
            Toast.makeText(this, "正在后台上传中，不可操作", Toast.LENGTH_SHORT).show()
            return
        }
        val url = webDavUrl.trim()
        val user = webDavUser.trim()
        val pass = webDavPass
        if (url.isEmpty()) {
            Toast.makeText(this, "请填写 WebDAV URL", Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig(url, user, pass)
        setLoading(true, "正在上传数据库和图片到 WebDAV...")
        ioExecutor.execute {
            try {
                WebDavSyncManager(this).upload(url, user, pass)
                runOnUiThread {
                    setLoading(false, "上传完成")
                    Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    setLoading(false, "上传失败")
                    Toast.makeText(this, "上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startDownload() {
        if (App.getInstance().isAutoWebDavUploading()) {
            Toast.makeText(this, "正在后台上传中，不可操作", Toast.LENGTH_SHORT).show()
            return
        }
        val url = webDavUrl.trim()
        val user = webDavUser.trim()
        val pass = webDavPass
        if (url.isEmpty()) {
            Toast.makeText(this, "请填写 WebDAV URL", Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig(url, user, pass)
        setLoading(true, "正在从 WebDAV 恢复数据库和图片...")
        ioExecutor.execute {
            try {
                WebDavSyncManager(this).downloadAndRestore(url, user, pass)
                runOnUiThread {
                    setLoading(false, "恢复完成")
                    setResult(RESULT_OK)
                    Toast.makeText(this, "恢复成功", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    setLoading(false, "恢复失败")
                    Toast.makeText(this, "恢复失败: ${e.message}", Toast.LENGTH_LONG).show()
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
                            Text("WebDAV 同步", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "上传或恢复数据库与图片",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_gold),
                                contentDescription = "返回",
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
                    label = { Text("WebDAV 目录 URL") },
                    placeholder = { Text("例如：https://example.com/dav") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = webDavUser,
                    onValueChange = onUserChange,
                    enabled = !isSyncing,
                    singleLine = true,
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = webDavPass,
                    onValueChange = onPassChange,
                    enabled = !isSyncing,
                    singleLine = true,
                    label = { Text("密码") },
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
                            Text(if (passwordVisible) "隐藏" else "显示")
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
                        Text("上传到 WebDAV")
                    }
                    OutlinedButton(
                        onClick = onDownloadClick,
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("从 WebDAV 恢复")
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
