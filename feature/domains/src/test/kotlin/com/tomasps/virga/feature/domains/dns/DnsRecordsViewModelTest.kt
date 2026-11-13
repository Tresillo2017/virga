package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.testing.MainDispatcherRule
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DnsRecordsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val waitMs = 10_000L

    private fun record(id: String, type: String = "A") = buildJsonObject {
        put("id", id)
        put("name", "$id.example.com")
        put("type", type)
        put("content", "192.0.2.1")
        put("ttl", 1)
        put("proxiable", true)
    }

    private fun pageInfo(page: Int, perPage: Int, total: Int) =
        buildJsonObject { put("page", page); put("per_page", perPage); put("count", 1); put("total_count", total) }

    /**
     * Builds the ViewModel against canned routes. When [paged] is true the first page holds one record out of three, so
     * a second page exists; page 2 waits on [pageTwoGate] and a filtered load of AAAA returns a single record.
     * [postGate] holds the POST for a create until it is completed, so a second save can be tried while one is in flight.
     */
    private fun viewModel(
        writes: MutableList<String>,
        deletes: MutableList<String>,
        imports: MutableList<String> = mutableListOf(),
        paged: Boolean = false,
        pageTwoGate: CompletableDeferred<Unit>? = null,
        postGate: CompletableDeferred<Unit>? = null,
    ): DnsRecordsViewModel {
        val api = mockCloudflareApi {
            get("/zones/{zoneId}") {
                DemoReply.ok(buildJsonObject { put("id", "z1"); put("name", "example.com"); put("plan", buildJsonObject { put("name", "Pro Plan") }) })
            }
            get("/zones/{zoneId}/dns_records") { request ->
                when {
                    !paged -> DemoReply.ok(JsonArray(listOf(record("r1"), record("r2"))), pageInfo(1, 100, 2))
                    request.query["type"] == "AAAA" -> DemoReply.ok(JsonArray(listOf(record("x1", "AAAA"))), pageInfo(1, 1, 1))
                    request.query["page"] == "2" -> {
                        pageTwoGate?.await()
                        DemoReply.ok(JsonArray(listOf(record("r2"))), pageInfo(2, 1, 3))
                    }
                    else -> DemoReply.ok(JsonArray(listOf(record("r1"))), pageInfo(1, 1, 3))
                }
            }
            post("/zones/{zoneId}/dns_records") { request ->
                writes += request.bodyText.orEmpty()
                postGate?.await()
                DemoReply.ok(record("r3"))
            }
            post("/zones/{zoneId}/dns_records/import") {
                imports += "import"
                DemoReply.ok(buildJsonObject { put("recs_added", 1) })
            }
            get("/zones/{zoneId}/dns_records/export") { DemoReply.text("; BIND zone\nwww 300 IN A 192.0.2.1\n") }
            delete("/zones/{zoneId}/dns_records/{recordId}") { request ->
                deletes += request.pathParams["recordId"].orEmpty()
                DemoReply.ok(buildJsonObject { put("id", request.pathParams["recordId"].orEmpty()) })
            }
        }
        return DnsRecordsViewModel(zoneId = "z1", zones = ZoneRepository(api), dns = DnsRecordRepository(api))
    }

    private fun recordIds(vm: DnsRecordsViewModel): List<String> =
        (vm.state.value.records as? UiState.Content)?.data?.map { it.id }.orEmpty()

    @Test
    fun `loads the first page and knows the zone is on a paid plan`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())

        // Act
        val state = withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }

        // Assert
        assertEquals(2, (state.records as UiState.Content).data.size)
        assertTrue(state.isPaidPlan)
    }

    @Test
    fun `a record with an invalid IPv4 address is not sent and the form keeps the errors`() = runBlocking {
        // Arrange
        val writes = mutableListOf<String>()
        val vm = viewModel(writes, mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.openCreate()
        vm.updateForm(DnsRecordForm(type = "A", name = "www", values = mapOf("content" to "999.1.1.1")))

        // Act
        vm.save()

        // Assert
        assertTrue(vm.state.value.formErrors.isNotEmpty())
        assertEquals(0, writes.size)
        assertTrue(vm.state.value.editor != null)
    }

    @Test
    fun `a valid new record is posted and the editor closes`() = runBlocking {
        // Arrange
        val writes = mutableListOf<String>()
        val vm = viewModel(writes, mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.openCreate()
        vm.updateForm(DnsRecordForm(type = "A", name = "www", values = mapOf("content" to "192.0.2.9")))

        // Act
        vm.save()
        withTimeout(waitMs) { vm.state.first { it.editor == null } }

        // Assert
        assertEquals(1, writes.size)
        assertEquals(null, vm.state.value.editor)
    }

    @Test
    fun `a second save while the first is in flight does not create the record twice`() = runBlocking {
        // Arrange
        val writes = mutableListOf<String>()
        val postGate = CompletableDeferred<Unit>()
        val vm = viewModel(writes, mutableListOf(), postGate = postGate)
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.openCreate()
        vm.updateForm(DnsRecordForm(type = "A", name = "www", values = mapOf("content" to "192.0.2.9")))
        vm.save()
        assertTrue(vm.state.value.saving)

        // Act
        vm.save()
        postGate.complete(Unit)
        withTimeout(waitMs) { vm.state.first { !it.saving } }

        // Assert
        assertEquals(1, writes.size)
    }

    @Test
    fun `a confirmed single delete removes only that row`() = runBlocking {
        // Arrange
        val deletes = mutableListOf<String>()
        val vm = viewModel(mutableListOf(), deletes)
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }

        // Act
        vm.requestDelete(listOf("r1"))
        vm.confirmDelete()
        val remaining = withTimeout(waitMs) {
            vm.state.first { !it.busy }.let { (it.records as UiState.Content).data.map { row -> row.id } }
        }

        // Assert
        assertEquals(listOf("r1"), deletes)
        assertEquals(listOf("r2"), remaining)
    }

    @Test
    fun `deleting from the open editor closes it and asks before removing only that record`() = runBlocking {
        // Arrange
        val deletes = mutableListOf<String>()
        val vm = viewModel(mutableListOf(), deletes)
        val loaded = withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.openEdit((loaded.records as UiState.Content).data.first { it.id == "r2" })

        // Act
        vm.requestDeleteEditing()
        val pending = vm.state.value
        assertTrue(deletes.isEmpty())
        vm.confirmDelete()
        val remaining = withTimeout(waitMs) {
            vm.state.first { !it.busy }.let { (it.records as UiState.Content).data.map { row -> row.id } }
        }

        // Assert
        assertEquals(null, pending.editor)
        assertEquals(listOf("r2"), pending.pendingDeleteIds)
        assertEquals(listOf("r2"), deletes)
        assertEquals(listOf("r1"), remaining)
    }

    @Test
    fun `delete from the editor does nothing when a new record is being created`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.openCreate()

        // Act
        vm.requestDeleteEditing()

        // Assert
        assertEquals(null, vm.state.value.pendingDeleteIds)
        assertTrue(vm.state.value.editor != null)
    }

    @Test
    fun `a page that arrives after a filter change never mixes into the new filter`() = runBlocking {
        // Arrange: page 2 of the unfiltered list is still in flight when the owner picks AAAA.
        val pageTwoGate = CompletableDeferred<Unit>()
        val vm = viewModel(mutableListOf(), mutableListOf(), paged = true, pageTwoGate = pageTwoGate)
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content && it.hasMore } }
        vm.loadMore()
        assertTrue(vm.state.value.loadingMore)

        // Act
        vm.setTypeFilter("AAAA")
        val state = withTimeout(waitMs) { vm.state.first { !it.loadingMore && it.records is UiState.Content } }
        pageTwoGate.complete(Unit)

        // Assert
        assertEquals(listOf("x1"), recordIds(vm))
        assertFalse(state.hasMore)
        assertFalse(state.loadingMore)
    }

    @Test
    fun `load more is ignored when the filtered list has no further page`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf(), paged = true)
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content && it.hasMore } }
        vm.setTypeFilter("AAAA")
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content && !it.loadingMore } }

        // Act
        vm.loadMore()

        // Assert: the AAAA list has no second page, so nothing is appended and load more stays off.
        assertEquals(listOf("x1"), recordIds(vm))
        assertFalse(vm.state.value.hasMore)
    }

    @Test
    fun `a file over the size limit is refused and nothing is uploaded`() = runBlocking {
        // Arrange
        val imports = mutableListOf<String>()
        val vm = viewModel(mutableListOf(), mutableListOf(), imports = imports)
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }

        // Act
        vm.importBind("zone.bind", BindFileRead.TooLarge)

        // Assert
        assertNotNull(vm.state.value.message)
        assertEquals(0, imports.size)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `a readable file is uploaded and the list reloads`() = runBlocking {
        // Arrange
        val imports = mutableListOf<String>()
        val vm = viewModel(mutableListOf(), mutableListOf(), imports = imports)
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }

        // Act
        vm.importBind("zone.bind", BindFileRead.Content("example.com. 300 IN A 192.0.2.1".toByteArray()))
        withTimeout(waitMs) { vm.state.first { !it.busy && it.message != null && it.records is UiState.Content } }

        // Assert
        assertEquals(listOf("import"), imports)
        assertEquals(listOf("r1", "r2"), recordIds(vm))
    }

    @Test
    fun `an import reports how many records the zone file added`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }

        // Act
        vm.importBind("zone.bind", BindFileRead.Content("www 300 IN A 192.0.2.1".toByteArray()))
        withTimeout(waitMs) { vm.state.first { !it.busy && it.message != null } }

        // Assert
        val message = vm.state.value.message as UiText.Resource
        assertEquals(R.string.domains_message_imported_count, message.id)
        assertEquals(listOf(1), message.args)
    }

    @Test
    fun `exporting holds the BIND text until the owner picks a file, then reports whether it was saved`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }

        // Act
        vm.requestExport()
        withTimeout(waitMs) { vm.state.first { it.exportText != null } }
        val pending = vm.state.value.exportText
        vm.exportSaved(saved = false)

        // Assert
        assertEquals("; BIND zone\nwww 300 IN A 192.0.2.1\n", pending)
        assertEquals(null, vm.state.value.exportText)
        assertEquals(R.string.domains_message_export_not_saved, (vm.state.value.message as UiText.Resource).id)
    }

    @Test
    fun `a saved record of another type clears the type filter so the new row shows`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.setTypeFilter("CAA")
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content && it.typeFilter == "CAA" } }
        vm.openCreate()
        vm.updateForm(DnsRecordForm(type = "A", name = "api", values = mapOf("content" to "192.0.2.1")))

        // Act
        vm.save()
        withTimeout(waitMs) { vm.state.first { !it.saving && it.editor == null } }

        // Assert
        assertEquals(null, vm.state.value.typeFilter)
    }

    @Test
    fun `a saved record that the search would hide clears the search`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.setQuery("no-such-name")
        vm.openCreate()
        vm.updateForm(DnsRecordForm(type = "A", name = "api", values = mapOf("content" to "192.0.2.1")))

        // Act
        vm.save()
        withTimeout(waitMs) { vm.state.first { !it.saving && it.editor == null } }

        // Assert
        assertEquals("", vm.state.value.query)
    }

    @Test
    fun `a saved record of the filtered type keeps the filter and the search`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.setTypeFilter("A")
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content && it.typeFilter == "A" } }
        vm.openCreate()
        vm.updateForm(DnsRecordForm(type = "A", name = "api", values = mapOf("content" to "192.0.2.1")))

        // Act
        vm.save()
        withTimeout(waitMs) { vm.state.first { !it.saving && it.editor == null } }

        // Assert
        assertEquals("A", vm.state.value.typeFilter)
    }

    @Test
    fun `an import clears the type filter because it can add records of any type`() = runBlocking {
        // Arrange
        val vm = viewModel(mutableListOf(), mutableListOf())
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content } }
        vm.setTypeFilter("CAA")
        withTimeout(waitMs) { vm.state.first { it.records is UiState.Content && it.typeFilter == "CAA" } }

        // Act
        vm.importBind("zone.bind", BindFileRead.Content("www 300 IN A 192.0.2.1".toByteArray()))
        withTimeout(waitMs) { vm.state.first { !it.busy && it.message != null } }

        // Assert
        assertEquals(null, vm.state.value.typeFilter)
    }
}
