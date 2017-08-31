package io.atomofiron.wirelessscan.utils

import android.arch.persistence.room.Room
import android.content.Context
import android.widget.Toast
import io.atomofiron.wirelessscan.R
import io.atomofiron.wirelessscan.room.Node
import io.atomofiron.wirelessscan.room.NodeDao
import io.atomofiron.wirelessscan.room.Snapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SnapshotManager(private val co: Context) {
    private val formatter = SimpleDateFormat("YY.MM.dd-HH.mm.ss")

    fun put(nodes: ArrayList<Node>) {
        val name = "snapshot_${formatter.format(Date())}.db"

        getDao(name).put(nodes)
        File(co.getDatabasePath(name).absolutePath + "-journal").delete()
    }

    fun get(name: String): ArrayList<Node> {
        val list = ArrayList<Node>()
        list.addAll(getDao(name).get().toList())
        return list
    }

    private fun getDao(name: String): NodeDao =
            Room.databaseBuilder(co, Snapshot::class.java, name).allowMainThreadQueries().build().nodeDao()
}