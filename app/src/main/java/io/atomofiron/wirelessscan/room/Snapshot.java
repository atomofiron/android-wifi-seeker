package io.atomofiron.wirelessscan.room;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {Node.class}, version = 1)
public abstract class Snapshot extends RoomDatabase {
	public abstract NodeDao nodeDao();
}