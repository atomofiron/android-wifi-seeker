package ru.raslav.wirelessscan.utils

import android.arch.persistence.room.Room
import android.content.Context
import android.widget.Toast
import ru.raslav.wirelessscan.R
import ru.raslav.wirelessscan.room.Point
import ru.raslav.wirelessscan.room.Snapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SnapshotManager(private val co: Context) {
    private val formatter = SimpleDateFormat("YY.MM.dd-HH.mm.ss")

    /** @return database file name*/
    fun put(points: ArrayList<Point>): String {
        val name = "snapshot_${formatter.format(Date())}.db"

        val snapshot = getSnapshot(name)
        snapshot.pointDao().put(points)
        snapshot.close()

        File(co.getDatabasePath(name).absolutePath + "-journal").delete()

        Toast.makeText(co, R.string.snapshot_saved, Toast.LENGTH_SHORT).show()
        return name
    }

    fun get(name: String): ArrayList<Point> {
        val list = ArrayList<Point>()
        val snapshot = getSnapshot(name)

        list.addAll(snapshot.pointDao().get().toList())
        snapshot.close()

        return list
    }

    private fun getSnapshot(name: String): Snapshot =
            Room.databaseBuilder(co, Snapshot::class.java, name).allowMainThreadQueries().build()
}