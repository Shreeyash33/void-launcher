package com.ark.voidlauncher.data.background

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ark.voidlauncher.domain.background.BackgroundKind
import com.ark.voidlauncher.domain.background.BackgroundScope
import com.ark.voidlauncher.domain.background.PageBackground
import com.ark.voidlauncher.domain.background.decodeGradientStops
import com.ark.voidlauncher.domain.background.encodeGradientStops

/**
 * Room row for a background. Enums are stored as their [name] so the migration
 * does not depend on type converters. Expected column types (for MIGRATION_2_3):
 * id INTEGER PRIMARY KEY AUTOINCREMENT, scope TEXT NOT NULL, pageId INTEGER,
 * kind TEXT NOT NULL, colorHex TEXT, gradientSpec TEXT, gradientAngle REAL NOT NULL,
 * imageToken TEXT.
 */
@Entity(tableName = "backgrounds")
data class BackgroundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scope: String,
    val pageId: Long?,
    val kind: String,
    val colorHex: String?,
    val gradientSpec: String?,
    val gradientAngle: Float,
    val imageToken: String?,
)

/** Corrupt enum names fall back to safe defaults instead of throwing on read. */
fun BackgroundEntity.toDomain(): PageBackground = PageBackground(
    id = id,
    scope = runCatching { BackgroundScope.valueOf(scope) }
        .getOrDefault(BackgroundScope.GLOBAL),
    pageId = pageId,
    kind = runCatching { BackgroundKind.valueOf(kind) }
        .getOrDefault(BackgroundKind.COLOR),
    colorHex = colorHex,
    gradientStops = decodeGradientStops(gradientSpec),
    gradientAngle = gradientAngle,
    imageToken = imageToken,
)

/** GLOBAL rows never keep a pageId, even if a caller supplied one defensively. */
fun PageBackground.toEntity(): BackgroundEntity = BackgroundEntity(
    id = id,
    scope = scope.name,
    pageId = if (scope == BackgroundScope.PAGE) pageId else null,
    kind = kind.name,
    colorHex = colorHex,
    gradientSpec = gradientStops.takeIf { it.isNotEmpty() }?.let(::encodeGradientStops),
    gradientAngle = gradientAngle,
    imageToken = imageToken,
)
