package com.sdgsystems.collector.photos.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;

/**
 * Created by bfriedberg on 10/10/17.
 */

@Database(entities = {Image.class, ImageFile.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ImageDao imageDao();
    public abstract ImageFileDao imageFileDao();
}
