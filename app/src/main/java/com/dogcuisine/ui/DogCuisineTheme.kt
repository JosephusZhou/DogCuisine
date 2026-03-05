package com.dogcuisine.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.dogcuisine.R

@Composable
fun DogCuisineTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = colorResource(id = R.color.dog_primary),
        onPrimary = colorResource(id = R.color.dog_on_primary),
        primaryContainer = colorResource(id = R.color.dog_primary_container),
        onPrimaryContainer = colorResource(id = R.color.dog_on_primary_container),
        secondary = colorResource(id = R.color.dog_secondary),
        onSecondary = colorResource(id = R.color.dog_on_secondary),
        secondaryContainer = colorResource(id = R.color.dog_secondary_container),
        onSecondaryContainer = colorResource(id = R.color.dog_on_secondary_container),
        surface = colorResource(id = R.color.dog_surface),
        surfaceVariant = colorResource(id = R.color.dog_surface_variant),
        onSurface = colorResource(id = R.color.dog_on_surface),
        onSurfaceVariant = colorResource(id = R.color.dog_on_surface_variant),
        outline = colorResource(id = R.color.dog_outline)
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

object DogCuisineColors {
    val Transparent = Color.Transparent

    val GradientTop: Color
        @Composable get() = colorResource(id = R.color.dog_gradient_top)

    val GradientBottom: Color
        @Composable get() = colorResource(id = R.color.dog_gradient_bottom)

    val TextPrimary: Color
        @Composable get() = colorResource(id = R.color.dog_text_primary)

    val TextPlaceholder: Color
        @Composable get() = colorResource(id = R.color.dog_text_placeholder)

    val TextMuted: Color
        @Composable get() = colorResource(id = R.color.dog_text_muted)

    val Scrim: Color
        @Composable get() = colorResource(id = R.color.dog_scrim)

    val CropSurface: Color
        @Composable get() = colorResource(id = R.color.dog_crop_surface)

    val CropMask: Color
        @Composable get() = colorResource(id = R.color.dog_crop_mask)
}

@Composable
fun dogCuisineBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(DogCuisineColors.GradientTop, DogCuisineColors.GradientBottom)
    )
}
