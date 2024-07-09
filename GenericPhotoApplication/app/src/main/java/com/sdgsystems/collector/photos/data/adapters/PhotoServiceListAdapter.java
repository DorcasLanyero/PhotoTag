package com.sdgsystems.collector.photos.data.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import com.google.android.flexbox.FlexboxLayout;

import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.GenericPhotoApplication;
import com.sdgsystems.collector.photos.R;
import com.sdgsystems.collector.photos.Utilities;
import com.sdgsystems.collector.photos.ui.activity.ISearchableActivityCallback;
import com.sdgsystems.collector.photos.data.model.Image;
import com.sdgsystems.collector.photos.data.model.ImageCategory;
import com.sdgsystems.collector.photos.data.model.ImageFile;
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue;

import java.io.File;
import java.io.LineNumberReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipListener;

/**
 * Created by jay on 5/23/17.
 */

public class PhotoServiceListAdapter implements ListAdapter {
    private static final String TAG = "PhotoServiceListAdapter";
    private Set<DataSetObserver> mObservers = new HashSet<>();
    private List<Image> mImages = new ArrayList<>();
    private Context mContext;
    private ISearchableActivityCallback mSearchableActivityCallback = null;

    public PhotoServiceListAdapter(Context context, String urlRoot) {
        mContext = context;
    }

    public void setImages(List<Image> images) {
        if(images != null) {
            SDGLog.d("Adapter", "Set images: " + images.size());
            mImages = images;

            for(DataSetObserver o : mObservers) {
                o.onChanged();
            }
        }
    }

    public void setISearchableActivityCallback(ISearchableActivityCallback callback) {
        mSearchableActivityCallback = callback;
    }

    public void addImage(Image i) {
        mImages.add(i);

        for(DataSetObserver o : mObservers) {
            o.onChanged();
        }
    }

    public void removeImage(Image i) {
        mImages.remove(i);

        for(DataSetObserver o : mObservers) {
            o.onChanged();
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {
        mObservers.add(dataSetObserver);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {
        mObservers.remove(dataSetObserver);
    }

    @Override
    public int getCount() {
        return mImages.size();
    }

    @Override
    public Object getItem(int i) {
        return mImages.get(i);
    }

    @Override
    public long getItemId(int i) {
        if(mImages != null && mImages.size() > i) {
            Image mImage = mImages.get(i);

            if(mImage.mongo_id != null) {
                BigInteger numericalValue = new BigInteger(mImage.mongo_id, 16);
                return numericalValue.longValue(); //truncates to 8 bytes instead of the 12-byte mongo ID, but should still be unique.
            } else {
                return mImage.getUid();
            }
        } else {
            return -1;
        }
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    static class ImageViewHolder  {

        public ImageView imageView;
        public ScrollView scrollView;
        ImageView uploadIconView;
        ImageLoader.ImageContainer container;
        TextView metaText;
        ImageView icoComment;
        LinearLayout linearTag;
        LinearLayout linearCat;
        String URL;
    }


    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {

        final ImageViewHolder viewHolder;

        final Image image = (Image) getItem(i);

        if (convertView == null) {
            viewHolder = new ImageViewHolder();

            //view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_view_item, viewGroup, false);
            convertView = convertView.inflate(viewGroup.getContext(), R.layout.list_view_item, null);

            viewHolder.imageView = (ImageView) convertView.findViewById(R.id.non_network_image_view);
            viewHolder.imageView.setImageResource(R.drawable.ellipsis_small);
            viewHolder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

            viewHolder.metaText = (TextView) convertView.findViewById(R.id.meta_text);
            viewHolder.icoComment = (ImageView) convertView.findViewById(R.id.icoComment);
            viewHolder.linearTag = (LinearLayout) convertView.findViewById(R.id.tagCloud);
            viewHolder.linearCat= (LinearLayout) convertView.findViewById(R.id.categoryCloud);

            viewHolder.uploadIconView = convertView.findViewById(R.id.image_uploading_icon_view);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ImageViewHolder) convertView.getTag();
            viewHolder.imageView.setImageResource(R.drawable.ellipsis_small);
            viewHolder.container.cancelRequest();
        }

        //String URL = GenericPhotoApplication.getInstance().getImageApiUrl() + image.mongo_id + "/thumb";
        viewHolder.URL = GenericPhotoApplication.getInstance().getImageApiUrl() + image.mongo_id + "/mini";

        if(image.pendingUpload) {
            SDGLog.d(TAG, "Image is a local image, pulling the filename");

            ImageFile imageFileObject = GenericPhotoApplication.getInstance().getDb().imageDao().getImageFile(image.getUid());
            if(imageFileObject != null) {
                File imgFile = new File(imageFileObject.filename);
                if (imgFile.exists()) {

                    SDGLog.d(TAG, "Filename is " + imgFile.getAbsolutePath());

                    //We are going to hack in a local request w/ a network image view
                    viewHolder.URL = "file://" + imgFile.getAbsolutePath() + "_thumb";
                }
            } else {
                viewHolder.URL = null;
            }

            viewHolder.uploadIconView.setVisibility(View.VISIBLE);
        }
        else {
            viewHolder.uploadIconView.setVisibility(View.GONE);
        }

        viewHolder.metaText.setText(image.getFormattedTimestamp());
        if (image.annotation !=null && (image.annotation!="" && ! image.annotation.matches("^\\s*$"))){
            viewHolder.icoComment.setVisibility(View.VISIBLE);
        }
        else {
            viewHolder.icoComment.setVisibility(View.INVISIBLE);
        }
        viewHolder.linearTag.removeAllViews();
        viewHolder.linearCat.removeAllViews();

        if(image.pendingUpload && image.tags != null && !image.tags.contains(mContext.getString(R.string.pending_tag))) {
            image.inflateFromDatabase();
            //image.tags.add(mContext.getString(R.string.pending_tag));
        }

        updateChips(viewHolder.linearTag, "#aa81c784", "#000000", image.tags, ChipCloud.SelectMode.single, 0);

        if(image.categories != null && image.categories.size() > 0) {
            ArrayList<String> categoryList = new ArrayList<String>();
            for(ImageCategory category : image.categories) {
                categoryList.add(category.name);
            }

            updateChips(viewHolder.linearCat, "#aa64b5f6", "#000000", categoryList, ChipCloud.SelectMode.single, 0);
        }

        final View finalView = convertView;

        if(viewHolder.URL == null) {
            viewHolder.imageView.setImageDrawable(finalView.getContext().getResources().getDrawable(R.drawable.error));
            SDGLog.d(TAG, "Couldn't find image for image");
        } else {
            ImageLoader loader = ImageLoaderRequestQueue.getInstance(convertView.getContext().getApplicationContext()).getImageLoader();
            ImageLoader.ImageContainer container = loader.get(viewHolder.URL, new ImageLoader.ImageListener() {
                @Override
                public void onResponse(final ImageLoader.ImageContainer response, boolean isImmediate) {
                    // if bitmap is null, it's a cache miss, and we want to keep the still-loading drawable
                    SDGLog.d(TAG, "Photo response: immediate? " + isImmediate);

                    if(response.getBitmap() != null) {
                        final Bitmap bitmap = response.getBitmap();

                        boolean shouldScale = true;

                        if(shouldScale) {

                            AsyncTask<Bitmap, Void, Bitmap> task = new ViewHolderUpdateTask(mContext, viewHolder, image);
                            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, bitmap);

                        } else {
                            Bitmap rotatedBitmap = Utilities.rotateBitmap(bitmap, image);
                            viewHolder.imageView.setImageBitmap(rotatedBitmap);
                        }
                    }
                }


                @Override
                public void onErrorResponse(VolleyError error) {
                    viewHolder.imageView.setImageDrawable(finalView.getContext().getResources().getDrawable(R.drawable.error));
                }
            });

            viewHolder.container = container;


        }

        return convertView;
    }

    private static class ViewHolderUpdateTask extends AsyncTask<Bitmap, Void, Bitmap> {
        private int height;
        private int width;
        private ImageViewHolder viewHolder;
        private Image metadata;
        private Context context;

        public ViewHolderUpdateTask(Context c, ImageViewHolder vh, Image image) {
            context = c;
            viewHolder = vh;
            metadata = image;
            height = viewHolder.imageView.getHeight();
        }

        @Override
        protected Bitmap doInBackground(Bitmap... bitmaps) {
            // Height should be 150dp, converted to pixels
            if (height == 0) height = (int) (150 * context.getResources().getDisplayMetrics().density);
            Bitmap bitmap = Utilities.rotateBitmap(bitmaps[0], metadata);
            width = (height * bitmap.getWidth()) / bitmap.getHeight();
            return Bitmap.createScaledBitmap(bitmap, width, height, true);
        }

        protected void onPostExecute(Bitmap result) {
            if(result != null) {
                viewHolder.imageView.setImageBitmap(result);
                SDGLog.d(TAG, "onResponse: "+viewHolder.imageView.getWidth() + "," + result.getWidth());
                viewHolder.icoComment.setX((viewHolder.imageView.getWidth() - result.getWidth())/2);
            }
        }
    };

    /**
     * Add a chipcloud to the specified flexbox
     * @param flexBox
     * @param backgroundColor
     * @param textColor
     * @param chipList The String List of chip names to add
     * @param mode The select mode (none, single, multi)
     * @param offset The number of chips that are already IN the flexbox (used to identify a chip when selected...)
     */
    private void updateChips(LinearLayout flexBox, final String backgroundColor, final String textColor, final List<String> chipList, ChipCloud.SelectMode mode, final int offset) {
        SDGLog.d(TAG, "update chips: " + chipList);

        ChipCloudConfig config = new ChipCloudConfig()
                .selectMode(mode)
                .uncheckedChipColor(Color.parseColor(backgroundColor))
                .uncheckedTextColor(Color.parseColor(textColor))
                .useInsetPadding(true);

        final ChipCloud chipCloud = new ChipCloud(mContext, flexBox, config);

        chipCloud.setListener(new ChipListener() {
            @Override
            public void chipCheckedChange(int i, boolean b, boolean b1) {
                SDGLog.d(TAG, i + " " + b + " " + b1 + " offset: " + offset);
                if(chipList != null && (i - offset) < chipList.size() && b) {
                    SDGLog.d(TAG, "chip was " + chipList.get(i - offset));

                    if(mSearchableActivityCallback != null && b) {
                        mSearchableActivityCallback.search(chipList.get(i - offset));
                    }
                } else if(b){
                    SDGLog.d(TAG, "clicked index " + i + " the list is " + chipList.size() + " long with an offset of " + offset);
                }
            }
        });

        if(chipList != null) {
            chipCloud.addChips(chipList);
        }
    }


    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }
}
