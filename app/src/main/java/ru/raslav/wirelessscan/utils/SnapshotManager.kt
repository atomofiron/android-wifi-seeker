package ru.raslav.wirelessscan.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.newGenericWriter
import nl.adaptivity.xmlutil.serialization.OutputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.decodeFromStream
import nl.adaptivity.xmlutil.xmlStreaming
import ru.raslav.wirelessscan.BuildConfig
import ru.raslav.wirelessscan.Const.MIME_TYPE_XML
import ru.raslav.wirelessscan.Const.SNAPSHOT_FORMAT
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.elog
import ru.raslav.wirelessscan.tryStartActivity
import ru.raslav.wirelessscan.utils.OuiManager.Companion.oui
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnapshotManager(private val co: Context) {

    private val xml = XML.v1 {
        policy {
            defaultPrimitiveOutputKind = OutputKind.Element
            ignoreUnknownChildren()
        }
    }

    /** @return snapshot file name*/
    fun put(
        points: List<Point>,
        name: String? = defaultName(),
    ): Rslt<String> {
        val name = when (name) {
            null -> "tmp/${defaultName()}"
            else -> name
        }
        val file = File(co.filesDir, name)
        val parent = file.parentFile
        if (parent?.isFile == true) {
            parent.delete()
        }

        if (parent == null || !parent.exists() && !parent.mkdirs()) {
            return Rslt.Err()
        }
        try {
            file.outputStream().bufferedWriter(Charsets.UTF_8).use { output ->
                xmlStreaming.newGenericWriter(output as Appendable).use { writer ->
                    xml.encodeToWriter(writer, Snapshot(points))
                }
            }
        } catch (e: Exception) {
            elog(e.toString())
            return Rslt.Err(e.message.orEmpty())
        }
        return Rslt.Ok(name)
    }

    suspend fun get(name: String): Rslt<List<Point>> {
        val file = File(co.filesDir, name)
        return try {
            val snapshot = file.inputStream().use {
                xml.decodeFromStream<Snapshot>(it, QName(RootTag))
            }
            val points = snapshot.points.map { point ->
                val manuf = oui { find(point.bssid) }
                    ?: return@map point
                point.copy(
                    bssidGroup = manuf.bssidGroup,
                    manufacturer = manuf.label,
                    manufacturerDesc = manuf.description,
                )
            }
            Rslt.Ok(points)
        } catch (e: Exception) {
            elog(e.toString())
            Rslt.Err(e.message.orEmpty())
        }
    }

    @Serializable
    @XmlSerialName(RootTag, "", "")
    data class Snapshot(
        val points: List<Point>,
    )

    companion object {

        private const val RootTag = "snapshot"

        private fun defaultName() = "snapshot_${SimpleDateFormat("yyyy.MM.dd-HH.mm.ss", Locale.getDefault()).format(Date())}$SNAPSHOT_FORMAT"

        fun isSnapshotName(name: String) = name.endsWith(SNAPSHOT_FORMAT)

        fun Context.shareSnapshot(name: String) {
            val file = File(filesDir, name)
            val uri = FileProvider.getUriForFile(this, BuildConfig.AUTHORITY, file)
            val intent = Intent(Intent.ACTION_SEND)
                .setType(MIME_TYPE_XML)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TITLE, name)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newUri(contentResolver, name, uri)
            tryStartActivity(intent)
        }
    }
}