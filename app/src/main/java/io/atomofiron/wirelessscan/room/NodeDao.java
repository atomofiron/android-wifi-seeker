package io.atomofiron.wirelessscan.room;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.ArrayList;

@Dao
public interface NodeDao {
	@Query("SELECT * FROM nodes")
	Node[] get();

	@Insert
	void put(ArrayList<Node> node);
}