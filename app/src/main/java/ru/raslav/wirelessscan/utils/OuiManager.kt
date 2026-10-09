package ru.raslav.wirelessscan.utils

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.N
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.raslav.wirelessscan.BuildConfig
import ru.raslav.wirelessscan.data.Loading
import ru.raslav.wirelessscan.data.OuiMeta
import ru.raslav.wirelessscan.elog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.regex.Pattern
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val DB_NAME = "oui.db"
private val DIGITS = arrayOf(6, 7, 9)

private const val TABLE = "OUI"
private const val COLUMN_MAC = "MAC"
private const val COLUMN_LABEL = "label"
private const val COLUMN_DESC = "description"
private const val INDEX_MAC = "idx_oui_mac"

private val MockRefreshing = BuildConfig.DEBUG

class OuiManager private constructor(context: Context) {
    companion object {

        const val BUILTIN_OUI_TEXT_LENGTH = 3169791L
        private const val BUILTIN_ENTRIES = 58482L

        private lateinit var self: OuiManager

        private val mutex = Mutex()

        val ouiLoading: StateFlow<Loading<OuiMeta>?>
            field = MutableStateFlow(null)

        suspend fun <T> oui(action: OuiManager.() -> T): T {
            return mutex.withLock {
                self.action()
            }
        }

        fun <T> javaOui(action: OuiManager.() -> T): T {
            return runBlocking {
                oui(action)
            }
        }

        suspend fun update(length: Long) = self.update(length)

        fun init(context: Context) {
            self = OuiManager(context)
        }
    }

    private var db: SQLiteDatabase
    private val tmpFile = File(context.filesDir, "tmp.db")
    private val file = File(context.filesDir, DB_NAME)

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

    private fun openDatabase(): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, OPEN_READONLY)

        val cursor = db.rawQuery("select count(*) from sqlite_master where type='index' and name=?;", arrayOf(INDEX_MAC))
        val exists = cursor.moveToFirst() && cursor.getInt(0) > 0
        cursor.close()
        if (exists) {
            return db
        }
        db.close()
        SQLiteDatabase.openDatabase(file.absolutePath, null, OPEN_READWRITE).use { db ->
            db.execSQL("create index if not exists $INDEX_MAC on $TABLE($COLUMN_MAC);")
        }

        return SQLiteDatabase.openDatabase(file.absolutePath, null, OPEN_READONLY)
    }

    fun entries(): Long = DatabaseUtils.queryNumEntries(db, TABLE)

    fun find(bssid: String): Manufacturer? {
        val mac = bssid.replace(":", "").uppercase()
        val candidates = DIGITS.map { mac.take(it) }
        val found = db.find(candidates)
        candidates.forEach {
            candidate -> found[candidate]?.let { return it }
        }
        return null
    }

    private suspend fun update(length: Long): OuiMeta? = withContext(IO) {
        if (MockRefreshing) {
            return@withContext fakeUpdate(length)
        }
        try {
            ouiLoading.emit(Loading())
            val entries = oui { entries() }
            val connection = URL("https://www.wireshark.org/download/automated/data/manuf") // alternative https://www.wireshark.org/json/manuf.json
                .openConnection()
            val total = when {
                SDK_INT >= N && connection.contentLengthLong > 0 -> connection.contentLengthLong
                else -> length
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
            val newEntries = bytes.toString(Charsets.UTF_8.name()).parse(entries) {
                ouiLoading.emit(Loading(0.5f + it / 2))
            }
            ouiLoading.emit(Loading())
            oui {
                db.close()
                file.delete()
                tmpFile.renameTo(file)
                db = openDatabase()
            }
            OuiMeta(entries = newEntries, length = downloaded)
                .also { ouiLoading.emit(Loading.Finished(it)) }
        } catch (e: Exception) {
            elog(e.toString())
            ouiLoading.emit(Loading(e.toString()))
            null
        }
    }

    private fun SQLiteDatabase.find(digits: List<String>): Map<String, Manufacturer> {
        val cursor = rawQuery(
            "select * from $TABLE where $COLUMN_MAC in (${digits.joinToString { "?" }});",
            digits.toTypedArray(),
        )
        val found = mutableMapOf<String, Manufacturer>()
        val macColumn = cursor.getColumnIndex(COLUMN_MAC)
        val labelColumn = cursor.getColumnIndex(COLUMN_LABEL)
        val descColumn = cursor.getColumnIndex(COLUMN_DESC)
        while (cursor.moveToNext()) {
            val label = labelColumn.takeIf { it >= 0 }?.let { cursor.getString(it) }
            val description = descColumn.takeIf { it >= 0 }?.let { cursor.getString(it) }
            if (label == null && description == null) {
                continue
            }
            val mac = cursor.getString(macColumn)
            found[mac] = Manufacturer(mac, label = label ?: "", description = description ?: "")
        }
        cursor.close()
        return found
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

    private suspend fun fakeUpdate(length: Long): OuiMeta? {
        ouiLoading.emit(Loading())
        delay(500.milliseconds)
        ouiLoading.emit(Loading(0.3f))
        delay(500.milliseconds)
        ouiLoading.emit(Loading(0.5f))
        delay(500.milliseconds)
        ouiLoading.emit(Loading(0.8f))
        delay(500.milliseconds)
        ouiLoading.emit(Loading(1f))
        delay(500.milliseconds)
        ouiLoading.emit(Loading(null))
        delay(500.milliseconds)
        val entries = oui {
            db.close()
            db = openDatabase()
            entries()
        }
        return OuiMeta(entries, length)
            .also { ouiLoading.emit(Loading.Finished(it)) }
    }
}
