package com.dogcuisine.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dogcuisine.App
import com.dogcuisine.R
import com.dogcuisine.data.CategoryDao
import com.dogcuisine.data.CategoryEntity
import com.dogcuisine.data.LevelConfig
import com.dogcuisine.data.RecipeDao
import com.dogcuisine.data.RecipeEntity
import com.dogcuisine.data.StepItem
import com.dogcuisine.data.UserProfileDao
import com.dogcuisine.data.UserProfileEntity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import android.app.Dialog as AndroidDialog

class AddRecipeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RECIPE_ID = "recipe_id"
        private const val MAX_IMAGE_LONG_EDGE = 1600
        private const val TARGET_IMAGE_BYTES = 600 * 1024
        private const val JPEG_QUALITY_START = 85
        private const val JPEG_QUALITY_MIN = 65
    }

    private lateinit var ioExecutor: ExecutorService
    private lateinit var recipeDao: RecipeDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var userProfileDao: UserProfileDao
    private val gson = Gson()

    private var recipeName by mutableStateOf("")
    private var coverPath by mutableStateOf<String?>(null)
    private var selectedCategoryId by mutableStateOf<Long?>(null)
    private var editingFavorite = 0

    private var ingredientText by mutableStateOf("")
    private val ingredientImages = mutableStateListOf<String>()
    private val steps = mutableStateListOf<StepItem>()
    private val categoryOptions = mutableStateListOf<CategoryEntity>()
    private lateinit var stepAdapter: StepAdapter
    private lateinit var stepTouchHelper: ItemTouchHelper
    private var boundStepRecyclerView: RecyclerView? = null

    private var previewImagePath by mutableStateOf<String?>(null)
    private var scrollToBottomToken by mutableIntStateOf(0)

    private lateinit var coverPicker: ActivityResultLauncher<Array<String>>
    private lateinit var coverCropLauncher: ActivityResultLauncher<Intent>
    private lateinit var ingredientImagesPicker: ActivityResultLauncher<Array<String>>
    private lateinit var stepImagesPicker: ActivityResultLauncher<Array<String>>
    private var pendingStepIndex = -1

    private var editingId = -1L
    private var existingCreatedAt = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)

        val app = App.getInstance()
        ioExecutor = app.ioExecutor()
        recipeDao = app.getDatabase().recipeDao()
        categoryDao = app.getDatabase().categoryDao()
        userProfileDao = app.getDatabase().userProfileDao()

        registerPickers()

        if (steps.isEmpty()) {
            steps.add(StepItem())
        }
        setupStepListAdapter()

        editingId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
        if (editingId > 0L) {
            loadForEdit(editingId)
        }
        loadCategoriesForDropdown()

        setContent {
            AddRecipeTheme {
                AddRecipeScreen(
                    title = if (editingId > 0L) "编辑菜谱" else getString(R.string.add_recipe_title),
                    recipeName = recipeName,
                    coverPath = coverPath,
                    selectedCategoryId = selectedCategoryId,
                    categoryOptions = categoryOptions,
                    ingredientText = ingredientText,
                    ingredientImages = ingredientImages,
                    scrollToBottomToken = scrollToBottomToken,
                    onBack = { finish() },
                    onSave = { saveRecipe() },
                    onPickCover = { pickCover() },
                    onNameChange = { recipeName = it },
                    onCategorySelected = { selectedCategoryId = it },
                    onIngredientTextClick = {
                        showBottomTextEditor("编辑食材", ingredientText) { text ->
                            ingredientText = text
                        }
                    },
                    onAddIngredientImages = { ingredientImagesPicker.launch(arrayOf("image/*")) },
                    onIngredientImageClick = { path -> previewImagePath = path },
                    onIngredientImageDelete = { path -> removeIngredientImage(path) },
                    onAddStep = { addStep() },
                    onBindStepRecyclerView = { recyclerView ->
                        bindStepRecyclerView(recyclerView)
                    }
                )

                val imagePath = previewImagePath
                if (!imagePath.isNullOrEmpty()) {
                    ImagePreviewDialog(
                        imagePath = imagePath,
                        onDismiss = { previewImagePath = null }
                    )
                }
            }
        }
    }

    private fun setupStepListAdapter() {
        stepAdapter = StepAdapter(
            steps,
            StepAdapter.StepImageAddListener { position ->
                if (position !in steps.indices) return@StepImageAddListener
                pendingStepIndex = position
                stepImagesPicker.launch(arrayOf("image/*"))
            },
            StepAdapter.StepDeleteListener { position ->
                if (position !in steps.indices) return@StepDeleteListener
                steps.removeAt(position)
                stepAdapter.notifyItemRemoved(position)
                stepAdapter.notifyItemRangeChanged(position, (steps.size - position).coerceAtLeast(0))
            },
            StepAdapter.StepTextClickListener { position, currentText ->
                if (position !in steps.indices) return@StepTextClickListener
                showBottomTextEditor("编辑步骤", currentText) { text ->
                    updateStepText(position, text)
                }
            }
        )
        stepTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    stepAdapter.moveItem(from, to)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No-op, swipe delete is not supported.
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.33f

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                viewHolder?.itemView?.let { itemView ->
                    ViewCompat.setElevation(itemView, 0f)
                    ViewCompat.setTranslationZ(itemView, 0f)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                ViewCompat.setElevation(viewHolder.itemView, 0f)
                ViewCompat.setTranslationZ(viewHolder.itemView, 0f)
            }
        })
        stepAdapter.setDragStartListener { viewHolder ->
            stepTouchHelper.startDrag(viewHolder)
        }
    }

    private fun bindStepRecyclerView(recyclerView: RecyclerView) {
        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        if (recyclerView.itemAnimator != null) {
            recyclerView.itemAnimator = null
        }
        if (recyclerView.adapter !== stepAdapter) {
            recyclerView.adapter = stepAdapter
        }
        if (boundStepRecyclerView !== recyclerView) {
            stepTouchHelper.attachToRecyclerView(recyclerView)
            boundStepRecyclerView = recyclerView
        }
    }

    private fun registerPickers() {
        coverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                startCoverCrop(uri)
            }
        }

        coverCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                return@registerForActivityResult
            }
            val raw = result.data?.getStringExtra(CropCoverActivity.EXTRA_CROPPED_URI)
            if (raw.isNullOrEmpty()) {
                return@registerForActivityResult
            }
            val uri = Uri.parse(raw)
            val path = copyAndCompressCroppedCover(uri)
            if (!path.isNullOrEmpty()) {
                coverPath = path
            }
        }

        stepImagesPicker =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                val stepIndex = pendingStepIndex
                pendingStepIndex = -1
                if (stepIndex !in steps.indices || uris.isNullOrEmpty()) return@registerForActivityResult
                val copiedPaths = uris.mapNotNull { uri ->
                    copyAndCompressImage(uri, "step$stepIndex")
                }
                if (copiedPaths.isEmpty()) return@registerForActivityResult
                steps[stepIndex].addImagePaths(copiedPaths)
                stepAdapter.notifyItemChanged(stepIndex)
            }

        ingredientImagesPicker =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNullOrEmpty()) return@registerForActivityResult
                val copiedPaths = uris.mapNotNull { uri ->
                    copyAndCompressImage(uri, "ingredient")
                }
                if (copiedPaths.isNotEmpty()) {
                    ingredientImages.addAll(copiedPaths)
                }
            }
    }

    private fun pickCover() {
        coverPicker.launch(arrayOf("image/*"))
    }

    private fun startCoverCrop(sourceUri: Uri) {
        val intent = Intent(this, CropCoverActivity::class.java)
        intent.putExtra(CropCoverActivity.EXTRA_SOURCE_URI, sourceUri.toString())
        coverCropLauncher.launch(intent)
    }

    private fun addStep() {
        steps.add(StepItem())
        stepAdapter.notifyItemInserted(steps.lastIndex)
        scrollToBottomToken += 1
    }

    private fun updateStepText(index: Int, text: String) {
        if (index !in steps.indices) return
        steps[index].text = text
        stepAdapter.notifyItemChanged(index)
    }

    private fun removeIngredientImage(path: String) {
        ingredientImages.remove(path)
    }

    private fun showBottomTextEditor(
        title: String,
        initialText: String?,
        onConfirm: (String) -> Unit
    ) {
        val dialog = BottomSheetDialog(this, R.style.ThemeOverlay_DogCuisine_BottomSheetDialog)
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_bottom_text_editor, null, false)
        val tvTitle = content.findViewById<TextView>(R.id.tvBottomEditorTitle)
        val etInput = content.findViewById<EditText>(R.id.etBottomEditorInput)
        val btnCancel = content.findViewById<Button>(R.id.btnBottomEditorCancel)
        val btnConfirm = content.findViewById<Button>(R.id.btnBottomEditorConfirm)

        tvTitle.text = title
        etInput.setText(initialText.orEmpty())
        etInput.setSelection(etInput.text?.length ?: 0)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            onConfirm(etInput.text?.toString().orEmpty())
            dialog.dismiss()
        }

        dialog.setContentView(content)
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )
            val behavior = dialog.behavior
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            etInput.post {
                etInput.isFocusableInTouchMode = true
                etInput.requestFocus()
                showKeyboard(etInput)
            }
            etInput.postDelayed({
                if (!etInput.hasFocus()) {
                    etInput.requestFocus()
                }
                showKeyboard(etInput)
            }, 80)
        }
        dialog.show()
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun saveRecipe() {
        val name = recipeName.trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入菜谱名称", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val ingredientJson = gson.toJson(
            StepItem(ingredientText, ArrayList(ingredientImages))
        )
        val stepsJson = gson.toJson(steps)
        val editingIdSnapshot = editingId
        val existingCreatedAtSnapshot = existingCreatedAt
        val coverPathSnapshot = coverPath

        ioExecutor.execute {
            var categoryId = selectedCategoryId
            if (categoryId == null) {
                categoryId = fetchDefaultCategoryId()
            }

            val entity = if (editingIdSnapshot > 0L) {
                RecipeEntity(
                    editingIdSnapshot,
                    name,
                    if (existingCreatedAtSnapshot > 0L) existingCreatedAtSnapshot else now,
                    now,
                    "",
                    coverPathSnapshot,
                    stepsJson,
                    ingredientJson,
                    categoryId,
                    editingFavorite
                )
            } else {
                RecipeEntity(
                    null,
                    name,
                    now,
                    now,
                    "",
                    coverPathSnapshot,
                    stepsJson,
                    ingredientJson,
                    categoryId,
                    0
                )
            }
            recipeDao.insert(entity)

            val levelUpName = if (editingIdSnapshot > 0L) null else checkLevelUpIfNeeded()
            runOnUiThread {
                App.getInstance().requestAutoWebDavUploadIfConfigured()
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
                if (!levelUpName.isNullOrEmpty()) {
                    showLevelUpDialog(levelUpName)
                } else {
                    finishAfterSave()
                }
            }
        }
    }

    private fun loadForEdit(id: Long) {
        ioExecutor.execute {
            val entity = recipeDao.getById(id)
            runOnUiThread {
                if (entity == null) {
                    Toast.makeText(this, "未找到菜谱", Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                existingCreatedAt = entity.createdAt
                selectedCategoryId = entity.categoryId
                editingFavorite = entity.isFavorite
                recipeName = entity.name.orEmpty()
                coverPath = entity.coverImagePath

                val loadedIngredient = parseIngredient(entity.ingredientJson)
                ingredientText = loadedIngredient.text.orEmpty()
                ingredientImages.clear()
                ingredientImages.addAll(loadedIngredient.imagePaths ?: emptyList())

                val loadedSteps = parseSteps(entity.stepsJson)
                resetSteps(loadedSteps)
            }
        }
    }

    private fun resetSteps(items: List<StepItem>) {
        steps.clear()
        if (items.isEmpty()) {
            steps.add(StepItem())
        } else {
            items.forEach { item ->
                steps.add(StepItem(item.text, ArrayList(item.imagePaths ?: emptyList())))
            }
        }
        stepAdapter.notifyDataSetChanged()
    }

    private fun loadCategoriesForDropdown() {
        ioExecutor.execute {
            val all = categoryDao.getAll() ?: emptyList()
            var nextSelected = selectedCategoryId
            if (nextSelected == null && editingId <= 0L && all.isNotEmpty()) {
                nextSelected = all.first().id
            }
            if (nextSelected != null && all.none { it.id == nextSelected }) {
                nextSelected = all.firstOrNull()?.id
            }
            runOnUiThread {
                categoryOptions.clear()
                categoryOptions.addAll(all)
                selectedCategoryId = nextSelected
            }
        }
    }

    @Nullable
    private fun fetchDefaultCategoryId(): Long? {
        return try {
            val categories = categoryDao.getAll()
            if (categories.isNullOrEmpty()) {
                null
            } else {
                categories.first().id
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSteps(json: String?): List<StepItem> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<StepItem>>() {}.type
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

    @Nullable
    private fun checkLevelUpIfNeeded(): String? {
        return try {
            val recipeCount = recipeDao.count()
            var profile = userProfileDao.profile
            if (profile == null) {
                val id = userProfileDao.insert(UserProfileEntity(0, null))
                profile = UserProfileEntity(id, null)
            }

            val currentLevel = profile.currentLevel
            if (currentLevel.isNullOrBlank()) {
                val reachedIndex = LevelConfig.getHighestReachedIndex(recipeCount)
                if (reachedIndex >= 0) {
                    val profileId = ensureProfileId(profile)
                    val levelName = LevelConfig.getLevelName(reachedIndex) ?: return null
                    userProfileDao.updateLevel(profileId, levelName)
                    return levelName
                }
                return null
            }

            val currentIndex = LevelConfig.getLevelIndex(currentLevel)
            if (currentIndex < 0) {
                val reachedIndex = LevelConfig.getHighestReachedIndex(recipeCount)
                if (reachedIndex >= 0) {
                    val profileId = ensureProfileId(profile)
                    val levelName = LevelConfig.getLevelName(reachedIndex) ?: return null
                    userProfileDao.updateLevel(profileId, levelName)
                    return levelName
                }
                return null
            }

            val nextIndex = currentIndex + 1
            if (nextIndex < LevelConfig.size() && recipeCount >= LevelConfig.getRequiredCount(nextIndex)) {
                val profileId = ensureProfileId(profile)
                val levelName = LevelConfig.getLevelName(nextIndex) ?: return null
                userProfileDao.updateLevel(profileId, levelName)
                return levelName
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun ensureProfileId(profile: UserProfileEntity): Long {
        if (profile.id != 0L) {
            return profile.id
        }
        return userProfileDao.insert(UserProfileEntity(0, profile.currentLevel))
    }

    private fun showLevelUpDialog(levelName: String) {
        val dialog = AndroidDialog(this)
        dialog.setContentView(R.layout.dialog_level_up)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.findViewById<TextView>(R.id.tvLevelName)?.text = levelName
        dialog.findViewById<View>(R.id.levelUpRoot)?.setOnClickListener { dialog.dismiss() }

        dialog.findViewById<View>(R.id.levelUpCard)?.let { card ->
            val width = resources.displayMetrics.widthPixels
            val target = (width * 0.75f).roundToInt()
            val params = card.layoutParams
            params.width = target
            card.layoutParams = params
            card.setOnClickListener { dialog.dismiss() }
        }

        var animated: AnimatedImageDrawable? = null
        dialog.findViewById<ImageView>(R.id.ivCelebrate)?.let { celebrate ->
            val drawable = celebrate.drawable
            if (drawable is AnimatedImageDrawable) {
                animated = drawable
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                drawable.start()
            }
        }

        dialog.setOnDismissListener {
            animated?.stop()
            finishAfterSave()
        }
        dialog.show()
    }

    private fun finishAfterSave() {
        setResult(RESULT_OK)
        finish()
    }

    @Nullable
    private fun copyAndCompressImage(uri: Uri, prefix: String): String? {
        return try {
            val bitmap = decodeBitmapWithSample(uri, MAX_IMAGE_LONG_EDGE) ?: return null
            val scaledBitmap = scaleBitmapIfNeeded(bitmap, MAX_IMAGE_LONG_EDGE)
            val rotatedBitmap = rotateByExifIfNeeded(scaledBitmap, uri)
            val compressed = compressToJpegBytes(rotatedBitmap)

            val dir = File(filesDir, "images")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val fileName = "${prefix}_${System.currentTimeMillis()}.jpg"
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { fos ->
                fos.write(compressed)
                fos.flush()
            }

            if (!rotatedBitmap.isRecycled) rotatedBitmap.recycle()
            if (scaledBitmap !== rotatedBitmap && !scaledBitmap.isRecycled) scaledBitmap.recycle()
            if (bitmap !== scaledBitmap && bitmap !== rotatedBitmap && !bitmap.isRecycled) bitmap.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    @Nullable
    private fun copyAndCompressCroppedCover(croppedUri: Uri): String? {
        return try {
            val bitmap = decodeBitmapWithSample(croppedUri, MAX_IMAGE_LONG_EDGE) ?: return null
            val square = cropCenterSquareIfNeeded(bitmap)
            val scaled = scaleBitmapIfNeeded(square, MAX_IMAGE_LONG_EDGE)
            val compressed = compressToJpegBytes(scaled)

            val dir = File(filesDir, "images")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val outFile = File(dir, "cover_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                fos.write(compressed)
                fos.flush()
            }

            if (!scaled.isRecycled) scaled.recycle()
            if (square !== scaled && !square.isRecycled) square.recycle()
            if (bitmap !== square && bitmap !== scaled && !bitmap.isRecycled) bitmap.recycle()
            outFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    @Nullable
    @Throws(Exception::class)
    private fun decodeBitmapWithSample(uri: Uri, maxLongEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val decode = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
        }
        return contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            BitmapFactory.decodeStream(input, null, decode)
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
        var inSampleSize = 1
        val longEdge = maxOf(width, height)
        while (longEdge / inSampleSize > maxLongEdge * 2) {
            inSampleSize *= 2
        }
        return maxOf(1, inSampleSize)
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxLongEdge: Int): Bitmap {
        val width = source.width
        val height = source.height
        val longEdge = maxOf(width, height)
        if (longEdge <= maxLongEdge) {
            return source
        }
        val ratio = maxLongEdge / longEdge.toFloat()
        val targetW = maxOf(1, (width * ratio).roundToInt())
        val targetH = maxOf(1, (height * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (scaled !== source) {
            source.recycle()
        }
        return scaled
    }

    private fun rotateByExifIfNeeded(source: Bitmap, uri: Uri): Bitmap {
        var orientation = ExifInterface.ORIENTATION_UNDEFINED
        try {
            contentResolver.openInputStream(uri).use { input ->
                if (input != null) {
                    val exif = ExifInterface(input)
                    orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            }
        } catch (_: Exception) {
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return source
        }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) {
            source.recycle()
        }
        return rotated
    }

    private fun compressToJpegBytes(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        var quality = JPEG_QUALITY_START
        while (true) {
            baos.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (baos.size() <= TARGET_IMAGE_BYTES || quality <= JPEG_QUALITY_MIN) {
                break
            }
            quality -= 5
        }
        return baos.toByteArray()
    }

    private fun cropCenterSquareIfNeeded(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0 || width == height) {
            return source
        }
        val side = minOf(width, height)
        val left = (width - side) / 2
        val top = (height - side) / 2
        val square = Bitmap.createBitmap(source, left, top, side, side)
        if (square !== source) {
            source.recycle()
        }
        return square
    }
}

@Composable
private fun AddRecipeTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = ComposeColor(0xFFFEF1A5),
        onPrimary = ComposeColor(0xFF2C2518),
        secondary = ComposeColor(0xFF4A3F2B),
        onSecondary = ComposeColor.White,
        secondaryContainer = ComposeColor(0xFFF2EAD6),
        onSecondaryContainer = ComposeColor(0xFF2C2518),
        surface = ComposeColor(0xFFFFFCF3),
        surfaceVariant = ComposeColor(0xFFF7F2E6),
        onSurface = ComposeColor(0xFF2B2822),
        onSurfaceVariant = ComposeColor(0xFF5A5448),
        outline = ComposeColor(0xFFD8CFBA)
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecipeScreen(
    title: String,
    recipeName: String,
    coverPath: String?,
    selectedCategoryId: Long?,
    categoryOptions: List<CategoryEntity>,
    ingredientText: String,
    ingredientImages: List<String>,
    scrollToBottomToken: Int,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPickCover: () -> Unit,
    onNameChange: (String) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onIngredientTextClick: () -> Unit,
    onAddIngredientImages: () -> Unit,
    onIngredientImageClick: (String) -> Unit,
    onIngredientImageDelete: (String) -> Unit,
    onAddStep: () -> Unit,
    onBindStepRecyclerView: (RecyclerView) -> Unit
) {
    val listState = rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ComposeColor(0xFFFFF8E8), ComposeColor(0xFFFFFDF7))
                )
            )
    ) {
        Scaffold(
            containerColor = ComposeColor.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(text = title, fontWeight = FontWeight.SemiBold) },
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
                        IconButton(onClick = onSave) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_save_gold),
                                contentDescription = "保存",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .navigationBarsPadding()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddStep,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "添加步骤", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "cover") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CoverSelector(
                            coverPath = coverPath,
                            onPickCover = onPickCover
                        )
                    }
                }
                item(key = "name-title") {
                    SectionTitle(text = "菜谱名称")
                }
                item(key = "name-input") {
                    OutlinedTextField(
                        value = recipeName,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("请输入菜谱名称") },
                        colors = appOutlinedTextFieldColors()
                    )
                }
                item(key = "category-title") {
                    SectionTitle(text = "菜谱分类")
                }
                item(key = "category-selector") {
                    CategorySelector(
                        categoryOptions = categoryOptions,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = onCategorySelected
                    )
                }
                item(key = "ingredient-title") {
                    SectionTitle(text = "食材")
                }
                item(key = "ingredient-card") {
                    IngredientCard(
                        text = ingredientText,
                        imagePaths = ingredientImages,
                        onTextClick = onIngredientTextClick,
                        onAddImages = onAddIngredientImages,
                        onImageClick = onIngredientImageClick,
                        onImageDelete = onIngredientImageDelete
                    )
                }
                item(key = "steps-title") {
                    SectionTitle(text = "步骤")
                }
                item(key = "step-list") {
                    StepRecyclerList(
                        onBindStepRecyclerView = onBindStepRecyclerView
                    )
                }
                item(key = "tail-spacing") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    LaunchedEffect(scrollToBottomToken) {
        if (scrollToBottomToken <= 0) return@LaunchedEffect
        delay(80)
        val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        listState.animateScrollToItem(lastIndex)
    }
}

@Composable
private fun CoverSelector(
    coverPath: String?,
    onPickCover: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onPickCover)
    ) {
        if (!coverPath.isNullOrEmpty()) {
            LocalImage(
                imagePath = coverPath,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "添加封面",
                color = ComposeColor(0xFF6B7280),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = ComposeColor(0xFF111827)
    )
}

@Composable
private fun appOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categoryOptions: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categoryOptions.firstOrNull { it.id == selectedCategoryId }?.name
        ?: categoryOptions.firstOrNull()?.name
        ?: "暂无分类"

    ExposedDropdownMenuBox(
        expanded = expanded && categoryOptions.isNotEmpty(),
        onExpandedChange = { canExpand ->
            if (categoryOptions.isNotEmpty()) {
                expanded = canExpand
            }
        }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = appOutlinedTextFieldColors()
        )

        ExposedDropdownMenu(
            expanded = expanded && categoryOptions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            categoryOptions.forEach { category ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text = category.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        expanded = false
                        onCategorySelected(category.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun IngredientCard(
    text: String,
    imagePaths: List<String>,
    onTextClick: () -> Unit,
    onAddImages: () -> Unit,
    onImageClick: (String) -> Unit,
    onImageDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TextProxyField(
                text = text,
                hint = "请输入食材文字（可选）",
                onClick = onTextClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImageStrip(
                imagePaths = imagePaths,
                onImageClick = onImageClick,
                onImageDelete = onImageDelete
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onAddImages, modifier = Modifier.fillMaxWidth()) {
                Text(text = "添加图片", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StepRecyclerList(
    onBindStepRecyclerView: (RecyclerView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            RecyclerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        update = { recyclerView ->
            onBindStepRecyclerView(recyclerView)
        }
    )
}

@Composable
private fun TextProxyField(
    text: String,
    hint: String,
    onClick: () -> Unit
) {
    val hasText = text.isNotBlank()
    val alpha by animateFloatAsState(targetValue = if (hasText) 1f else 0.78f, label = "text_alpha")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = if (hasText) text else hint,
            color = if (hasText) ComposeColor(0xFF111827) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer(alpha = alpha)
        )
    }
}

@Composable
private fun ImageStrip(
    imagePaths: List<String>,
    onImageClick: (String) -> Unit,
    onImageDelete: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        imagePaths.forEach { path ->
            ImageTile(
                path = path,
                onImageClick = { onImageClick(path) },
                onImageDelete = { onImageDelete(path) }
            )
        }
    }
}

@Composable
private fun ImageTile(
    path: String,
    onImageClick: () -> Unit,
    onImageDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        LocalImage(
            imagePath = path,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 8.dp, top = 8.dp)
                .clickable(onClick = onImageClick)
        )
        IconButton(
            onClick = onImageDelete,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_gold),
                contentDescription = "删除图片",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun LocalImage(
    imagePath: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                scaleType = if (contentScale == ContentScale.Fit) {
                    ImageView.ScaleType.FIT_CENTER
                } else {
                    ImageView.ScaleType.CENTER_CROP
                }
            }
        },
        update = { view ->
            view.scaleType = if (contentScale == ContentScale.Fit) {
                ImageView.ScaleType.FIT_CENTER
            } else {
                ImageView.ScaleType.CENTER_CROP
            }
            view.setImageURI(Uri.fromFile(File(imagePath)))
        }
    )
}

@Composable
private fun ImagePreviewDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor(0xCC000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            LocalImage(
                imagePath = imagePath,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
