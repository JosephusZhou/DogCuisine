package com.dogcuisine.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.dogcuisine.App
import com.dogcuisine.R
import com.dogcuisine.data.CategoryDao
import com.dogcuisine.data.RecipeDao
import com.dogcuisine.data.RecipeEntity
import com.dogcuisine.data.StepItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.ExecutorService

class RecipeDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
    }

    private lateinit var ioExecutor: ExecutorService
    private lateinit var recipeDao: RecipeDao
    private lateinit var categoryDao: CategoryDao

    private val gson = Gson()
    private val steps = mutableStateListOf<StepItem>()

    private var recipeId: Long = -1L
    private var recipeName by mutableStateOf("")
    private var categoryName by mutableStateOf("未分类")
    private var coverImagePath by mutableStateOf<String?>(null)
    private var ingredient by mutableStateOf(StepItem())
    private var isFavorite by mutableStateOf(false)
    private var previewImagePath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)

        val app = App.getInstance()
        ioExecutor = app.ioExecutor()
        recipeDao = app.getDatabase().recipeDao()
        categoryDao = app.getDatabase().categoryDao()

        recipeId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
        if (recipeId <= 0L) {
            Toast.makeText(this, "无效的菜谱", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            DogCuisineTheme {
                RecipeDetailScreen(
                    recipeName = recipeName,
                    categoryName = categoryName,
                    coverImagePath = coverImagePath,
                    ingredient = ingredient,
                    steps = steps,
                    isFavorite = isFavorite,
                    onBack = { finish() },
                    onToggleFavorite = { toggleFavorite() },
                    onEdit = { openEditPage() },
                    onImageClick = { path -> previewImagePath = path }
                )

                val path = previewImagePath
                if (!path.isNullOrEmpty()) {
                    ImagePreviewDialog(path = path, onDismiss = { previewImagePath = null })
                }
            }
        }

        loadRecipe()
    }

    override fun onResume() {
        super.onResume()
        if (recipeId > 0L) {
            loadRecipe()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun loadRecipe() {
        ioExecutor.execute {
            val entity = recipeDao.getById(recipeId)
            val category = if (entity?.categoryId != null) {
                categoryDao.getById(entity.categoryId!!)
            } else {
                null
            }
            runOnUiThread {
                if (entity == null) {
                    Toast.makeText(this, "未找到菜谱", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                bindRecipe(entity, category?.name)
            }
        }
    }

    private fun bindRecipe(entity: RecipeEntity, category: String?) {
        recipeName = entity.name ?: ""
        categoryName = if (category.isNullOrEmpty()) "未分类" else category
        coverImagePath = entity.coverImagePath
        isFavorite = entity.isFavorite == 1
        ingredient = parseIngredient(entity.ingredientJson)
        steps.clear()
        steps.addAll(parseSteps(entity.stepsJson))
    }

    private fun toggleFavorite() {
        if (recipeId <= 0L) return
        val next = if (isFavorite) 0 else 1
        ioExecutor.execute {
            recipeDao.updateFavorite(recipeId, next)
            runOnUiThread {
                isFavorite = next == 1
            }
        }
    }

    private fun openEditPage() {
        val intent = Intent(this, AddRecipeActivity::class.java)
        intent.putExtra(AddRecipeActivity.EXTRA_RECIPE_ID, recipeId)
        startActivity(intent)
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

    private fun parseIngredient(json: String?): StepItem {
        if (json.isNullOrEmpty()) return StepItem()
        return try {
            gson.fromJson(json, StepItem::class.java) ?: StepItem()
        } catch (_: Exception) {
            StepItem()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailScreen(
    recipeName: String,
    categoryName: String,
    coverImagePath: String?,
    ingredient: StepItem,
    steps: List<StepItem>,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onImageClick: (String) -> Unit
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
                    title = { Text(text = "菜谱详情", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_gold),
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                painter = painterResource(
                                    id = if (isFavorite) {
                                        R.drawable.ic_favorite_gold
                                    } else {
                                        R.drawable.ic_favorite_border_gold
                                    }
                                ),
                                contentDescription = "收藏",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit_gold),
                                contentDescription = "修改",
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
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                LocalSquareCover(
                    imagePath = coverImagePath,
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.CenterHorizontally)
                )
                Text(
                    text = recipeName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = DogCuisineColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "食材",
                    style = MaterialTheme.typography.titleMedium,
                    color = DogCuisineColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IngredientCard(
                    ingredient = ingredient,
                    onImageClick = onImageClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                )

                Text(
                    text = "步骤",
                    style = MaterialTheme.typography.titleMedium,
                    color = DogCuisineColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (steps.isEmpty()) {
                    Text(
                        text = "暂无步骤",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        steps.forEachIndexed { index, step ->
                            StepCard(
                                index = index,
                                step = step,
                                onImageClick = onImageClick
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LocalSquareCover(
    imagePath: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun IngredientCard(
    ingredient: StepItem,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val text = ingredient.text?.trim().orEmpty()
    val hasText = text.isNotEmpty()
    val images = ingredient.imagePaths ?: emptyList()
    val hasImages = images.isNotEmpty()

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (hasText) text else "暂无食材",
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasText) DogCuisineColors.TextPrimary else DogCuisineColors.TextPlaceholder
            )
            if (hasImages) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (hasText) 8.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    images.forEach { path ->
                        FullWidthRatioImage(
                            imagePath = path,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onImageClick(path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: StepItem,
    onImageClick: (String) -> Unit
) {
    val stepText = step.text?.trim().orEmpty()
    val images = step.imagePaths ?: emptyList()
    val hasText = stepText.isNotEmpty()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "步骤 ${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = DogCuisineColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (hasText) {
                Text(
                    text = stepText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DogCuisineColors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (images.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (hasText) 8.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    images.forEach { path ->
                        FullWidthRatioImage(
                            imagePath = path,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onImageClick(path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullWidthRatioImage(
    imagePath: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AndroidView(
        modifier = modifier.clickable(onClick = onClick),
        factory = { context ->
            ImageView(context).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { imageView ->
            val file = File(imagePath)
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
private fun ImagePreviewDialog(
    path: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DogCuisineColors.Scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setOnClickListener { onDismiss() }
                    }
                },
                update = { imageView ->
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
    }
}
