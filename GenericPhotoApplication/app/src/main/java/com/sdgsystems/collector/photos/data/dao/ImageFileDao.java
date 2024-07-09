package com.sdgsystems.collector.photos.data.dao;

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
public abstract class ImageFileDao implements ISynchableDao<ImageFile> {

    @Delete
    public abstract void delete(ImageFile ImageFile);

    @Delete
    public abstract void delete(List<ImageFile> ImageFile);

    @Insert
    public abstract Long insert(ImageFile ImageFile);

    @Insert
    public abstract void insert(List<ImageFile> ImageFile);

    @Update
    public abstract void update(ImageFile ImageFile);

    @Update
    public abstract int update(List<ImageFile> ImageFile);
    
    @Query("select * from ImageFile where not uploadStatus = '" + SynchableConstants.UPLOAD_STATUS_UPLOADED + "'")
    public abstract List<ImageFile> getUnsent();

    @Query("select count(*) from ImageFile where not uploadStatus = '" + SynchableConstants.UPLOAD_STATUS_UPLOADED + "'")
    public abstract int countUnsent();

    @Query("select * from ImageFile")
    public abstract List<ImageFile> getAll();

    @Query("select * from ImageFile where uid = :imageFileId")
    public abstract ImageFile getImageFile(Long imageFileId);

    @Query("delete from ImageFile")
    public abstract void deleteAll();

    @Query("select * from Image where uid = :imageId")
    public abstract Image getImage(Long imageId);
}
