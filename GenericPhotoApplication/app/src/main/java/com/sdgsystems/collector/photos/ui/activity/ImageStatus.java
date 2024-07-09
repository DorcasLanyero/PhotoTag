package com.sdgsystems.collector.photos.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.collector.photos.sync.ImageFileUploader;

import java.io.File;
import java.io.IOException;

import static com.sdgsystems.collector.photos.ui.activity.PhotoDetailActivity.IMAGE_DATABASE_ID;

public class ImageStatus extends AppCompatActivity {

    private static final String TAG = "ImageStatus";
    private long mImageDatabaseId;
    private Image mImage;
    private ImageFile mImageFile;
    private IntentReceiver mIntentReceiver;
    private boolean myReceiverIsRegistered = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_status);

        mImageDatabaseId = getIntent().getLongExtra(IMAGE_DATABASE_ID, -1);

        updateImage();

        mIntentReceiver = new IntentReceiver();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    private void updateImage() {
        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
        ImageFileDao imageFileDao = GenericPhotoApplication.getInstance().getDb().imageFileDao();

        mImage = imageDao.getImage(mImageDatabaseId);

        mImage.inflateFromDatabase();

        TextView filename = (TextView) findViewById(R.id.filename);
        TextView filesize = (TextView) findViewById(R.id.filesize);
        TextView id = (TextView) findViewById(R.id.id);
        TextView mongo_id = (TextView) findViewById(R.id.mongo_id);
        TextView image_upload_status = (TextView) findViewById(R.id.image_upload_status);
        TextView image_details_upload_status = (TextView) findViewById(R.id.image_details_upload_status);
        TextView json = (TextView) findViewById(R.id.json_representation);


        mImageFile = imageDao.getImageFile(mImageDatabaseId);// mImage.getImageFile();

        if (mImageFile != null) {
            filename.setText(mImageFile.filename);
            File file = new File(mImageFile.filename);
            if (file.exists()) {
                filesize.setText(file.length() + " bytes");
            }

            id.setText(String.valueOf(mImage.getUid()));
            mongo_id.setText(mImage.mongo_id);
            image_upload_status.setText(mImageFile.getUploadStatus());
            image_details_upload_status.setText(mImage.getUploadStatus());
            json.setText(mImage.json_representation);
        }
    }

    public void upload(View view) {

        if(mImageFile != null) {
            try {
                ImageFileUploader.sendImageFile(getApplicationContext(), mImageFile, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!myReceiverIsRegistered) {
            registerReceiver(mIntentReceiver, new IntentFilter(getResources().getString(R.string.UPDATE_IMAGE_INTENT)));
            myReceiverIsRegistered = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (myReceiverIsRegistered) {
            unregisterReceiver(mIntentReceiver);
            myReceiverIsRegistered = false;
        }
    }

    private class IntentReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SDGLog.d(TAG, "got broadcast: " + intent.getAction());

            if(intent.getAction().equals(context.getResources().getString(R.string.UPDATE_IMAGE_INTENT))) {
                SDGLog.d(TAG, "updating the image list due to intent");
                updateImage();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            SDGLog.d(TAG, "Heading back...");
            onBackPressed();
             return true;
        }
        return false;
    }

}


