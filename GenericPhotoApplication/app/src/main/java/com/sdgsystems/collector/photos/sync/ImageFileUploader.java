package com.sdgsystems.collector.photos.sync;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.gson.Gson;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.synchableapplication.SynchableConstants;
import com.sdgsystems.synchableapplication.Synchronizer;
import com.sdgsystems.synchableapplication.callbacks.IUploadCallback;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Created by bfriedberg on 7/21/17.
 */

public class ImageFileUploader {
    private static final String TAG = "ImageFileUploader";

    public synchronized static void sendAllPendingImages(Context context) {

        if(!Utilities.isConnected(context)) {
            SDGLog.d(TAG, "No network connection");
            return;
        }

        List<ImageFile> unsentObjects = Synchronizer.getUnsentObjects(GenericPhotoApplication.getInstance().getDb().imageFileDao());

        SDGLog.d(TAG, "got the following unsent objects: " + unsentObjects);


        for(ImageFile object : unsentObjects) {

            if(object.getUploadStatus().equals(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED)) {

                try {
                    object.setUploadStatus(SynchableConstants.UPLOAD_STATUS_UPLOADING);
                    GenericPhotoApplication.getInstance().getDb().imageFileDao().update(object);

                    sendImageFile(context, object);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                SDGLog.d(TAG, "Not uploading " + object.filename + " already in process: " + object.getUploadStatus());
            }
        }
    }


    public static void sendImageFile(final Context context, final ImageFile file) throws IOException {
        sendImageFile(context, file, false);
    }

    public static void sendImageFile(final Context context, final ImageFile file, final boolean sendUpdateIntents) throws IOException {

        if(!Utilities.isConnected(context)) {
            SDGLog.d(TAG, "No network connection");
            return;
        }

        String URL = GenericPhotoApplication.getInstance().getImagesUrl();

        SDGLog.d(NetworkRequestHandler.TAG, "trying to upload " + file.filename + " to " + URL);

        Synchronizer.sendModel(context, SynchableConstants.UPLOAD_METHOD.UPLOAD_METHOD_POST, URL,
                file, GenericPhotoApplication.getInstance().getDb().imageFileDao(), new IUploadCallback() {
            @Override
            public void OnSuccess(String s) {
                parseImageUploadResponse(s, file, context, sendUpdateIntents);
            }

            @Override
            public void OnFailure(String s) {
                //failed, do something better.
                SDGLog.w(TAG, "Failed to upload file " + file.filename + ": " + s);

                File localFile = new File(file.getFilename());

                if(localFile.exists()) {
                    file.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);

                    GenericPhotoApplication.getInstance().getDb().imageFileDao().update(file);
                }
                else {
                    SDGLog.e(TAG, "Local image file no longer exists!");
                    file.deleteLocalFile();
                    GenericPhotoApplication.getInstance().getDb().imageFileDao().delete(file);
                }

                if(sendUpdateIntents) {
                    sendUpdateIntent(context);
                }
            }

            @Override
            public HashMap<String, String> getHeaders() {
                HashMap<String, String> headers = GenericPhotoApplication.getInstance().getAuthHeaders();
                headers.put("Content-Type", "application/octet-stream");
                return headers;
            }

            @Override
            public SynchableConstants.BODY_TYPE getBodyType() {
                return SynchableConstants.BODY_TYPE.FILE;
            }
        }, false);
    }

    private static void sendUpdateIntent(Context context) {
        Intent updateIntent = new Intent(context.getResources().getString(R.string.UPDATE_IMAGE_INTENT));
        context.sendBroadcast(updateIntent);
    }

    // Parse image upload JSON and turn it into image metadata to upload
    static void parseImageUploadResponse(String response, ImageFile file, Context context, boolean sendUpdateIntents) {
        SDGLog.d(NetworkRequestHandler.TAG, "Saved photo: Raw JSON" + response);

        //We have the new mongo_id, should upload the image meta data at this point
        Gson imageGson = Image.getGson();
        Image resultImageTmp = imageGson.fromJson(response, Image.class);

        SDGLog.d(NetworkRequestHandler.TAG, "Saved photo: Image mongo_id:" + resultImageTmp.mongo_id);

        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
        ImageFileDao imageFileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();

        Image resultImage = imageDao.getImage(file.image_id);
        if(resultImage != null) {
          resultImage.inflateFromDatabase();

          //Copy over the new mongo id for the photo to the result image.  This is the trigger for the meta data to be uploaded
          resultImage.mongo_id = resultImageTmp.mongo_id;
          resultImage.setUploadStatus(SynchableConstants.UPLOAD_STATUS_NOT_UPLOADED);
          resultImage.compressToDatabase();
          imageDao.update(resultImage);

          //Reload the file object from the db since it just had it's upload status marked up...
          file = imageFileDao.getImageFile(file.getUid());
          file.mongo_id = resultImageTmp.mongo_id;
          imageFileDao.update(file);

            if(GenericPhotoApplication.getInstance().getBearerToken() != null) {
                ImageMetaDataUploader.sendAllPendingMetaDataObjects(context);
            }

            if(sendUpdateIntents) {
                sendUpdateIntent(context);
            }
        }
    }
}
