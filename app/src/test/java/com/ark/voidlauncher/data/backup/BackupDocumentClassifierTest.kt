package com.ark.voidlauncher.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDocumentClassifierTest {

    @Test
    fun classifiesLayoutDocument() {
        assertEquals(
            BackupDocumentKind.LAYOUT,
            BackupDocumentClassifier.classify("""{"version":1,"pages":[],"edges":[],"items":[]}"""),
        )
    }

    @Test
    fun classifiesWidgetBundleDocument() {
        assertEquals(
            BackupDocumentKind.WIDGET_BUNDLE,
            BackupDocumentClassifier.classify("""{"version":1,"widgets":[]}"""),
        )
    }

    @Test
    fun classifiesInventoryDocument() {
        assertEquals(
            BackupDocumentKind.DEVICE_INVENTORY,
            BackupDocumentClassifier.classify("""{"version":1,"apps":[],"systemWidgets":[]}"""),
        )
    }

    @Test
    fun malformedOrForeignDocumentsAreUnknown() {
        assertEquals(BackupDocumentKind.UNKNOWN, BackupDocumentClassifier.classify("not json"))
        assertEquals(BackupDocumentKind.UNKNOWN, BackupDocumentClassifier.classify("""{"hello":1}"""))
        assertEquals(
            BackupDocumentKind.UNKNOWN,
            BackupDocumentClassifier.classify("""{"widgets":[],"apps":[]}"""),
        )
    }
}
