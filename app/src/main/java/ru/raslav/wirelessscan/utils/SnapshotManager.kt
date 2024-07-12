package ru.raslav.wirelessscan.utils

import android.content.Context
import android.widget.Toast
import ru.raslav.wirelessscan.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import org.simpleframework.xml.Root
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.ElementList
import ru.raslav.wirelessscan.Const
import java.io.StringWriter

class SnapshotManager(private val co: Context) {

    /** @return snapshot file name*/
    fun put(points: List<Point>): String? {
        val name = "snapshot_${SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format(Date())}${Const.SNAPSHOT_FORMAT}"
        val file = File(co.filesDir, name)

        if (!co.filesDir.exists() && !co.filesDir.mkdirs() || !co.filesDir.canWrite()) {
            Toast.makeText(co, R.string.error, Toast.LENGTH_LONG).show()
            return null
        }

        try {
            val writer = StringWriter()
            Persister().write(Snapshot(points), writer)
            val stream = file.outputStream()
            stream.write(writer.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            stream.close()
        } catch (e: Exception) {
            Toast.makeText(co, e.message, Toast.LENGTH_LONG).show()
            return null
        }

        Toast.makeText(co, R.string.snapshot_saved, Toast.LENGTH_SHORT).show()
        return name
    }

    fun get(name: String): List<Point>? {
        val file = File(co.filesDir, name)
        return try {
            Persister().read(Snapshot::class.java, file.readText(Charsets.UTF_8), false).points
        } catch (e: Exception) {
            Toast.makeText(co, e.message, Toast.LENGTH_LONG).show()

            null
        }
    }

    @Root(name = "snapshot")
    private class Snapshot(
        @field:ElementList(inline = true, name = "points")
        var points: List<Point>? = null,
    )
}