package com.sdgsystems.collector.photos.ui.activity;

import com.sdgsystems.collector.photos.data.model.Image;

/**
 * Created by bfriedberg on 8/17/17.
 */

public interface IImageSaverCallback {
    void imageSaved(Image image);
}
