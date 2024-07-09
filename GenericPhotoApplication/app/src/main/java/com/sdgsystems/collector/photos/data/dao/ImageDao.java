package com.sdgsystems.collector.photos.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.synchableapplication.ISynchableDao;
import com.sdgsystems.synchableapplication.SynchableConstants;

import java.util.List;

/**
 * Created by bfriedberg on 10/10/17.
 */

@Dao
public abstract class ImageDao implements ISynchableDao<Image> {

    @Delete
    public abstract void delete(Image Image);

    @Delete
    public abstract void delete(List<Image> Image);

    @Insert
    public abstract Long insert(Image Image);

    @Insert
    public abstract void insert(List<Image> Image);

    @Update
    public abstract void update(Image Image);

    @Update
    public abstract int update(List<Image> Image);
    
    @Query("select * from Image where not uploadStatus = '" + SynchableConstants.UPLOAD_STATUS_UPLOADED + "'")
    public abstract List<Image> getUnsent();

    @Query("select count(*) from Image where not uploadStatus = '" + SynchableConstants.UPLOAD_STATUS_UPLOADED + "'")
    public abstract int countUnsent();

    @Query("select * from Image")
    public abstract List<Image> getAll();

    @Query("select * from Image where uid = :imageId")
    public abstract Image getImage(Long imageId);

    @Query("delete from Image")
    public abstract void deleteAll();

    @Query("select * from ImageFile where image_id = :imageId")
    public abstract ImageFile getImageFile(Long imageId);
}
