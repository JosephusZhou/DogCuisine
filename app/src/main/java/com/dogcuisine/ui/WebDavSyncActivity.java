package com.dogcuisine.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.sync.WebDavSyncConfig;
import com.dogcuisine.sync.WebDavSyncManager;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.concurrent.ExecutorService;

public class WebDavSyncActivity extends AppCompatActivity {

    private EditText etUrl;
    private EditText etUser;
    private EditText etPass;
    private Button btnUpload;
    private Button btnDownload;
    private TextView tvStatus;
    private ExecutorService ioExecutor;
    private LoadingDialogHelper loadingDialogHelper;
    private boolean isSyncing = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webdav_sync);

        MaterialToolbar toolbar = findViewById(R.id.toolbarSync);
        toolbar.setTitle("WebDAV 同步");
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        toolbar.setNavigationIcon(R.drawable.ic_back_gold);
        toolbar.setNavigationOnClickListener(v -> {
            if (!isSyncing) {
                finish();
            }
        });

        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{toolbar},
                new View[]{findViewById(android.R.id.content)});

        App app = App.getInstance();
        ioExecutor = app.ioExecutor();
        loadingDialogHelper = new LoadingDialogHelper(this);

        etUrl = findViewById(R.id.etWebDavUrl);
        etUser = findViewById(R.id.etWebDavUser);
        etPass = findViewById(R.id.etWebDavPass);
        btnUpload = findViewById(R.id.btnUploadSync);
        btnDownload = findViewById(R.id.btnDownloadSync);
        tvStatus = findViewById(R.id.tvSyncStatus);

        loadConfig();

        btnUpload.setOnClickListener(v -> startUpload());
        btnDownload.setOnClickListener(v -> startDownload());
    }

    private void startUpload() {
        if (App.getInstance().isAutoWebDavUploading()) {
            Toast.makeText(this, "正在后台上传中，不可操作", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = etUrl.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString();
        if (url.isEmpty()) {
            Toast.makeText(this, "请填写 WebDAV URL", Toast.LENGTH_SHORT).show();
            return;
        }
        saveConfig(url, user, pass);
        setLoading(true, "正在上传数据库和图片到 WebDAV...");
        ioExecutor.execute(() -> {
            try {
                new WebDavSyncManager(this).upload(url, user, pass);
                runOnUiThread(() -> {
                    setLoading(false, "上传完成");
                    Toast.makeText(this, "上传成功", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setLoading(false, "上传失败");
                    Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startDownload() {
        if (App.getInstance().isAutoWebDavUploading()) {
            Toast.makeText(this, "正在后台上传中，不可操作", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = etUrl.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String pass = etPass.getText().toString();
        if (url.isEmpty()) {
            Toast.makeText(this, "请填写 WebDAV URL", Toast.LENGTH_SHORT).show();
            return;
        }
        saveConfig(url, user, pass);
        setLoading(true, "正在从 WebDAV 恢复数据库和图片...");
        ioExecutor.execute(() -> {
            try {
                new WebDavSyncManager(this).downloadAndRestore(url, user, pass);
                runOnUiThread(() -> {
                    setLoading(false, "恢复完成");
                    setResult(RESULT_OK);
                    Toast.makeText(this, "恢复成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setLoading(false, "恢复失败");
                    Toast.makeText(this, "恢复失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLoading(boolean loading, String status) {
        isSyncing = loading;
        btnUpload.setEnabled(!loading);
        btnDownload.setEnabled(!loading);
        etUrl.setEnabled(!loading);
        etUser.setEnabled(!loading);
        etPass.setEnabled(!loading);
        tvStatus.setText(status);
        if (loading) {
            showLoadingDialog();
        } else {
            dismissLoadingDialog();
        }
    }

    private void loadConfig() {
        WebDavSyncConfig config = WebDavSyncConfig.load(this);
        if (config == null) {
            etUrl.setText("");
            etUser.setText("");
            etPass.setText("");
            return;
        }
        etUrl.setText(config.url);
        etUser.setText(config.user);
        etPass.setText(config.pass);
    }

    private void saveConfig(String url, String user, String pass) {
        getSharedPreferences(WebDavSyncConfig.PREF_SYNC, MODE_PRIVATE)
                .edit()
                .putString(WebDavSyncConfig.KEY_URL, url)
                .putString(WebDavSyncConfig.KEY_USER, user)
                .putString(WebDavSyncConfig.KEY_PASS, pass)
                .apply();
    }

    private void showLoadingDialog() {
        loadingDialogHelper.show();
    }

    private void dismissLoadingDialog() {
        loadingDialogHelper.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (!isSyncing) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        dismissLoadingDialog();
        super.onDestroy();
    }
}
