package io.atomofiron.wirelessscan.utils

import android.arch.persistence.room.Room
import android.content.Context
import android.os.AsyncTask
import io.atomofiron.wirelessscan.room.Snapshot
import io.atomofiron.wirelessscan.room.Node
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SnapshotMaker(private val co: Context) : AsyncTask<ArrayList<Node>, Unit, Unit>() {
    private val formatter = SimpleDateFormat("YY.MM.dd-HH.mm.ss")

    override fun doInBackground(vararg params: ArrayList<Node>) {
        val name = "snapshot_${formatter.format(Date())}.db"

        Room.databaseBuilder(co, Snapshot::class.java, name).build().nodeDao().put(params[0])
        File(co.getDatabasePath(name).absolutePath + "-journal").delete()
    }
}