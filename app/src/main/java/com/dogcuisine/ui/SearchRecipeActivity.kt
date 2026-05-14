package com.dogcuisine.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dogcuisine.App
import com.dogcuisine.R
import com.dogcuisine.data.RecipeDao
import com.dogcuisine.data.RecipeEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService

class SearchRecipeActivity : AppCompatActivity() {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L

        fun createIntent(context: Context): Intent {
            return Intent(context, SearchRecipeActivity::class.java)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchResults = mutableStateListOf<RecipeEntity>()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    private lateinit var ioExecutor: ExecutorService
    private lateinit var recipeDao: RecipeDao

    private var keyword by mutableStateOf("")
    private var showResultPanel by mutableStateOf(false)
    private var pendingSearchTask: Runnable? = null
    private var searchVersion = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithoutAnimation()
            }
        })

        val app = App.getInstance()
        ioExecutor = app.ioExecutor()
        recipeDao = app.getDatabase().recipeDao()

        setContent {
            DogCuisineTheme {
                SearchRecipeScreen(
                    keyword = keyword,
                    showResultPanel = showResultPanel,
                    searchResults = searchResults,
                    formatUpdatedAt = { formatter.format(it) },
                    onKeywordChange = { onKeywordChanged(it) },
                    onBack = { finishWithoutAnimation() },
                    onCancel = { finishWithoutAnimation() },
                    onRecipeClick = { openRecipeDetail(it) }
                )
            }
        }
    }

    private fun onKeywordChanged(newKeyword: String) {
        keyword = newKeyword
        scheduleSearch(newKeyword.trim())
    }

    private fun scheduleSearch(trimmedKeyword: String) {
        pendingSearchTask?.let { mainHandler.removeCallbacks(it) }
        searchVersion++
        if (trimmedKeyword.isEmpty()) {
            searchResults.clear()
            showResultPanel = false
            return
        }
        val currentVersion = searchVersion
        pendingSearchTask = Runnable { doSearch(trimmedKeyword, currentVersion) }
        mainHandler.postDelayed(pendingSearchTask!!, SEARCH_DEBOUNCE_MS)
    }

    private fun doSearch(trimmedKeyword: String, currentVersion: Int) {
        val fuzzyKeyword = "%$trimmedKeyword%"
        ioExecutor.execute {
            val list = recipeDao.searchByKeyword(fuzzyKeyword) ?: emptyList()
            runOnUiThread {
                if (isFinishing || currentVersion != searchVersion) {
                    return@runOnUiThread
                }
                searchResults.clear()
                searchResults.addAll(list)
                showResultPanel = true
            }
        }
    }

    private fun openRecipeDetail(recipe: RecipeEntity) {
        val recipeId = recipe.id ?: return
        startActivity(RecipeDetailActivity.createIntent(this, recipeId))
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
    }
}

@Composable
private fun SearchRecipeScreen(
    keyword: String,
    showResultPanel: Boolean,
    searchResults: List<RecipeEntity>,
    formatUpdatedAt: (Long) -> String,
    onKeywordChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRecipeClick: (RecipeEntity) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var autoFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!autoFocusDone) {
            autoFocusDone = true
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        containerColor = DogCuisineColors.Transparent,
        topBar = {
            SearchTopBar(
                keyword = keyword,
                focusRequester = searchFocusRequester,
                onKeywordChange = onKeywordChange,
                onBack = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onBack()
                },
                onCancel = {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    onCancel()
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showResultPanel) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    if (searchResults.isEmpty()) {
                        EmptySearchState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = searchResults, key = { it.id ?: it.hashCode().toLong() }) { recipe ->
                                SearchResultItem(
                                    recipe = recipe,
                                    formattedTime = formatUpdatedAt(recipe.updatedAt),
                                    onClick = { onRecipeClick(recipe) }
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    keyword: String,
    focusRequester: FocusRequester,
    onKeywordChange: (String) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    TopAppBar(
        title = {
            SearchKeywordField(
                value = keyword,
                onValueChange = onKeywordChange,
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth()
            )
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
        actions = {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun SearchKeywordField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = DogCuisineColors.TextPrimary,
            textAlign = TextAlign.Start
        ),
        cursorBrush = SolidColor(DogCuisineColors.TextPrimary),
        modifier = modifier
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .height(40.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = DogCuisineColors.TextPlaceholder,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun SearchResultItem(
    recipe: RecipeEntity,
    formattedTime: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecipeThumb(
            imagePath = recipe.coverImagePath,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecipeThumb(
    imagePath: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fallbackPainter = painterResource(id = R.drawable.ic_launcher_foreground)
    val model = remember(imagePath) {
        imagePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = fallbackPainter,
        error = fallbackPainter,
        fallback = fallbackPainter,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
private fun EmptySearchState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
