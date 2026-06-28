package com.dogcuisine.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
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
import androidx.annotation.AttrRes
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import kotlin.math.roundToInt
import android.app.Dialog as AndroidDialog
import androidx.compose.ui.graphics.Color as ComposeColor

class AddRecipeActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_RECIPE_ID = "recipe_id"
        private const val MAX_IMAGE_LONG_EDGE = 1600
        private const val TARGET_IMAGE_BYTES = 600 * 1024
        private const val JPEG_QUALITY_START = 85
        private const val JPEG_QUALITY_MIN = 65

        fun createIntent(context: Context): Intent {
            return Intent(context, AddRecipeActivity::class.java)
        }

        fun createIntent(context: Context, recipeId: Long): Intent {
            return Intent(context, AddRecipeActivity::class.java)
                .putExtra(EXTRA_RECIPE_ID, recipeId)
        }
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
    private var editingRating by mutableStateOf(0)
    private var isSaving by mutableStateOf(false)

    private var ingredientText by mutableStateOf("")
    private val ingredientImages = mutableStateListOf<String>()
    private val steps = mutableStateListOf<StepItem>()
    private val stepStableKeys = mutableStateListOf<Long>()
    private var nextStepStableKey = 0L
    private val categoryOptions = mutableStateListOf<CategoryEntity>()

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
            stepStableKeys.add(obtainNextStepStableKey())
        }

        editingId = intent.getLongExtra(EXTRA_RECIPE_ID, -1L)
        if (editingId > 0L) {
            loadForEdit(editingId)
        }
        loadCategoriesForDropdown()

        setContent {
            DogCuisineTheme {
                AddRecipeScreen(
                    title = if (editingId > 0L) {
                        getString(R.string.add_recipe_edit_title)
                    } else {
                        getString(R.string.add_recipe_title)
                    },
                    recipeName = recipeName,
                    coverPath = coverPath,
                    selectedCategoryId = selectedCategoryId,
                    categoryOptions = categoryOptions,
                    rating = editingRating,
                    ingredientText = ingredientText,
                    ingredientImages = ingredientImages,
                    steps = steps,
                    stepKeys = stepStableKeys,
                    scrollToBottomToken = scrollToBottomToken,
                    isSaving = isSaving,
                    onBack = { finish() },
                    onSave = { saveRecipe() },
                    onPickCover = { pickCover() },
                    onNameChange = { recipeName = it },
                    onCategorySelected = { selectedCategoryId = it },
                    onRatingChange = { editingRating = it },
                    onIngredientTextClick = {
                        showBottomTextEditor(getString(R.string.edit_ingredient_title), ingredientText) { text ->
                            ingredientText = text
                        }
                    },
                    onAddIngredientImages = { ingredientImagesPicker.launch(arrayOf("image/*")) },
                    onIngredientImageClick = { path -> previewImagePath = path },
                    onIngredientImageDelete = { path -> removeIngredientImage(path) },
                    onAddStep = { addStep() },
                    onStepTextClick = { index, currentText ->
                        if (index in steps.indices) {
                            showBottomTextEditor(getString(R.string.edit_step_title), currentText) { text ->
                                updateStepText(index, text)
                            }
                        }
                    },
                    onAddStepImages = { index ->
                        if (index in steps.indices) {
                            pendingStepIndex = index
                            stepImagesPicker.launch(arrayOf("image/*"))
                        }
                    },
                    onDeleteStep = { index ->
                        if (index in steps.indices) {
                            steps.removeAt(index)
                            stepStableKeys.removeAt(index)
                        }
                    },
                    onMoveStep = { fromIndex, toIndex ->
                        moveStep(fromIndex, toIndex)
                    },
                    onStepImageClick = { path -> previewImagePath = path },
                    onStepImageDelete = { index, path ->
                        removeStepImage(index, path)
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
                val current = steps[stepIndex]
                val mergedPaths = ArrayList(current.imagePaths ?: emptyList())
                mergedPaths.addAll(copiedPaths)
                steps[stepIndex] = StepItem(current.text, mergedPaths)
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
        coverCropLauncher.launch(CropCoverActivity.createIntent(this, sourceUri.toString()))
    }

    private fun obtainNextStepStableKey(): Long {
        val key = nextStepStableKey
        nextStepStableKey += 1
        return key
    }

    private fun addStep() {
        steps.add(StepItem())
        stepStableKeys.add(obtainNextStepStableKey())
        scrollToBottomToken += 1
    }

    private fun updateStepText(index: Int, text: String) {
        if (index !in steps.indices) return
        val current = steps[index]
        steps[index] = StepItem(text, ArrayList(current.imagePaths ?: emptyList()))
    }

    private fun removeStepImage(index: Int, path: String) {
        if (index !in steps.indices) return
        val current = steps[index]
        val updatedPaths = ArrayList(current.imagePaths ?: emptyList())
        val removed = updatedPaths.remove(path)
        if (!removed) return
        steps[index] = StepItem(current.text, updatedPaths)
    }

    private fun moveStep(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        if (fromIndex !in steps.indices || toIndex !in steps.indices) return
        val moved = steps.removeAt(fromIndex)
        steps.add(toIndex, moved)
        val movedKey = stepStableKeys.removeAt(fromIndex)
        stepStableKeys.add(toIndex, movedKey)
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
        if (isSaving) return
        val name = recipeName.trim()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.recipe_name_required_toast), Toast.LENGTH_SHORT).show()
            return
        }
        isSaving = true

        val now = System.currentTimeMillis()
        val ingredientJson = gson.toJson(
            StepItem(ingredientText, ArrayList(ingredientImages))
        )
        val stepsJson = gson.toJson(steps)
        val editingIdSnapshot = editingId
        val existingCreatedAtSnapshot = existingCreatedAt
        val coverPathSnapshot = coverPath

        ioExecutor.execute {
            try {
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
                        editingFavorite,
                        editingRating
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
                        0,
                        editingRating
                    )
                }
                App.getInstance().getDatabase().recipeDao().insert(entity)

                val levelUpName = if (editingIdSnapshot > 0L) null else checkLevelUpIfNeeded()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    App.getInstance().requestAutoWebDavUploadIfConfigured()
                    Toast.makeText(this, getString(R.string.save_success_toast), Toast.LENGTH_SHORT).show()
                    if (!levelUpName.isNullOrEmpty()) {
                        showLevelUpDialog(levelUpName)
                    } else {
                        finishAfterSave()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isSaving = false
                    Toast.makeText(
                        this,
                        getString(R.string.save_failed_toast, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadForEdit(id: Long) {
        ioExecutor.execute {
            val entity = recipeDao.getById(id)
            runOnUiThread {
                if (entity == null) {
                    Toast.makeText(this, getString(R.string.recipe_not_found_toast), Toast.LENGTH_SHORT).show()
                    finish()
                    return@runOnUiThread
                }
                existingCreatedAt = entity.createdAt
                selectedCategoryId = entity.categoryId
                editingFavorite = entity.isFavorite
                editingRating = entity.rating
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
        stepStableKeys.clear()
        if (items.isEmpty()) {
            steps.add(StepItem())
            stepStableKeys.add(obtainNextStepStableKey())
        } else {
            items.forEach { item ->
                steps.add(StepItem(item.text, ArrayList(item.imagePaths ?: emptyList())))
                stepStableKeys.add(obtainNextStepStableKey())
            }
        }
    }

    private fun loadCategoriesForDropdown() {
        ioExecutor.execute {
            val all = categoryDao.getAll() ?: emptyList()
            runOnUiThread {
                categoryOptions.clear()
                categoryOptions.addAll(all)
                val currentSelected = selectedCategoryId
                var nextSelected = currentSelected
                if (nextSelected == null && editingId <= 0L && all.isNotEmpty()) {
                    nextSelected = all.first().id
                }
                if (nextSelected != null && all.none { it.id == nextSelected }) {
                    nextSelected = all.firstOrNull()?.id
                }
                if (nextSelected != currentSelected) {
                    selectedCategoryId = nextSelected
                }
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
            val levelNames = resources.getStringArray(R.array.level_names)
            val maxLevelIndex = minOf(levelNames.size, LevelConfig.size())
            fun levelNameAt(index: Int): String? {
                return if (index in 0 until maxLevelIndex) levelNames[index] else null
            }
            fun levelIndexOf(name: String?): Int {
                if (name.isNullOrBlank()) return -1
                val index = levelNames.indexOf(name)
                return if (index in 0 until maxLevelIndex) index else -1
            }

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
                    val levelName = levelNameAt(reachedIndex) ?: return null
                    userProfileDao.updateLevel(profileId, levelName)
                    return levelName
                }
                return null
            }

            val currentIndex = levelIndexOf(currentLevel)
            if (currentIndex < 0) {
                val reachedIndex = LevelConfig.getHighestReachedIndex(recipeCount)
                if (reachedIndex >= 0) {
                    val profileId = ensureProfileId(profile)
                    val levelName = levelNameAt(reachedIndex) ?: return null
                    userProfileDao.updateLevel(profileId, levelName)
                    return levelName
                }
                return null
            }

            val nextIndex = currentIndex + 1
            if (nextIndex < LevelConfig.size() && recipeCount >= LevelConfig.getRequiredCount(nextIndex)) {
                val profileId = ensureProfileId(profile)
                val levelName = levelNameAt(nextIndex) ?: return null
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
                Toast.makeText(this, getString(R.string.image_process_failed_toast), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, getString(R.string.image_process_failed_toast), Toast.LENGTH_SHORT).show()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun AddRecipeScreen(
    title: String,
    recipeName: String,
    coverPath: String?,
    selectedCategoryId: Long?,
    categoryOptions: List<CategoryEntity>,
    rating: Int,
    ingredientText: String,
    ingredientImages: List<String>,
    steps: List<StepItem>,
    stepKeys: List<Long>,
    scrollToBottomToken: Int,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPickCover: () -> Unit,
    onNameChange: (String) -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onRatingChange: (Int) -> Unit,
    onIngredientTextClick: () -> Unit,
    onAddIngredientImages: () -> Unit,
    onIngredientImageClick: (String) -> Unit,
    onIngredientImageDelete: (String) -> Unit,
    onAddStep: () -> Unit,
    onStepTextClick: (Int, String) -> Unit,
    onAddStepImages: (Int) -> Unit,
    onDeleteStep: (Int) -> Unit,
    onMoveStep: (Int, Int) -> Unit,
    onStepImageClick: (String) -> Unit,
    onStepImageDelete: (Int, String) -> Unit
) {
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? Long
        val toKey = to.key as? Long
        val fromStepIndex = if (fromKey == null) -1 else stepKeys.indexOf(fromKey)
        val toStepIndex = if (toKey == null) -1 else stepKeys.indexOf(toKey)
        if (fromStepIndex >= 0 && toStepIndex >= 0 && fromStepIndex != toStepIndex) {
            onMoveStep(fromStepIndex, toStepIndex)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dogCuisineBackgroundBrush())
    ) {
        Scaffold(
            containerColor = DogCuisineColors.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(text = title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                        Text(text = stringResource(R.string.add_step), color = MaterialTheme.colorScheme.secondary)
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
                    SectionTitle(text = stringResource(R.string.recipe_name_label))
                }
                item(key = "name-input") {
                    OutlinedTextField(
                        value = recipeName,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.recipe_name_placeholder)) },
                        colors = appOutlinedTextFieldColors()
                    )
                }
                item(key = "category-title") {
                    SectionTitle(text = stringResource(R.string.recipe_category_label))
                }
                item(key = "category-selector") {
                    CategorySelector(
                        categoryOptions = categoryOptions,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = onCategorySelected
                    )
                }
                item(key = "rating-title") {
                    SectionTitle(text = stringResource(R.string.label_rating))
                }
                item(key = "rating-selector") {
                    RatingSelector(
                        rating = rating,
                        onRatingChange = onRatingChange
                    )
                }
                item(key = "ingredient-title") {
                    SectionTitle(text = stringResource(R.string.label_ingredient))
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
                    SectionTitle(text = stringResource(R.string.label_steps))
                }
                itemsIndexed(
                    items = steps,
                    key = { index, _ -> stepKeys.getOrElse(index) { index.toLong() } }
                ) { index, step ->
                    val stepItemKey = stepKeys.getOrElse(index) { index.toLong() }
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = stepItemKey
                    ) { isDragging ->
                        StepCard(
                            index = index,
                            step = step,
                            isDragging = isDragging,
                            onTextClick = { onStepTextClick(index, step.text.orEmpty()) },
                            onAddImages = { onAddStepImages(index) },
                            onImageClick = onStepImageClick,
                            onImageDelete = { path -> onStepImageDelete(index, path) },
                            onDeleteStep = { onDeleteStep(index) },
                            dragHandleModifier = with(this) { Modifier.longPressDraggableHandle() }
                        )
                    }
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
                text = stringResource(R.string.add_cover),
                color = DogCuisineColors.TextMuted,
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
        color = DogCuisineColors.TextPrimary
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
        ?: stringResource(R.string.category_empty)

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
                val selected = category.id == selectedCategoryId
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text = category.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.background(
                        if (selected) MaterialTheme.colorScheme.secondaryContainer else ComposeColor.Transparent
                    ),
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
private fun RatingSelector(
    rating: Int,
    onRatingChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (score in 1..5) {
            Icon(
                painter = painterResource(
                    id = if (score <= rating) {
                        R.drawable.ic_favorite_gold
                    } else {
                        R.drawable.ic_favorite_border_gold
                    }
                ),
                contentDescription = stringResource(R.string.recipe_rating_desc, score),
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onRatingChange(if (rating == score) 0 else score) }
                    .padding(4.dp)
            )
            if (score < 5) {
                Spacer(modifier = Modifier.width(8.dp))
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
                hint = stringResource(R.string.ingredient_text_hint),
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
                Text(text = stringResource(R.string.add_images), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: StepItem,
    isDragging: Boolean,
    onTextClick: () -> Unit,
    onAddImages: () -> Unit,
    onImageClick: (String) -> Unit,
    onImageDelete: (String) -> Unit,
    onDeleteStep: () -> Unit,
    dragHandleModifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 2.dp else 0.dp,
        label = "step_drag_elevation"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFFFFCF3)),
        border = BorderStroke(1.dp, ComposeColor(0xFFD8CFBA)),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.step_title, index + 1),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(id = R.drawable.ic_sort_gold),
                    contentDescription = stringResource(R.string.drag_reorder_desc),
                    modifier = dragHandleModifier
                        .padding(4.dp)
                        .size(24.dp),
                    tint = ComposeColor.Unspecified
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            StepTextProxyField(
                text = step.text.orEmpty(),
                onClick = onTextClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            ImageStrip(
                imagePaths = step.imagePaths ?: emptyList(),
                onImageClick = onImageClick,
                onImageDelete = onImageDelete
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onAddImages,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, ComposeColor(0xFFD8CFBA))
                ) {
                    Text(
                        text = stringResource(R.string.add_images),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDeleteStep,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.delete_step),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StepTextProxyField(
    text: String,
    onClick: () -> Unit
) {
    val hasText = text.isNotBlank()
    val alpha by animateFloatAsState(targetValue = if (hasText) 1f else 0.78f, label = "step_text_alpha")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0xFFF7F2E6))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = if (hasText) text else stringResource(R.string.step_text_hint),
            color = if (hasText) DogCuisineColors.TextPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer(alpha = alpha)
        )
    }
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
            color = if (hasText) DogCuisineColors.TextPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = imagePaths,
            key = { it }
        ) { path ->
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val containerSize = 70.dp
    val imageSize = 60.dp
    val imageMargin = 5.dp
    val imageSizePx = with(density) { imageSize.roundToPx() }
    val imageRequest = remember(path, imageSizePx) {
        ImageRequest.Builder(context)
            .data(File(path))
            .size(imageSizePx, imageSizePx)
            .crossfade(true)
            .build()
    }
    val placeholderPainter = remember {
        ColorPainter(ComposeColor(0xFFE0E0E0))
    }
    val closeBg = themeAttrColor(
        attr = com.google.android.material.R.attr.colorSecondaryContainer,
        fallback = MaterialTheme.colorScheme.secondaryContainer
    )
    val closeTint = themeAttrColor(
        attr = com.google.android.material.R.attr.colorOnSecondaryContainer,
        fallback = MaterialTheme.colorScheme.onSecondaryContainer
    )
    Box(
        modifier = Modifier
            .size(containerSize)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = imageMargin, y = -imageMargin)
                .requiredSize(imageSize)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onImageClick)
                .background(ComposeColor(0xFFE0E0E0))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = placeholderPainter,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(closeBg)
                .clickable(onClick = onImageDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close_gold),
                contentDescription = stringResource(R.string.delete_image_desc),
                tint = closeTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun themeAttrColor(
    @AttrRes attr: Int,
    fallback: ComposeColor
): ComposeColor {
    val context = LocalContext.current
    return remember(context, attr, fallback) {
        val typedValue = TypedValue()
        val resolved = context.theme.resolveAttribute(attr, typedValue, true)
        val colorInt = if (resolved) {
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            fallback.toArgb()
        }
        ComposeColor(colorInt)
    }
}

@Composable
private fun LocalImage(
    imagePath: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model = remember(imagePath) { File(imagePath).takeIf { it.exists() } }
    val placeholderPainter = remember { ColorPainter(ComposeColor(0xFFE0E0E0)) }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        placeholder = placeholderPainter,
        error = placeholderPainter,
        fallback = placeholderPainter,
        modifier = modifier
    )
}
