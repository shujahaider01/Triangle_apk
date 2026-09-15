package com.triangle.app.rewards

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens ported 1:1 from style.css's `.rw-*`/`.rws-*` classes (the
 * Rewards page's own dark/light palette, see style.css:10311-10320,10480-
 * 10488,10623-10629) — same "hardcoded tokens per screen" pattern
 * DashboardScreen.kt/ProfileTheme.kt already use.
 */
object RewardsColors {
    // .rw-balance-hero (Wallet tab) — dark navy gradient, same in both themes.
    val BalanceHeroGradient = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
    // .rws-hero (Store tab) — purple gradient, same in both themes.
    val StoreHeroGradient = listOf(Color(0xFF4834D4), Color(0xFF6C5CE7), Color(0xFFA29BFE))

    val Afford = Color(0xFFF59E0B)
    val CantAfford = Color(0xFFEF4444)

    val SurfaceDark = Color(0xFF1E2738)
    val BorderDark = Color(0xFF2A3242)
    val TextDark = Color(0xFFFFFFFF)
    val Text2Dark = Color(0xFFA1A1AA)
    val Text3Dark = Color(0xFF6B7280)

    val SurfaceLight = Color(0xFFF3F4F6)
    val BorderLight = Color(0xFFE5E7EB)
    val TextLight = Color(0xFF111827)
    val Text2Light = Color(0xFF6B7280)
    val Text3Light = Color(0xFF9CA3AF)
}

data class RewardsPalette(
    val surface2: Color,
    val border: Color,
    val text: Color,
    val text2: Color,
    val text3: Color
)

@Composable
@ReadOnlyComposable
fun rewardsPalette(): RewardsPalette {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        RewardsPalette(RewardsColors.SurfaceDark, RewardsColors.BorderDark, RewardsColors.TextDark, RewardsColors.Text2Dark, RewardsColors.Text3Dark)
    } else {
        RewardsPalette(RewardsColors.SurfaceLight, RewardsColors.BorderLight, RewardsColors.TextLight, RewardsColors.Text2Light, RewardsColors.Text3Light)
    }
}

/** Ported verbatim from script.js's CATS map (script.js:13698-13707) — stands in for a reward photo (no image upload this cut). */
data class RewardCategory(val label: String, val emoji: String, val color: Color, val bg: Color)

val RewardCategories: Map<String, RewardCategory> = mapOf(
    "digital" to RewardCategory("Digital", "💻", Color(0xFF6C5CE7), Color(0x1F6C5CE7)),
    "learning" to RewardCategory("Learning", "🎓", Color(0xFF00B894), Color(0x1F00B894)),
    "merch" to RewardCategory("Merch", "👕", Color(0xFFE17055), Color(0x1FE17055)),
    "office" to RewardCategory("Office", "💼", Color(0xFFFDCB6E), Color(0x26FDCB6E)),
    "physical" to RewardCategory("Physical", "📦", Color(0xFF3B82F6), Color(0x1F3B82F6)),
    "giftcard" to RewardCategory("Gift Card", "🎁", Color(0xFF22C55E), Color(0x1F22C55E)),
    "perk" to RewardCategory("Perk", "⚡", Color(0xFFF59E0B), Color(0x1FF59E0B)),
    "experience" to RewardCategory("Experience", "🌟", Color(0xFFE85D26), Color(0x1FE85D26))
)
val DefaultRewardCategory = RewardCategories.getValue("physical")
fun rewardCategory(key: String): RewardCategory = RewardCategories[key] ?: DefaultRewardCategory

/** Ported verbatim from script.js's RDST map (script.js:13928). */
data class RedemptionStatusInfo(val label: String, val color: Color, val bg: Color)

val RedemptionStatuses: Map<String, RedemptionStatusInfo> = mapOf(
    "pending" to RedemptionStatusInfo("Pending", Color(0xFFF59E0B), Color(0x1AF59E0B)),
    "approved" to RedemptionStatusInfo("Approved", Color(0xFF22C55E), Color(0x1A22C55E)),
    "rejected" to RedemptionStatusInfo("Rejected", Color(0xFFEF4444), Color(0x1AEF4444)),
    "processing" to RedemptionStatusInfo("Processing", Color(0xFF3B82F6), Color(0x1A3B82F6)),
    "delivered" to RedemptionStatusInfo("Delivered", Color(0xFF8B5CF6), Color(0x1A8B5CF6)),
    "completed" to RedemptionStatusInfo("Completed", Color(0xFF22C55E), Color(0x1A22C55E))
)
fun redemptionStatus(key: String): RedemptionStatusInfo = RedemptionStatuses[key] ?: RedemptionStatuses.getValue("pending")
