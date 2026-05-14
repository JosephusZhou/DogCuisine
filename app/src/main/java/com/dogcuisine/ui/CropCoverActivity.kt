package com.dogcuisine.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dogcuisine.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.Color as ComposeColor

class CropCoverActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_CROPPED_URI = "cropped_uri"
        private const val OUTPUT_SIZE = 1080

        fun createIntent(context: Context, sourceUri: String): Intent {
            return Intent(context, CropCoverActivity::class.java)
                .putExtra(EXTRA_SOURCE_URI, sourceUri)
        }
    }

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private var minScale = 1f
    private var maxScale = 5f
    private var currentScale = 1f
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private val cropRect = RectF()

    private var cropRectUi by mutableStateOf<Rect?>(null)
    private var imageView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComposeSystemBarDelegate.install(this)

        val raw = intent.getStringExtra(EXTRA_SOURCE_URI)
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.crop_invalid_image_toast), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        bitmap = loadBitmap(Uri.parse(raw))
        if (bitmap == null) {
            Toast.makeText(this, getString(R.string.crop_load_failed_toast), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            DogCuisineTheme {
                CropCoverScreen(
                    cropRect = cropRectUi,
                    onBack = { finish() },
                    onDone = { finishCrop() },
                    onViewportSizeChanged = { width, height ->
                        updateViewport(width, height)
                    },
                    onTransform = { centroid, pan, zoom ->
                        handleTransform(centroid, pan, zoom)
                    },
                    imageContent = {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                ImageView(context).apply {
                                    scaleType = ImageView.ScaleType.MATRIX
                                    setImageBitmap(bitmap)
                                    imageView = this
                                    applyMatrix()
                                }
                            },
                            update = { view ->
                                if (view.drawable == null) {
                                    view.setImageBitmap(bitmap)
                                }
                                imageView = view
                                applyMatrix()
                            }
                        )
                    }
                )
            }
        }
    }

    private fun updateViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewportWidth = width.toFloat()
        viewportHeight = height.toFloat()
        val margin = dpToPx(24f)
        val side = min(viewportWidth - margin * 2f, viewportHeight - margin * 2f).coerceAtLeast(0f)
        val left = (viewportWidth - side) * 0.5f
        val top = (viewportHeight - side) * 0.5f
        cropRect.set(left, top, left + side, top + side)
        cropRectUi = Rect(left, top, left + side, top + side)
        initMatrix()
    }

    private fun initMatrix() {
        val bmp = bitmap ?: return
        if (viewportWidth <= 0f || viewportHeight <= 0f || cropRect.width() <= 0f) return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        matrix.reset()
        minScale = max(cropRect.height() / bh, cropRect.width() / bw)
        maxScale = minScale * 8f
        currentScale = minScale
        val dx = (viewportWidth - bw * minScale) * 0.5f
        val dy = (viewportHeight - bh * minScale) * 0.5f
        matrix.postScale(minScale, minScale)
        matrix.postTranslate(dx, dy)
        fixBounds()
        applyMatrix()
    }

    private fun handleTransform(centroid: Offset, pan: Offset, zoom: Float) {
        if (bitmap == null || cropRect.width() <= 0f || cropRect.height() <= 0f) return
        val target = currentScale * zoom
        val clamped = target.coerceIn(minScale, maxScale)
        val factor = if (currentScale == 0f) 1f else clamped / currentScale
        matrix.postScale(factor, factor, centroid.x, centroid.y)
        currentScale = clamped
        matrix.postTranslate(pan.x, pan.y)
        fixBounds()
        applyMatrix()
    }

    private fun applyMatrix() {
        imageView?.imageMatrix = matrix
    }

    private fun fixBounds() {
        val bmp = bitmap ?: return
        if (cropRect.width() <= 0f || cropRect.height() <= 0f) return
        val rect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        matrix.mapRect(rect)
        var dx = 0f
        var dy = 0f
        if (rect.left > cropRect.left) dx = cropRect.left - rect.left
        if (rect.right < cropRect.right) dx = cropRect.right - rect.right
        if (rect.top > cropRect.top) dy = cropRect.top - rect.top
        if (rect.bottom < cropRect.bottom) dy = cropRect.bottom - rect.bottom
        matrix.postTranslate(dx, dy)
    }

    private fun finishCrop() {
        val bmp = bitmap
        if (bmp == null || cropRect.width() <= 0f || cropRect.height() <= 0f) {
            finish()
            return
        }
        try {
            val out = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.BLACK)
            val outMatrix = Matrix(matrix)
            outMatrix.postTranslate(-cropRect.left, -cropRect.top)
            val factor = OUTPUT_SIZE / cropRect.width()
            outMatrix.postScale(factor, factor)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bmp, outMatrix, paint)

            val dir = File(cacheDir, "crop")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "cover_crop_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { fos ->
                out.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.flush()
            }
            out.recycle()

            val result = Intent().putExtra(EXTRA_CROPPED_URI, Uri.fromFile(file).toString())
            setResult(RESULT_OK, result)
            finish()
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.crop_failed_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                BitmapFactory.decodeStream(input, null, bounds)
            }
            var inSample = 1
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            while (maxSide / inSample > 2048) {
                inSample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = max(1, inSample)
            }
            val src = contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null
            rotateByExifIfNeeded(src, uri)
        } catch (_: Exception) {
            null
        }
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

        val rotate = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate.postRotate(270f)
            else -> return source
        }
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, rotate, true)
        if (out !== source) source.recycle()
        return out
    }

    private fun dpToPx(valueDp: Float): Float {
        return valueDp * resources.displayMetrics.density
    }

    override fun onDestroy() {
        if (bitmap != null && !bitmap!!.isRecycled) {
            bitmap!!.recycle()
            bitmap = null
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CropCoverScreen(
    cropRect: Rect?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onViewportSizeChanged: (Int, Int) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    imageContent: @Composable () -> Unit
) {
    Scaffold(
        containerColor = DogCuisineColors.CropSurface,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.crop_cover_title), fontWeight = FontWeight.SemiBold) },
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
                    TextButton(onClick = onDone) {
                        Text(
                            text = stringResource(R.string.common_done),
                            color = MaterialTheme.colorScheme.onPrimary,
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DogCuisineColors.CropSurface)
                .onSizeChanged { size ->
                    onViewportSizeChanged(size.width, size.height)
                }
                .detectCropGestures(onTransform)
        ) {
            imageContent()
            cropRect?.let {
                CropMaskOverlay(cropRect = it)
            }
        }
    }
}

private fun Modifier.detectCropGestures(
    onTransform: (Offset, Offset, Float) -> Unit
): Modifier {
    return pointerInput(Unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            onTransform(centroid, pan, zoom)
        }
    }
}

@Composable
private fun CropMaskOverlay(cropRect: Rect) {
    val mask = DogCuisineColors.CropMask
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            color = mask,
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width, cropRect.top)
        )
        drawRect(
            color = mask,
            topLeft = Offset(0f, cropRect.bottom),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - cropRect.bottom)
        )
        drawRect(
            color = mask,
            topLeft = Offset(0f, cropRect.top),
            size = androidx.compose.ui.geometry.Size(cropRect.left, cropRect.height)
        )
        drawRect(
            color = mask,
            topLeft = Offset(cropRect.right, cropRect.top),
            size = androidx.compose.ui.geometry.Size(size.width - cropRect.right, cropRect.height)
        )
        drawRect(
            color = ComposeColor.White,
            topLeft = Offset(cropRect.left, cropRect.top),
            size = androidx.compose.ui.geometry.Size(cropRect.width, cropRect.height),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
