package com.sdgsystems.collector.photos.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import android.util.Log;

import com.google.gson.annotations.Expose;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.synchableapplication.SynchableConstants;
import com.sdgsystems.synchableapplication.SynchronizableModel;

import java.io.File;

/**
 * This is a placeholder model for image files prior to being uploaded.  It will hold the reference between the Image record in the database and the file
 *
 * Created by ben on 8/8/17.
 */

@Entity
public class ImageFile extends SynchronizableModel {

    private static final String TAG = "ImageFile";
    @Expose
    @ColumnInfo(name = "mongo_id")
    public String mongo_id;

    @Expose
    @ColumnInfo(name = "image_id")

    public Long image_id;

    @Expose
    @ColumnInfo(name = "filename")
    public String filename;

    public ImageFile() {
        setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
    }

    public String getMongo_id() {
        return mongo_id;
    }

    public void setMongo_id(String mongo_id) {
        this.mongo_id = mongo_id;
    }

    public Long getImage_id() {
        return image_id;
    }

    public void setImage_id(Long image_id) {
        this.image_id = image_id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void deleteLocalFile() {
        SDGLog.d(TAG, "Deleting local file:" + image_id);

        File photo = new File(filename);
        if(photo.exists()) {
            SDGLog.d(TAG, "Deleting local file " + filename);
            photo.delete();
        }

        photo = new File(filename + "_thumb");
        if(photo.exists()) {
            SDGLog.d(TAG, "Deleting local file " + filename + "_thumb");
            photo.delete();
        }
    }
}
