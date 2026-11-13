package com.tomasps.virga.feature.d1.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.feature.d1.list.RecentlyDeletedLog
import com.tomasps.virga.feature.d1.sql.QueryHistoryStore
import com.tomasps.virga.feature.d1.testing.InMemoryRecentlyDeletedLog
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Files

/**
 * Per-test state for the flows. The app keeps the console history and the recently deleted list in one process-wide
 * DataStore file, so without this a flow sees what an earlier test left behind. Both are replaced here: the recently
 * deleted list in memory, and the console history in a fresh temporary file for each graph. Loaded after d1KoinModule,
 * so these definitions win.
 */
fun isolatedD1Modules(): Module = module {
    single<RecentlyDeletedLog> { InMemoryRecentlyDeletedLog() }
    single {
        val file = Files.createTempFile("d1-flow-history", ".preferences_pb").toFile().apply { deleteOnExit() }
        file.delete()
        QueryHistoryStore(PreferenceDataStoreFactory.create(produceFile = { file }))
    }
}
