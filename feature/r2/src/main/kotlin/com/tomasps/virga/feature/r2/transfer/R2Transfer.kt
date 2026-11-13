package com.tomasps.virga.feature.r2.transfer

import com.tomasps.virga.core.ui.state.ErrorUi

enum class R2TransferDirection { DOWNLOAD, UPLOAD }

enum class R2TransferStatus { RUNNING, COMPLETED, CANCELLED, FAILED }

/** One download or upload as the transfers panel shows it. Immutable; the coordinator swaps in a copy on each change. */
data class R2Transfer(
    val id: String,
    val accountId: String,
    val direction: R2TransferDirection,
    val bucketName: String,
    val objectKey: String,
    val totalBytes: Long?,
    val doneBytes: Long = 0L,
    val status: R2TransferStatus = R2TransferStatus.RUNNING,
    val failure: ErrorUi? = null,
)
