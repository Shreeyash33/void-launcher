package com.ark.voidlauncher.system.widgets

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Process-wide notifications emitted by [WidgetDeleteReceiver]. */
object WidgetEvents {
    /**
     * Ids of widget instances deleted by the system or another app.
     *
     * Uses a small buffer with [BufferOverflow.DROP_OLDEST] so the receiver
     * never blocks when no collector is currently registered.
     */
    val deletedIds: MutableSharedFlow<Int> = MutableSharedFlow(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}
