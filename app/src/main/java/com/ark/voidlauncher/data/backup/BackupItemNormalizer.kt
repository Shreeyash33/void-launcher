package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.domain.BuiltInWidgets

object BackupItemNormalizer {
    fun normalize(item: BackedUpItem): BackedUpItem {
        val kind = item.widgetKind ?: BuiltInWidgets.fromReference(item.referenceId)
        return item.copy(
            referenceId = if (item.widgetKind == null && kind != null) {
                BuiltInWidgets.reference(kind)
            } else {
                item.referenceId
            },
            widgetKind = kind,
            appListPackages = BuiltInWidgets.encodePackages(
                BuiltInWidgets.decodePackages(item.appListPackages),
            ),
        )
    }
}
