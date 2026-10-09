package ru.raslav.wirelessscan.utils

import android.content.Context
import android.widget.Toast
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.newGenericWriter
import nl.adaptivity.xmlutil.serialization.OutputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.decodeFromStream
import nl.adaptivity.xmlutil.xmlStreaming
import ru.raslav.wirelessscan.Const
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.data.Point
import ru.raslav.wirelessscan.elog
import ru.raslav.wirelessscan.utils.OuiManager.Companion.oui
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class SnapshotManager(private val co: Context) {

    private val xml = XML.v1 {
        policy {
            defaultPrimitiveOutputKind = OutputKind.Element
            ignoreUnknownChildren()
        }
    }

    /** @return snapshot file name*/
    fun put(points: List<Point>): String? {
        val name = "snapshot_${SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format(Date())}${Const.SNAPSHOT_FORMAT}"
        val file = File(co.filesDir, name)

        if (!co.filesDir.exists() && !co.filesDir.mkdirs() || !co.filesDir.canWrite()) {
            Toast.makeText(co, R.string.error, Toast.LENGTH_LONG).show()
            return null
        }

        try {
            file.outputStream().bufferedWriter(Charsets.UTF_8).use { output ->
                xmlStreaming.newGenericWriter(output as Appendable).use { writer ->
                    xml.encodeToWriter(writer, Snapshot(points))
                }
            }
        } catch (e: Exception) {
            elog(e.toString())
            Toast.makeText(co, e.message, Toast.LENGTH_LONG).show()
            return null
        }

        Toast.makeText(co, R.string.snapshot_saved, Toast.LENGTH_SHORT).show()
        return name
    }

    suspend fun get(name: String): List<Point>? {
        val file = File(co.filesDir, name)
        return try {
            val snapshot = file.inputStream().use {
                xml.decodeFromStream<Snapshot>(it, QName("snapshot"))
            }
            snapshot.points.map { point ->
                val manuf = oui { find(point.bssid) }
                    ?: return@map point
                point.copy(
                    bssidHex = manuf.digits,
                    manufacturer = manuf.label,
                    manufacturerDesc = manuf.description,
                )
            }
        } catch (e: Exception) {
            elog(e.toString())
            Toast.makeText(co, e.message, Toast.LENGTH_LONG).show()
            return null
        }
    }

    @Serializable
    @XmlSerialName("snapshot", "", "")
    data class Snapshot(
        val points: List<Point>,
    )
}