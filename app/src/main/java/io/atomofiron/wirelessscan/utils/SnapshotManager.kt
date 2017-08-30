package io.atomofiron.wirelessscan.utils

import android.arch.persistence.room.Room
import android.content.Context
import android.os.AsyncTask
import io.atomofiron.wirelessscan.room.Node
import io.atomofiron.wirelessscan.room.NodeDao
import io.atomofiron.wirelessscan.room.Snapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class SnapshotManager(private val co: Context) : AsyncTask<() -> Unit, Unit, () -> Unit>() {
    private val formatter = SimpleDateFormat("YY.MM.dd-HH.mm.ss")

    override fun doInBackground(vararg params: () -> Unit) : () -> Unit {
        val count = params.size - 1

        for (i in 0 until count)
            params[i]()

        return params[count]
    }

    override fun onPostExecute(callback: (() -> Unit)) {
        super.onPostExecute(callback)
        callback()
    }

    fun put(nodes: ArrayList<Node>) {
        execute({
            val name = "snapshot_${formatter.format(Date())}.db"

            getDao(co, name).put(nodes)
            File(co.getDatabasePath(name).absolutePath + "-journal").delete()
        }, {})
    }

    fun get(name: String, callback: (list: ArrayList<Node>) -> Unit) {
        val list = ArrayList<Node>()
        execute(
                { list.addAll(getDao(co, name).get().toList()) },
                { callback(list) }
        )
    }

    companion object {
        fun getDao(co: Context, name: String): NodeDao =
                Room.databaseBuilder(co, Snapshot::class.java, name).build().nodeDao()
    }
}