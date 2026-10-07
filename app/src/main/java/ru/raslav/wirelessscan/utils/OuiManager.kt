package ru.raslav.wirelessscan.utils

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.raslav.wirelessscan.Const.PREF_OUI_TEXT_LENGTH
import ru.raslav.wirelessscan.data.Loading
import ru.raslav.wirelessscan.elog
import ru.raslav.wirelessscan.sp
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.regex.Pattern
import kotlin.math.min

private const val DB_NAME = "oui.db"
private val DIGITS = arrayOf(6, 7, 9)

private const val TABLE = "OUI"
private const val COLUMN_MAC = "MAC"
private const val COLUMN_LABEL = "label"
private const val COLUMN_DESC = "description"

class OuiManager private constructor(context: Context) {
    companion object {

        private const val OUI_TEXT_LENGTH = 3169791L
        private const val BUILTIN_ENTRIES = 58482L

        lateinit var self: OuiManager

        private val scope = CoroutineScope(Job())

        fun init(context: Context) {
            self = OuiManager(context)
        }
    }
    private var db: SQLiteDatabase
    private val tmpFile = File(context.filesDir, "tmp.db")
    private val file = File(context.filesDir, DB_NAME)
    val ouiLoading: StateFlow<Loading<Long>?>
        field = MutableStateFlow(null)

    init {
        if (!file.exists()) {
            context.extractBuiltInFile()
        }
        db = openDatabase()
        if (entries() < BUILTIN_ENTRIES) {
            db.close()
            file.delete()
            context.extractBuiltInFile()
            db = openDatabase()
        }
    }

    private fun openDatabase(): SQLiteDatabase = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    fun entries(): Long = DatabaseUtils.queryNumEntries(db, TABLE)

    fun find(bssid: String): Manufacturer {
        val mac = bssid.replace(":", "").uppercase()
        DIGITS.map { mac.take(it) }.forEach { digits ->
            db.find(digits)?.let { return it }
        }
        return Manufacturer.Unknown
    }

    fun update(context: Context) {
        ouiLoading.value = Loading()
        val sp = context.sp()
        val fallbackTotal = sp.getLong(PREF_OUI_TEXT_LENGTH, OUI_TEXT_LENGTH)
        scope.launch(IO) {
            try {
                val entries = entries()
                val connection = URL("https://www.wireshark.org/download/automated/data/manuf") // alternative https://www.wireshark.org/json/manuf.json
                    .openConnection()
                val total = when {
                    SDK_INT >= N -> connection.contentLengthLong
                    else -> fallbackTotal
                }.toFloat()
                val bytes = ByteArrayOutputStream()
                val step = total / 50
                var counter = 0
                var downloaded = 0L
                connection.getInputStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytes.write(buffer, 0, read)
                        downloaded += read
                        counter += read
                        if (counter > step) {
                            counter = 0
                            ouiLoading.emit(Loading(min(downloaded / total, 1f) / 2))
                        }
                    }
                }
                if (downloaded > total) scope.launch(Main) {
                    sp.edit { putLong(PREF_OUI_TEXT_LENGTH, downloaded) }
                }
                val newEntries = bytes.toString(Charsets.UTF_8.name()).parse(entries) {
                    ouiLoading.emit(Loading(0.5f + it / 2))
                }
                ouiLoading.emit(Loading())
                db.close()
                file.delete()
                tmpFile.renameTo(file)
                db = openDatabase()
                ouiLoading.emit(Loading.Finished(newEntries))
            } catch (e: Exception) {
                elog(e.toString())
                ouiLoading.emit(Loading(e.toString()))
            }
        }.start()
    }

    private fun SQLiteDatabase.find(digits: String): Manufacturer? {
        val cursor = rawQuery("select * from $TABLE where $COLUMN_MAC=?;", arrayOf(digits))
        val manufacturer = cursor.takeIf { it.moveToFirst() }?.run {
            val label = cursor.getColumnIndex(COLUMN_LABEL)
                .takeIf { it >= 0 }
                ?.let { getString(it) }
            val description = cursor.getColumnIndex(COLUMN_DESC)
                .takeIf { it >= 0 }
                ?.let { getString(it) }
            if (label == null && description == null) {
                return@run null
            }
            Manufacturer(digits, label = label ?: "", description = description ?: "")
        }
        cursor.close()
        return manufacturer
    }

    private fun Context.extractBuiltInFile() {
        assets.open(DB_NAME).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }

    private suspend fun String.parse(
        entries: Long,
        progress: suspend (Float) -> Unit,
    ): Long {
        // 08:45:D1         	Cisco       	Cisco Systems, Inc
        // 00:55:DA:90/28   	QuantumCommu	Quantum Communication Technology Co., Ltd.,Anhui
        // 00:1B:C5:0B:90/36	DenkiKogyo  	Denki Kogyo Company, Limited
        tmpFile.delete()

        val db = SQLiteDatabase.openOrCreateDatabase(tmpFile.absolutePath, null)
        db.execSQL("create table $TABLE ($COLUMN_MAC TEXT NOT NULL, $COLUMN_LABEL TEXT NOT NULL, $COLUMN_DESC TEXT NOT NULL)")

        val delimiter = Pattern.compile(" *\t")
        val statement = db.compileStatement("insert into $TABLE ($COLUMN_MAC, $COLUMN_LABEL, $COLUMN_DESC) values (?, ?, ?)")
        val step = entries / 100
        var counter = 0L
        var totalCounter = 0L
        splitToSequence('\n').forEach { line ->
            if (line.isEmpty() || line.startsWith('#')) {
                return@forEach
            }
            val parts = line.split(delimiter)
            if (parts.size != 3) {
                return@forEach elog("invalid line: $line")
            }
            val address = parts.first().split('/') // "08:45:D1" or "00:55:DA:90/28" or "00:1B:C5:0B:90/36"
            var mac = address.first().replace(":", "") // "0845D1" or "0055DA90" or "001BC50B90"
            address.getOrNull(1) // null or 28 or 36
                ?.toIntOrNull() // null then "0845D1"
                ?.let { it / 4 } // 28 or 36 -> 7 or 9
                ?.let { mac = mac.take(it) } // "0055DA90" or "001BC50B90" -> "0055DA9" or "001BC50B9"

            statement.clearBindings()
            statement.bindString(1, mac)
            statement.bindString(2, parts[1])
            statement.bindString(3, parts[2].replace('\'', '’'))
            statement.executeInsert()

            totalCounter++
            if (counter++ > step) {
                counter = 0
                progress(min(totalCounter / entries.toFloat(), 1f))
            }
        }
        db.close()
        return totalCounter
    }
}
