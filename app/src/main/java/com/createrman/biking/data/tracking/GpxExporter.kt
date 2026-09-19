package com.createrman.biking.data.tracking

import android.content.Context
import androidx.core.content.FileProvider
import com.createrman.biking.domain.model.RideDetails
import java.io.File
import java.time.Instant

class GpxExporter(private val context: Context) {
    fun export(details: RideDetails): File {
        val dir = File(context.cacheDir, "gpx").also { it.mkdirs() }
        val safeName = details.summary.name.ifBlank { "ride-${details.summary.id}" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "$safeName.gpx")
        file.writeText(toGpx(details))
        return file
    }

    fun uriFor(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun toGpx(details: RideDetails): String {
        val points = details.points.joinToString(separator = "\n") { point ->
            val elevation = point.elevationMeters?.let { "<ele>${"%.1f".format(java.util.Locale.US, it)}</ele>" }.orEmpty()
            """
            |      <trkpt lat="${point.latitude}" lon="${point.longitude}">
            |        $elevation
            |        <time>${Instant.ofEpochMilli(point.timestampMillis)}</time>
            |        <extensions><speed>${point.speedMetersPerSecond}</speed></extensions>
            |      </trkpt>
            """.trimMargin()
        }
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<gpx version="1.1" creator="Biking" xmlns="http://www.topografix.com/GPX/1/1">
            |  <metadata>
            |    <name>${details.summary.name.escapeXml()}</name>
            |    <time>${Instant.ofEpochMilli(details.summary.startedAtMillis)}</time>
            |  </metadata>
            |  <trk>
            |    <name>${details.summary.name.escapeXml()}</name>
            |    <desc>${details.summary.note.escapeXml()}</desc>
            |    <trkseg>
            |$points
            |    </trkseg>
            |  </trk>
            |</gpx>
        """.trimMargin()
    }

    private fun String.escapeXml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
