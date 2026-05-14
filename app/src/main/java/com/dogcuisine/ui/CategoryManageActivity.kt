package com.dogcuisine.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dogcuisine.App
import com.dogcuisine.R
import com.dogcuisine.data.CategoryDao
import com.dogcuisine.data.CategoryEntity
import com.dogcuisine.data.RecipeDao
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt

private data class EditableCategory(
    val id: Long?,
    val name: String,
    val uiKey: Long
)

private data class NameDialogState(
    val title: String,
    val editingIndex: Int?
)

class CategoryManageActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, CategoryManageActivity::class.java)
        }
    }

    private val categories = mutableStateListOf<EditableCategory>()
    private val originalIds = mutableListOf<Long>()
    private var baselineState: List<Pair<Long?, String>> = emptyList()
    private var nextTempUiKey = -1L

    private lateinit var categoryDao: CategoryDao
    private lateinit var recipeDao: RecipeDao
    private lateinit var ioExecutor: ExecutorService

    private var isSaving by mutableStateOf(false)
    private var nameDialogState by mutableStateOf<NameDialogState?>(null)
    private var nameDialogInput by mutableStateOf("")
    private var cannotDeleteDialogVisible by mutableStateOf(false)
    private var unsavedBackDialogVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isSaving) {
                    requestBackWithUnsavedCheck()
                }
            }
        })

        val app = App.getInstance()
        categoryDao = app.getDatabase().categoryDao()
        recipeDao = app.getDatabase().recipeDao()
        ioExecutor = app.ioExecutor()

        setContent {
            DogCuisineTheme {
                CategoryManageScreen(
                    categories = categories,
                    isSaving = isSaving,
                    onBack = { if (!isSaving) requestBackWithUnsavedCheck() },
                    onSave = { if (!isSaving) saveCategories() },
                    onMove = { from, to -> moveCategory(from, to) },
                    onAdd = { showNameDialog(getString(R.string.add_category_title), null) },
                    onEdit = { index -> showNameDialog(getString(R.string.edit_category_title), index) },
                    onDelete = { index -> tryDeleteCategory(index) }
                )

                val dialog = nameDialogState
                if (dialog != null) {
                    AppTextInputDialog(
                        onDismissRequest = { dismissNameDialog() },
                        title = dialog.title,
                        value = nameDialogInput,
                        onValueChange = { nameDialogInput = it },
                        hint = stringResource(R.string.category_name_hint),
                        onConfirm = { confirmNameDialog(dialog) },
                        onDismiss = { dismissNameDialog() }
                    )
                }

                if (cannotDeleteDialogVisible) {
                    AppAlertDialog(
                        onDismissRequest = { cannotDeleteDialogVisible = false },
                        title = stringResource(R.string.delete_blocked_title),
                        message = stringResource(R.string.delete_blocked_message),
                        confirmText = stringResource(R.string.got_it),
                        onConfirm = { cannotDeleteDialogVisible = false },
                        dismissText = null
                    )
                }

                if (unsavedBackDialogVisible) {
                    AppAlertDialog(
                        onDismissRequest = { unsavedBackDialogVisible = false },
                        title = stringResource(R.string.unsaved_changes_title),
                        message = stringResource(R.string.unsaved_changes_message),
                        confirmText = stringResource(R.string.save_and_back),
                        onConfirm = {
                            unsavedBackDialogVisible = false
                            saveCategories()
                        },
                        dismissText = stringResource(R.string.back_without_save),
                        onDismiss = {
                            unsavedBackDialogVisible = false
                            finish()
                        }
                    )
                }
            }
        }

        loadCategories()
    }

    private fun showNameDialog(title: String, editingIndex: Int?) {
        val defaultText = if (editingIndex != null && editingIndex in categories.indices) {
            categories[editingIndex].name
        } else {
            ""
        }
        nameDialogInput = defaultText
        nameDialogState = NameDialogState(title = title, editingIndex = editingIndex)
    }

    private fun dismissNameDialog() {
        nameDialogState = null
        nameDialogInput = ""
    }

    private fun confirmNameDialog(dialog: NameDialogState) {
        val newName = nameDialogInput.trim()
        if (newName.isEmpty()) {
            Toast.makeText(this, getString(R.string.category_name_empty_toast), Toast.LENGTH_SHORT).show()
            return
        }
        val editingIndex = dialog.editingIndex
        if (editingIndex == null) {
            categories.add(
                EditableCategory(
                    id = null,
                    name = newName,
                    uiKey = createTempUiKey()
                )
            )
        } else if (editingIndex in categories.indices) {
            categories[editingIndex] = categories[editingIndex].copy(name = newName)
        }
        dismissNameDialog()
    }

    private fun loadCategories() {
        ioExecutor.execute {
            val list = categoryDao.getAll() ?: emptyList()
            val loadedIds = list.mapNotNull { it.id }
            nextTempUiKey = -1L
            val mapped = list.map { entity ->
                EditableCategory(
                    id = entity.id,
                    name = entity.name,
                    uiKey = entity.id ?: createTempUiKey()
                )
            }
            runOnUiThread {
                originalIds.clear()
                originalIds.addAll(loadedIds)
                categories.clear()
                categories.addAll(mapped)
                baselineState = mapped.map { it.id to it.name }
            }
        }
    }

    private fun moveCategory(from: Int, to: Int) {
        if (from !in categories.indices || to !in categories.indices || from == to) return
        val item = categories.removeAt(from)
        categories.add(to, item)
    }

    private fun tryDeleteCategory(index: Int) {
        if (index !in categories.indices) return
        val target = categories[index]
        val targetUiKey = target.uiKey
        val categoryId = target.id
        if (categoryId == null) {
            categories.removeAt(index)
            return
        }
        ioExecutor.execute {
            val count = recipeDao.countByCategoryId(categoryId)
            runOnUiThread {
                if (count > 0) {
                    cannotDeleteDialogVisible = true
                    return@runOnUiThread
                }
                val removeIndex = categories.indexOfFirst { it.uiKey == targetUiKey }
                if (removeIndex >= 0) {
                    categories.removeAt(removeIndex)
                }
            }
        }
    }

    private fun saveCategories() {
        if (categories.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_categories_to_save_toast), Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true
        val snapshot = categories.toList()
        ioExecutor.execute {
            try {
                val currentIds = snapshot.mapNotNull { it.id }
                val toDelete = originalIds.filter { id -> !currentIds.contains(id) }
                if (toDelete.isNotEmpty()) {
                    categoryDao.deleteByIds(toDelete)
                }

                val entities = snapshot.mapIndexed { index, item ->
                    CategoryEntity(item.id, item.name, index + 1)
                }
                categoryDao.insertAll(entities)

                runOnUiThread {
                    isSaving = false
                    App.getInstance().requestAutoWebDavUploadIfConfigured()
                    Toast.makeText(this, getString(R.string.categories_saved_toast), Toast.LENGTH_SHORT).show()
                    baselineState = snapshot.map { it.id to it.name }
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    isSaving = false
                    Toast.makeText(
                        this,
                        getString(R.string.save_failed_toast, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createTempUiKey(): Long {
        val key = nextTempUiKey
        nextTempUiKey -= 1L
        return key
    }

    private fun requestBackWithUnsavedCheck() {
        if (isSaving) return
        if (hasUnsavedChanges()) {
            unsavedBackDialogVisible = true
        } else {
            finish()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentState = categories.map { it.id to it.name }
        return currentState != baselineState
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManageScreen(
    categories: List<EditableCategory>,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit
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
                            Text(stringResource(R.string.category_manage_title), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(R.string.category_manage_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !isSaving) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_back_gold),
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onSave, enabled = !isSaving) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save_gold),
                                contentDescription = stringResource(R.string.common_save),
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
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                CategoryList(
                    categories = categories,
                    onMove = onMove,
                    onAdd = onAdd,
                    onEdit = onEdit,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<EditableCategory>,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    var draggingItemUiKey by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    val swapThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(
            items = categories,
            key = { _, item -> item.uiKey }
        ) { index, category ->
            val isDragging = draggingItemUiKey == category.uiKey
            CategoryItemRow(
                category = category,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .then(
                        if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .offset { IntOffset(0, draggedOffsetY.roundToInt()) }
                        } else {
                            Modifier
                        }
                    ),
                onEdit = { onEdit(index) },
                onDelete = { onDelete(index) },
                dragHandleModifier = Modifier.pointerInput(category.uiKey) {
                    detectDragGestures(
                        onDragStart = {
                            draggingItemUiKey = category.uiKey
                            draggedOffsetY = 0f
                        },
                        onDragEnd = {
                            draggingItemUiKey = null
                            draggedOffsetY = 0f
                        },
                        onDragCancel = {
                            draggingItemUiKey = null
                            draggedOffsetY = 0f
                        },
                        onDrag = { _, dragAmount ->
                            val activeKey = draggingItemUiKey ?: return@detectDragGestures
                            val currentIndex = categories.indexOfFirst { it.uiKey == activeKey }
                            if (currentIndex == -1) return@detectDragGestures
                            draggedOffsetY += dragAmount.y
                            if (draggedOffsetY > swapThresholdPx && currentIndex < categories.lastIndex) {
                                onMove(currentIndex, currentIndex + 1)
                                draggedOffsetY -= swapThresholdPx
                            } else if (draggedOffsetY < -swapThresholdPx && currentIndex > 0) {
                                onMove(currentIndex, currentIndex - 1)
                                draggedOffsetY += swapThresholdPx
                            }
                        }
                    )
                }
            )
        }

        item(key = "add-row") {
            AddCategoryRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                onAdd = onAdd
            )
        }
    }
}

@Composable
private fun CategoryItemRow(
    category: EditableCategory,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.name,
            modifier = Modifier.weight(1f),
            color = DogCuisineColors.TextPrimary,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_edit_gold),
                contentDescription = stringResource(R.string.content_desc_edit),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete_gold),
                contentDescription = stringResource(R.string.content_desc_delete),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_sort_gold),
                contentDescription = stringResource(R.string.content_desc_reorder),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun AddCategoryRow(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .height(56.dp)
            .padding(horizontal = 12.dp)
            .clickable(onClick = onAdd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_add_gold),
            contentDescription = stringResource(R.string.content_desc_add_category),
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.add_category_title),
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
