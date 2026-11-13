package com.tomasps.virga.feature.r2.objects

import com.tomasps.virga.feature.r2.transfer.R2Transfer
import com.tomasps.virga.feature.r2.transfer.R2TransferDirection
import com.tomasps.virga.feature.r2.transfer.R2TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedUploadsTest {

    private fun transfer(
        id: String,
        direction: R2TransferDirection = R2TransferDirection.UPLOAD,
        status: R2TransferStatus = R2TransferStatus.COMPLETED,
    ) = R2Transfer(
        id = id,
        accountId = "account",
        direction = direction,
        bucketName = "demo-assets",
        objectKey = "$id.txt",
        totalBytes = 27L,
        status = status,
    )

    @Test
    fun `a completed upload that has not been reported is returned`() {
        // Arrange
        val transfers = listOf(transfer("up-1"))

        // Act
        val finished = completedUploadIds(transfers, reported = emptySet())

        // Assert
        assertEquals(setOf("up-1"), finished)
    }

    @Test
    fun `an upload that was already reported is not returned again`() {
        // Arrange
        val transfers = listOf(transfer("up-1"))

        // Act
        val finished = completedUploadIds(transfers, reported = setOf("up-1"))

        // Assert
        assertEquals(emptySet<String>(), finished)
    }

    @Test
    fun `running uploads, failed uploads and downloads are not returned`() {
        // Arrange
        val transfers = listOf(
            transfer("running", status = R2TransferStatus.RUNNING),
            transfer("failed", status = R2TransferStatus.FAILED),
            transfer("download", direction = R2TransferDirection.DOWNLOAD),
        )

        // Act
        val finished = completedUploadIds(transfers, reported = emptySet())

        // Assert
        assertEquals(emptySet<String>(), finished)
    }
}
