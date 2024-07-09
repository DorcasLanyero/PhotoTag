package com.sdgsystems.collector.photos.sync;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.synchableapplication.SynchableConstants;
import com.sdgsystems.synchableapplication.Synchronizer;
import com.sdgsystems.synchableapplication.callbacks.IUploadCallback;

import java.util.HashMap;
import java.util.List;

/**
 * Created by bfriedberg on 7/21/17.
 */

public class ImageMetaDataUploader {
    private static final String TAG = "ImageMetaDataUploader";

    public synchronized static void sendAllPendingMetaDataObjects(Context context) {

        if(!Utilities.isConnected(context)) {
            SDGLog.d(TAG, "No network connection");
            return;
        }

        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();

        List<Image> unsentObjects = imageDao.getUnsent();

        SDGLog.d(TAG, "Sending " + unsentObjects.size() + " image metadata objects");

        for(Image object : unsentObjects) {

            if(object.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED)) {
                object.setUploadStatus(SynchableConstants.UPLOAD_STATUS_UPLOADING);
                imageDao.update(object);

                object.inflateFromDatabase();
                uploadImageMetaData(object, context);
            } else {
                SDGLog.d(TAG, "Object " + object.getUid() + " was in the process of uploading...");
            }
        }
    }

    public static void uploadImageMetaData(Image mImage, Context context) {

        if(!Utilities.isConnected(context)) {
            SDGLog.d(TAG, "No network connection");
            return;
        }

        String URL = GenericPhotoApplication.getInstance().getImageApiUrl() + mImage.mongo_id + "/meta";

        if(GenericPhotoApplication.getInstance().getBearerToken() != null) {
            sendWithSynchronizer(mImage, context, URL);
        }
    }

    private static void sendWithSynchronizer(final Image mImage, final Context context, String URL) {

        SDGLog.d(TAG, "Sending metadata for " + mImage.getUid() + " to server");

        Synchronizer.sendModel(context,
                SynchableConstants.UPLOAD_METHOD.UPLOAD_METHOD_PUT,
                URL,
                mImage,
                GenericPhotoApplication.getInstance().getDb().imageDao(),
                new IUploadCallback() {
                    @Override
                    public void OnSuccess(String s) {
                        //Image was uploaded
                        SDGLog.d(TAG, "Image MetaData for " + mImage.getUid() +  " uploaded to remote server, sending broadcast");

                        if(PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext()).getBoolean("PREF_DELETE_UPLOADED_IMAGES", true)) {
                            if(mImage != null && mImage.getUid() != null && mImage.getUid() != -1) {
                                ImageFile file = GenericPhotoApplication.getInstance().getDb().imageDao().getImageFile(mImage.getUid());
                                if(file != null) {
                                    file.deleteLocalFile();
                                }
                            }
                        }

                        Intent updateIntent = new Intent(context.getResources().getString(R.string.UPDATE_IMAGE_INTENT));
                        context.sendBroadcast(updateIntent);
                    }

                    @Override
                    public void OnFailure(String s) {
                        //Image was NOT uploaded
                        SDGLog.d(TAG, "Image MetaData NOT uploaded to remote server: " + s);

                        mImage.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
                        GenericPhotoApplication.getInstance().getDb().imageDao().update(mImage);
                    }

                    @Override
                    public HashMap<String, String> getHeaders() {
                        return GenericPhotoApplication.getInstance().getAuthHeaders();
                    }
                    @Override
                    public SynchableConstants.BODY_TYPE getBodyType() {
                        return SynchableConstants.BODY_TYPE.JSON;
                    }
                },
                false
        );
    }
}
