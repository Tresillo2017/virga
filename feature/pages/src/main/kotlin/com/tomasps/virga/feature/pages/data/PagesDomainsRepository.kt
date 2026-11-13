package com.tomasps.virga.feature.pages.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import io.ktor.http.HttpMethod

/** Custom domain calls for one project. Validation records come back on each domain. */
class PagesDomainsRepository(private val api: CloudflareApi) {

    suspend fun domains(accountId: String, projectName: String): ApiResult<List<PagesDomain>> =
        api.get(domainsPath(accountId, projectName))

    suspend fun add(accountId: String, projectName: String, hostname: String): ApiResult<PagesDomain> =
        api.post(domainsPath(accountId, projectName), PagesDomainRequest(hostname))

    /** PATCH with no body asks the server to check the validation record again. */
    suspend fun retryValidation(accountId: String, projectName: String, hostname: String): ApiResult<PagesDomain> =
        api.execute(
            method = HttpMethod.Patch,
            path = domainPath(accountId, projectName, hostname),
            query = emptyMap(),
            body = null,
            deserializer = PagesDomain.serializer(),
        )

    suspend fun remove(accountId: String, projectName: String, hostname: String): ApiResult<Unit> =
        api.delete(domainPath(accountId, projectName, hostname))
}
