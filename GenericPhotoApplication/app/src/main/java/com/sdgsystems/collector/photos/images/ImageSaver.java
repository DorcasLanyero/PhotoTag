package com.sdgsystems.collector.photos.images;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ThumbnailUtils;
import android.util.Log;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.ui.activity.IImageSaverCallback;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.collector.photos.ui.activity.ThumbnailListActivity;
import com.sdgsystems.collector.photos.data.model.ImageCategory;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.collector.photos.sync.ImageFileUploader;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by bfriedberg on 8/8/17.
 */

public class ImageSaver implements Runnable {
    private static final String TAG = "ImageSaver";

        /**
         * The file we save the image into.
         */
        private File mFile = null;
        private final Context mContext;
        private byte[] mBytes;

        private ArrayList<String> mTags;
        private ArrayList<String> mCategories;
        private String mDescription;
        private int mOrientation;

        private IImageSaverCallback mCallback;

        public ImageSaver(File image, Context context, ArrayList<String> tags, ArrayList<String> categories, String description, IImageSaverCallback callback, int orientation) throws IOException {

            mFile = image;

            mContext = context;
            mOrientation = orientation;

            mTags = tags;
            mCategories = categories;
            mDescription = description;

            mBytes = IOUtils.readFully(new FileInputStream(image), (int) image.length());

            mCallback = callback;
        }

        @Override
        public void run() {
            ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
            ImageFileDao imageFileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();

            FileOutputStream output = null;
            int exifOrientation = 1;
            try {
                ExifInterface exif = new ExifInterface(mFile.getAbsolutePath());
                exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if(exifOrientation == ExifInterface.ORIENTATION_UNDEFINED) {
                    if(mOrientation != ExifInterface.ORIENTATION_UNDEFINED) {
                        SDGLog.d(TAG, "Writing exif orientation derived from screen rotation");
                        // Write to file
                        exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(mOrientation));
                        exif.saveAttributes();

                        // Save to image DB object
                        exifOrientation = mOrientation;
                    }
                    else {
                        SDGLog.d(TAG, "exif orientation derived from screen rotation is invalid, writing 'normal'");
                        exifOrientation = ExifInterface.ORIENTATION_NORMAL; // seems like a decent guess most of the time
                    }
                }
                else {
                    SDGLog.d(TAG, "Image has existing exif orientation: " + exifOrientation);
                }

                Bitmap fullsizeBitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath());

                int width = Math.max(480, fullsizeBitmap.getWidth() / 10);
                double factor = (float) width / (float) fullsizeBitmap.getWidth();
                int height = (int) (fullsizeBitmap.getHeight() * factor);

                SDGLog.d(TAG, "Resizing thumbnail to " + width + "x" + height);
                Bitmap thumbnailBitmap = ThumbnailUtils.extractThumbnail(fullsizeBitmap, width, height);

                File thumbnailFile = new File(mFile.getAbsoluteFile() + "_thumb");
                output = new FileOutputStream(thumbnailFile);
                thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
                output.flush();

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            SDGLog.d(TAG, "captured image, calling send image method");

            ImageFile file = new ImageFile();
            file.filename = mFile.getAbsolutePath();
            Long fileId = imageFileDao.insert(file);
            file.setUid(fileId);

            com.sdgsystems.collector.photos.data.model.Image newImage = new com.sdgsystems.collector.photos.data.model.Image();

            //String currentID = PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext()).getString(Constants.PREF_DEVICE_ID, null);

            //Log.d(TAG, "Setting image tags: "  + mTags + " " + currentID);

            if(mTags != null && !mTags.isEmpty()) {
                newImage.tags = mTags;
            }
            if(newImage.exif_data == null) {
                newImage.exif_data = new HashMap<>();
            }
            newImage.exif_data.put("orientation", new com.sdgsystems.collector.photos.data.model.Image.Datapoint(exifOrientation));

            //newImage.tags.add(currentID);



            if(mCategories != null && !mCategories.isEmpty()) {
                for(String categoryName : mCategories) {
                    for(ImageCategory category : GenericPhotoApplication.getInstance().categories) {
                        if(category.name.equals(categoryName)) {
                            SDGLog.v(TAG, "adding category " + categoryName);
                            newImage.categories.add(category);
                        }
                    }
                }
            }

            if(mDescription != null && mDescription.length() != 0) {
                newImage.annotation = mDescription;
            }


            Location currentLocation = GenericPhotoApplication.getInstance().getLastKnownLocation();

            if(currentLocation != null) {

                SDGLog.d(TAG, "Setting location to " + currentLocation.getLatitude() + " " + currentLocation.getLongitude());

                com.sdgsystems.collector.photos.data.model.Image.Location location = new com.sdgsystems.collector.photos.data.model.Image.Location();
                location.coordinates = new ArrayList<Double>();
                location.coordinates.add(Double.valueOf(currentLocation.getLongitude()));
                location.coordinates.add(Double.valueOf(currentLocation.getLatitude()));

                newImage.location = location;
            } else {
                SDGLog.d(TAG, "no last known location...");
            }

            newImage.compressToDatabase();
            Long newImageId = imageDao.insert(newImage);
            newImage.setUid(newImageId);

            file.image_id = newImageId;
            imageFileDao.update(file);

            mCallback.imageSaved(newImage);

            if(GenericPhotoApplication.getInstance().getBearerToken() != null) {
                ImageFileUploader.sendAllPendingImages(mContext);
            }
        }
}
