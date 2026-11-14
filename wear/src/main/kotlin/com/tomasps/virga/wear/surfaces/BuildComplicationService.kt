package com.tomasps.virga.wear.surfaces

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.tomasps.virga.wear.MainActivity
import com.tomasps.virga.wear.present.BuildVisualState
import com.tomasps.virga.wear.present.ComplicationModel
import org.koin.android.ext.android.inject
import java.time.Instant

private val PREVIEW_MODEL = ComplicationModel(shortText = "OK", title = "checkout", state = BuildVisualState.PASSED)

/**
 * A watch-face complication for the newest build. Short text carries the state word with the Worker name as title;
 * long text shows the Worker name (with its age when stale) and the state word. Tapping opens the build list.
 */
class BuildComplicationService : SuspendingComplicationDataSourceService() {

    private val refresher: BuildSurfaceRefresher by inject()

    /** Sample data for the picker; never touches the network or the cache. */
    override fun getPreviewData(type: ComplicationType): ComplicationData? = render(type, PREVIEW_MODEL)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        // Never blank on the first request: a failed refresh renders as "No data".
        val data = refresher.refreshOrNull() ?: SurfaceData(null, null, SurfaceStatus.Stale)
        return render(request.complicationType, complicationForSurface(data, Instant.now()))
    }

    private fun render(type: ComplicationType, model: ComplicationModel): ComplicationData? {
        val description = PlainComplicationText.Builder("${model.shortText} ${model.title}").build()
        val tap = openListIntent()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(model.shortText).build(),
                contentDescription = description,
            ).setTitle(PlainComplicationText.Builder(model.title).build()).setTapAction(tap).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(model.title).build(),
                contentDescription = description,
            ).setTitle(PlainComplicationText.Builder(model.shortText).build()).setTapAction(tap).build()
            else -> null
        }
    }

    private fun openListIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
