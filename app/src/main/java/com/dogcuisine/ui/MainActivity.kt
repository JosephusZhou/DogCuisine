package com.dogcuisine.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dogcuisine.App
import com.dogcuisine.R
import com.dogcuisine.data.AppDatabase
import com.dogcuisine.data.CategoryEntity
import com.dogcuisine.data.RecipeEntity
import com.dogcuisine.data.StepItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DURATION_MS = 2000L
        private const val FAVORITES_CATEGORY_ID = -1L
    }

    private lateinit var database: AppDatabase
    private lateinit var ioExecutor: ExecutorService
    private lateinit var categoryManageLauncher: ActivityResultLauncher<Intent>
    private lateinit var syncLauncher: ActivityResultLauncher<Intent>

    private val categories = mutableStateListOf<CategoryEntity>()
    private val recipes = mutableStateListOf<RecipeEntity>()
    private val gson = Gson()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    private var selectedCategoryId by mutableStateOf<Long?>(null)
    private var totalRecipeCount by mutableStateOf(0L)
    private var overflowExpanded by mutableStateOf(false)
    private var pendingDeleteRecipe by mutableStateOf<RecipeEntity?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashStartTime = System.currentTimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStartTime < SPLASH_DURATION_MS
        }

        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)

        val app = App.getInstance()
        database = app.getDatabase()
        ioExecutor = app.ioExecutor()

        categoryManageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    loadCategoriesFromDb()
                }
            }
        syncLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    database = App.getInstance().getDatabase()
                    loadCategoriesFromDb()
                }
            }

        setContent {
            MainScreenTheme {
                MainScreen(
                    title = getString(R.string.recipes_title) + "（$totalRecipeCount）",
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    recipes = recipes,
                    formatUpdatedAt = { formatter.format(it) },
                    overflowExpanded = overflowExpanded,
                    onOverflowExpandedChange = { overflowExpanded = it },
                    onCategorySelected = { onCategorySelected(it) },
                    onRecipeClick = { openRecipeDetail(it) },
                    onRecipeLongClick = { pendingDeleteRecipe = it },
                    onSearchClick = { openSearch() },
                    onCategoryManageClick = { openCategoryManage() },
                    onAddClick = { openAddRecipe() },
                    onSyncClick = { openSync() },
                    onBackupClick = { openBackupRestore() }
                )
                DeleteConfirmDialog(
                    recipe = pendingDeleteRecipe,
                    onDismiss = { pendingDeleteRecipe = null },
                    onConfirm = {
                        pendingDeleteRecipe = null
                        deleteRecipe(it)
                    }
                )
            }
        }

        loadCategoriesFromDb()
    }

    override fun onResume() {
        super.onResume()
        updateRecipeCountTitle()
        loadCategoriesFromDb()
    }

    private fun openSearch() {
        startActivity(Intent(this, SearchRecipeActivity::class.java))
        overridePendingTransition(0, 0)
    }

    private fun openCategoryManage() {
        categoryManageLauncher.launch(Intent(this, CategoryManageActivity::class.java))
    }

    private fun openAddRecipe() {
        startActivity(Intent(this, AddRecipeActivity::class.java))
    }

    private fun openSync() {
        syncLauncher.launch(Intent(this, WebDavSyncActivity::class.java))
    }

    private fun openBackupRestore() {
        syncLauncher.launch(Intent(this, BackupRestoreActivity::class.java))
    }

    private fun openRecipeDetail(recipe: RecipeEntity) {
        val id = recipe.id ?: return
        val intent = Intent(this, RecipeDetailActivity::class.java)
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, id)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun onCategorySelected(category: CategoryEntity) {
        selectedCategoryId = category.id
        loadRecipesForSelectedCategory()
    }

    private fun updateRecipeCountTitle() {
        ioExecutor.execute {
            val count = database.recipeDao().count()
            runOnUiThread {
                totalRecipeCount = count
            }
        }
    }

    private fun loadCategoriesFromDb() {
        ioExecutor.execute {
            val dbCategories = database.categoryDao().getAll() ?: emptyList()
            var nextSelected = selectedCategoryId ?: FAVORITES_CATEGORY_ID

            if (dbCategories.isNotEmpty()) {
                val hasSelected =
                    nextSelected == FAVORITES_CATEGORY_ID || dbCategories.any { it.id == nextSelected }
                if (!hasSelected) {
                    nextSelected = dbCategories.first().id ?: FAVORITES_CATEGORY_ID
                }
            } else {
                nextSelected = FAVORITES_CATEGORY_ID
            }

            val displayList = mutableListOf(CategoryEntity(FAVORITES_CATEGORY_ID, "收藏", Int.MIN_VALUE))
            displayList.addAll(dbCategories)

            runOnUiThread {
                selectedCategoryId = nextSelected
                categories.clear()
                categories.addAll(displayList)
                loadRecipesForSelectedCategory()
            }
        }
    }

    private fun loadRecipesForSelectedCategory() {
        val currentCategory = selectedCategoryId
        ioExecutor.execute {
            val list = if (currentCategory == FAVORITES_CATEGORY_ID) {
                database.recipeDao().getFavorites()
            } else {
                database.recipeDao().getByCategoryId(currentCategory)
            } ?: emptyList()
            runOnUiThread {
                recipes.clear()
                recipes.addAll(list)
                updateRecipeCountTitle()
            }
        }
    }

    private fun deleteRecipe(recipe: RecipeEntity) {
        val recipeId = recipe.id
        if (recipeId == null) {
            Toast.makeText(this, "无法删除：ID 缺失", Toast.LENGTH_SHORT).show()
            return
        }
        ioExecutor.execute {
            deleteImages(recipe)
            database.recipeDao().deleteById(recipeId)
            runOnUiThread {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                loadRecipesForSelectedCategory()
            }
        }
    }

    private fun deleteImages(recipe: RecipeEntity) {
        deleteFileSafe(recipe.coverImagePath)
        parseSteps(recipe.stepsJson).forEach { step ->
            step.imagePaths.forEach { path ->
                deleteFileSafe(path)
            }
        }
    }

    private fun parseSteps(json: String?): List<StepItem> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type: Type = object : TypeToken<List<StepItem>>() {}.type
            gson.fromJson<List<StepItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun deleteFileSafe(path: String?) {
        if (path.isNullOrEmpty()) return
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

@Composable
private fun MainScreenTheme(content: @Composable () -> Unit) {
    val warmScheme = lightColorScheme(
        primary = Color(0xFFFEF1A5),
        onPrimary = Color(0xFF2C2518),
        secondary = Color(0xFF4A3F2B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF2EAD6),
        onSecondaryContainer = Color(0xFF2C2518),
        surface = Color(0xFFFFFCF3),
        surfaceVariant = Color(0xFFF7F2E6),
        onSurface = Color(0xFF2B2822),
        onSurfaceVariant = Color(0xFF5A5448),
        outline = Color(0xFFD8CFBA)
    )
    MaterialTheme(colorScheme = warmScheme, typography = MaterialTheme.typography, content = content)
}

@Composable
private fun MainScreen(
    title: String,
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    recipes: List<RecipeEntity>,
    formatUpdatedAt: (Long) -> String,
    overflowExpanded: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onCategorySelected: (CategoryEntity) -> Unit,
    onRecipeClick: (RecipeEntity) -> Unit,
    onRecipeLongClick: (RecipeEntity) -> Unit,
    onSearchClick: () -> Unit,
    onCategoryManageClick: () -> Unit,
    onAddClick: () -> Unit,
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    val recipeListState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF8E8), Color(0xFFFFFDF7))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                MainTopBar(
                    title = title,
                    overflowExpanded = overflowExpanded,
                    onOverflowExpandedChange = onOverflowExpandedChange,
                    onSearchClick = onSearchClick,
                    onCategoryManageClick = onCategoryManageClick,
                    onAddClick = onAddClick,
                    onSyncClick = onSyncClick,
                    onBackupClick = onBackupClick
                )
            }
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CategoryPane(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(112.dp),
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = onCategorySelected
                )
                RecipePane(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    listState = recipeListState,
                    selectedCategoryId = selectedCategoryId,
                    recipes = recipes,
                    formatUpdatedAt = formatUpdatedAt,
                    onRecipeClick = onRecipeClick,
                    onRecipeLongClick = onRecipeLongClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    overflowExpanded: Boolean,
    onOverflowExpandedChange: (Boolean) -> Unit,
    onSearchClick: () -> Unit,
    onCategoryManageClick: () -> Unit,
    onAddClick: () -> Unit,
    onSyncClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "按分类浏览，长按卡片删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = {
            ToolbarIconButton(iconRes = R.drawable.ic_search_gold, onClick = onSearchClick)
            ToolbarIconButton(iconRes = R.drawable.ic_category_gold, onClick = onCategoryManageClick)
            ToolbarIconButton(iconRes = R.drawable.ic_add_gold, onClick = onAddClick)
            Box {
                IconButton(onClick = { onOverflowExpandedChange(true) }) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_more),
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { onOverflowExpandedChange(false) }
                ) {
                    DropdownMenuItem(
                        text = { Text("同步") },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onSyncClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("备份与恢复") },
                        onClick = {
                            onOverflowExpandedChange(false)
                            onBackupClick()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun ToolbarIconButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun CategoryPane(
    modifier: Modifier = Modifier,
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onCategorySelected: (CategoryEntity) -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = "分类",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 6.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 10.dp,
                vertical = 8.dp
            )
        ) {
            items(items = categories, key = { it.id ?: it.hashCode().toLong() }) { category ->
                val selected = category.id == selectedCategoryId
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(
                            alpha = 0.4f
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategorySelected(category) }
                ) {
                    Text(
                        text = category.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipePane(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    selectedCategoryId: Long?,
    recipes: List<RecipeEntity>,
    formatUpdatedAt: (Long) -> String,
    onRecipeClick: (RecipeEntity) -> Unit,
    onRecipeLongClick: (RecipeEntity) -> Unit
) {
    LaunchedEffect(selectedCategoryId, recipes.size) {
        listState.scrollToItem(0)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (recipes.isEmpty()) {
            EmptyRecipeState()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(items = recipes, key = { it.id ?: it.hashCode().toLong() }) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        formattedTime = formatUpdatedAt(recipe.updatedAt),
                        onClick = { onRecipeClick(recipe) },
                        onLongClick = { onRecipeLongClick(recipe) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeCard(
    recipe: RecipeEntity,
    formattedTime: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecipeThumbnail(
            imagePath = recipe.coverImagePath,
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "最近编辑：$formattedTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecipeThumbnail(
    imagePath: String?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(R.drawable.ic_launcher_foreground)
            }
        },
        update = { imageView ->
            val path = imagePath
            if (path.isNullOrEmpty()) {
                imageView.setImageResource(R.drawable.ic_launcher_foreground)
                return@AndroidView
            }
            val file = File(path)
            if (!file.exists()) {
                imageView.setImageResource(R.drawable.ic_launcher_foreground)
                return@AndroidView
            }
            runCatching {
                imageView.setImageURI(Uri.fromFile(file))
            }.onFailure {
                imageView.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    )
}

@Composable
private fun EmptyRecipeState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_add_gold),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "暂无菜谱",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "点击右上角添加",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    recipe: RecipeEntity?,
    onDismiss: () -> Unit,
    onConfirm: (RecipeEntity) -> Unit
) {
    val target = recipe ?: return
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "删除菜谱",
        message = "确定删除“${target.name}”吗？",
        confirmText = "删除",
        onConfirm = { onConfirm(target) },
        dismissText = "取消",
        onDismiss = onDismiss
    )
}
