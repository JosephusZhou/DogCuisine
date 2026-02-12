package com.dogcuisine.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dogcuisine.App;
import com.dogcuisine.R;
import com.dogcuisine.data.CategoryDao;
import com.dogcuisine.data.CategoryEntity;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class CategoryManageActivity extends AppCompatActivity implements CategoryManageAdapter.OnItemInteractionListener {

    private static final int MENU_ID_SAVE = 3001;

    private RecyclerView recyclerView;
    private CategoryManageAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private CategoryDao categoryDao;
    private ExecutorService ioExecutor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_manage);

        App app = App.getInstance();
        categoryDao = app.getDatabase().categoryDao();
        ioExecutor = app.ioExecutor();

        MaterialToolbar toolbar = findViewById(R.id.toolbarCategory);
        toolbar.setTitle("菜谱分类");
        SystemBarHelper.applyEdgeToEdge(this,
                new View[]{toolbar},
                new View[]{findViewById(R.id.rvCategoryManage)});
        toolbar.setNavigationIcon(R.drawable.ic_back_gold);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.teal_200));
        Menu menu = toolbar.getMenu();
        menu.clear();
        MenuItem saveItem = menu.add(Menu.NONE, MENU_ID_SAVE, Menu.NONE, "保存");
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        saveItem.setIcon(android.R.drawable.ic_menu_save);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ID_SAVE) {
                saveCategories();
                return true;
            }
            return false;
        });

        recyclerView = findViewById(R.id.rvCategoryManage);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CategoryManageAdapter(this);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getBindingAdapterPosition();
                int toPos = to.getBindingAdapterPosition();
                adapter.onItemMove(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // no-op, we don't support swipe delete
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // use handle
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        loadCategories();
    }

    private void loadCategories() {
        ioExecutor.execute(() -> {
            List<CategoryEntity> list = categoryDao.getAll();
            if (list == null) list = new ArrayList<>();
            List<CategoryEntity> finalList = list;
            runOnUiThread(() -> adapter.setData(finalList));
        });
    }

    private void saveCategories() {
        List<CategoryEntity> list = adapter.getData();
        if (list == null || list.isEmpty()) {
            Toast.makeText(this, "暂无可保存的分类", Toast.LENGTH_SHORT).show();
            return;
        }
        ioExecutor.execute(() -> {
            for (int i = 0; i < list.size(); i++) {
                CategoryEntity entity = list.get(i);
                entity.setSortOrder(i + 1);
            }
            categoryDao.insertAll(list);
            runOnUiThread(() -> {
                App.getInstance().requestAutoWebDavUploadIfConfigured();
                Toast.makeText(this, "分类已保存", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    @Override
    public void onEdit(@NonNull CategoryEntity category, int position) {
        showNameDialog("编辑分类", category.getName(), newName -> {
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            List<CategoryEntity> list = adapter.getData();
            if (position >= 0 && position < list.size()) {
                list.get(position).setName(newName);
                adapter.notifyItemChanged(position);
            }
        });
    }

    @Override
    public void onDelete(@NonNull CategoryEntity category, int position) {
        List<CategoryEntity> list = adapter.getData();
        if (position < 0 || position >= list.size()) return;
        list.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, list.size() - position);
    }

    @Override
    public void onAddNew() {
        showNameDialog("新增分类", "", newName -> {
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            List<CategoryEntity> list = adapter.getData();
            CategoryEntity entity = new CategoryEntity(null, newName, list.size() + 1);
            list.add(entity);
            adapter.notifyItemInserted(list.size() - 1);
        });
    }

    @Override
    public void onStartDrag(@NonNull RecyclerView.ViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }

    private interface OnNameConfirmListener {
        void onConfirm(String name);
    }

    private void showNameDialog(String title, String defaultText, OnNameConfirmListener listener) {
        EditText editText = new EditText(this);
        editText.setHint("请输入分类名称");
        editText.setText(defaultText);
        editText.setSelection(defaultText != null ? defaultText.length() : 0);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, which) -> {
                    if (listener != null) {
                        listener.onConfirm(editText.getText().toString().trim());
                    }
                })
                .create();

        dialog.setOnShowListener(d -> {
            int accent = ContextCompat.getColor(this, R.color.teal_200);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent);
        });
        dialog.show();
    }
}
