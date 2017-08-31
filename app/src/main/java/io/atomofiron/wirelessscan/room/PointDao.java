package io.atomofiron.wirelessscan.room;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.ArrayList;

@Dao
public interface PointDao {
	@Query("SELECT * FROM points")
	Point[] get();

	@Insert
	void put(ArrayList<Point> point);
}