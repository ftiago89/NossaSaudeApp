package com.example.nossasaudeapp.ui.theme

import androidx.compose.ui.graphics.Color

object NsColors {
    // Backgrounds
    val Background = Color(0xFFFAF7F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceWarm = Color(0xFFFFF8F2)

    // Primary (coral)
    val Primary = Color(0xFFE8634A)
    val PrimarySoft = Color(0xFFFCEAE6)
    val PrimaryDark = Color(0xFFC94E38)

    // Accent (teal)
    val AccentTeal = Color(0xFF2BA89C)
    val AccentTealSoft = Color(0xFFE6F6F4)

    // Text
    val TextPrimary = Color(0xFF2D2A26)
    val TextSecondary = Color(0xFF7A756E)
    val TextTertiary = Color(0xFFADA69E)

    // Semantic
    val Border = Color(0xFFEDE8E3)
    val AllergyBg = Color(0xFFFDE8E4)
    val AllergyText = Color(0xFFC94E38)
    val ConditionBg = Color(0xFFE6F0FF)
    val ConditionText = Color(0xFF3B6EB5)
    val BloodTypeBg = Color(0xFFFFF0F0)
    val BloodTypeText = Color(0xFFD64545)
    val SyncPending = Color(0xFFF5A623)
    val Danger = Color(0xFFD64545)
    val DangerSoft = Color(0xFFFDE8E8)

    // Avatar palette pairs (background -> foreground)
    val AvatarCoralBg = PrimarySoft
    val AvatarCoralFg = Primary
    val AvatarTealBg = AccentTealSoft
    val AvatarTealFg = AccentTeal
    val AvatarBlueBg = Color(0xFFE6F0FF)
    val AvatarBlueFg = Color(0xFF3B6EB5)
    val AvatarAmberBg = Color(0xFFFFF3E0)
    val AvatarAmberFg = Color(0xFFE8964A)
}

data class AvatarPalette(val background: Color, val foreground: Color)

val AvatarPalettes: List<AvatarPalette> = listOf(
    AvatarPalette(NsColors.AvatarCoralBg, NsColors.AvatarCoralFg),
    AvatarPalette(NsColors.AvatarTealBg, NsColors.AvatarTealFg),
    AvatarPalette(NsColors.AvatarBlueBg, NsColors.AvatarBlueFg),
    AvatarPalette(NsColors.AvatarAmberBg, NsColors.AvatarAmberFg),
)

fun avatarPaletteFor(index: Int): AvatarPalette =
    AvatarPalettes[((index % AvatarPalettes.size) + AvatarPalettes.size) % AvatarPalettes.size]

// Legacy aliases kept so unmodified Material templates still compile
val Purple80 = NsColors.Primary
val PurpleGrey80 = NsColors.AccentTeal
val Pink80 = NsColors.PrimarySoft
val Purple40 = NsColors.Primary
val PurpleGrey40 = NsColors.AccentTeal
val Pink40 = NsColors.PrimarySoft
