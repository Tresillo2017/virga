package com.tomasps.virga.feature.r2.objects

import com.tomasps.virga.feature.r2.transfer.R2Transfer
import com.tomasps.virga.feature.r2.transfer.R2TransferDirection
import com.tomasps.virga.feature.r2.transfer.R2TransferStatus

/**
 * Ids of uploads that have completed and are not yet in [reported]. The listing reloads for these, so a finished
 * upload appears without leaving and reopening the folder.
 */
internal fun completedUploadIds(transfers: List<R2Transfer>, reported: Set<String>): Set<String> =
    transfers
        .filter { it.direction == R2TransferDirection.UPLOAD && it.status == R2TransferStatus.COMPLETED }
        .map { it.id }
        .filterNot { it in reported }
        .toSet()
