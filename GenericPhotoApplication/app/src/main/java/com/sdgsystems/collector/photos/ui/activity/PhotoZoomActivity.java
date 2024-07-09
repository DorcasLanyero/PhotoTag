package com.sdgsystems.collector.photos.ui.activity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ImageView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue;

import java.io.File;

public class PhotoZoomActivity extends AppCompatActivity {
    public static final String IMAGE_MONGO_ID = "com.sdgsystems.EXTRA_IMAGE_MONGO_ID";
    public static final String IMAGE_DATABASE_ID = "com.sdgsystems.EXTRA_IMAGE_DATABASE_ID";
    public static final String IMAGE_EXIF_ROTATION = "com.sdgsystems.EXTRA_IMAGE_EXIF_ROTATION";
    private static final String TAG = "PhotoZoomActivity";

    private String mUrlRoot = "";
    private String mImageMongoId = "";
    private int mImageRotation = 1;
    private Long mImageDatabaseId = null;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_zoom);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mImageMongoId = getIntent().getStringExtra(IMAGE_MONGO_ID);
        mImageDatabaseId = getIntent().getLongExtra(IMAGE_DATABASE_ID, -1);
        mImageRotation = getIntent().getIntExtra(IMAGE_EXIF_ROTATION, 1);
        mUrlRoot = getIntent().getStringExtra(ThumbnailListActivity.URL_ROOT);

        // Always always always get from the database—that way, we check if the image is
        // uploaded whenever we resume the activity
        ImageDao imageDao = GenericPhotoApplication.getInstance().getDb().imageDao();
        Image i = imageDao.getImage(mImageDatabaseId);
        if(i != null) {
            i.inflateFromDatabase();
            mImageMongoId = i.mongo_id;
        }

        mImageView = (ImageView) findViewById(R.id.photo_fullscreen);
        mImageView.setImageDrawable(getResources().getDrawable(R.drawable.ellipsis));

        if(mImageDatabaseId == -1 && (mImageMongoId == null || mImageMongoId.isEmpty())) {
            return;
        }

        if(mImageMongoId == null || mImageMongoId.isEmpty()) {
            Image mImage = imageDao.getImage(mImageDatabaseId);
            mImage.inflateFromDatabase();

            File imageFile = new File(imageDao.getImageFile(mImage.getUid()).filename);
            boolean mImageLoaded = false;
            if(imageFile.exists()) {
                Bitmap imageBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                if (imageBitmap != null) {
                    imageBitmap = Utilities.rotateBitmap(imageBitmap, mImageRotation);
                    mImageView.setImageBitmap(imageBitmap);
                    mImageLoaded = true;
                }
            }

            if(!mImageLoaded) {
                mImageView.setImageDrawable(getResources().getDrawable(R.drawable.error));
            }
        } else{
            ImageLoaderRequestQueue.getInstance(getApplicationContext()).getImageLoader().get(GenericPhotoApplication.getInstance().getImageApiUrl() + mImageMongoId, new ImageLoader.ImageListener() {
                @Override
                public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                    // if bitmap is null, it's a cache miss, and we want to keep the still-loading drawable
                    if (response.getBitmap() != null) {
                        Bitmap b = Utilities.rotateBitmap(response.getBitmap(), mImageRotation);
                        mImageView.setImageBitmap(b);
                    }
                }

                @Override
                public void onErrorResponse(VolleyError error) {
                    mImageView.setImageDrawable(getResources().getDrawable(R.drawable.error));
                }
            });
        }
    }


    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // This is a hack to fix broken home-as-up navigation: without it,
            // the parent activity is recreated and its saved state is discarded,
            // so it's missing photo details.
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
