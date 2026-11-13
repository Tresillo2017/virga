package com.tomasps.virga.feature.account.details

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.cloudflarePath
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One account as GET /accounts/{id} returns it. Only the fields Virga shows; everything else is ignored. */
@Serializable
data class AccountDetails(
    val id: String,
    val name: String = "",
    val type: String? = null,
    @SerialName("created_on") val createdOn: String? = null,
)

/** Account-level reads. Repositories take [CloudflareApi] and return [ApiResult]; they never touch the UI. */
class AccountDetailsRepository(private val api: CloudflareApi) {

    suspend fun details(accountId: String): ApiResult<AccountDetails> =
        api.get(cloudflarePath("accounts", accountId))
}
