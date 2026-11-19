package com.ark.voidlauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ark.voidlauncher.domain.AppFontWeight
import com.ark.voidlauncher.domain.FontFamily
import com.ark.voidlauncher.domain.TextAlign
import com.ark.voidlauncher.domain.TextTransform

/** Per-app overrides. A null field means "use the launcher default". */
@Entity(tableName = "app_customizations")
data class AppCustomizationEntity(
    @PrimaryKey
    val packageName: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    @ColumnInfo(name = "customNickname")
    val customNickname: String? = null,
    val fontFamily: FontFamily? = null,
    val fontSizeSp: Float? = null,
    val fontWeight: AppFontWeight? = null,
    val letterSpacingSp: Float? = null,
    val textColorHex: String? = null,
    val textAlign: TextAlign? = null,
    val textTransform: TextTransform? = null,
)
