package com.sdgsystems.collector.photos.data.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import androidx.annotation.Nullable;

/**
 * Created by jay on 5/16/17.
 */

public class ImageCategory implements Comparable {

    @Expose
    public String id;

    @Expose
    public String name;

    @Expose
    public String description;

    @Expose
    public Boolean hidden;

    @Expose
    public String parent_id;

    @Override
    public String toString() {
        return new Gson().toJson(this, this.getClass());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return (obj != null && obj.toString().equals(toString()));
    }

    public static Gson getGson() {
        GsonBuilder gb = new GsonBuilder();
        gb.excludeFieldsWithoutExposeAnnotation();
        return gb.create();
    }

    @Override
    public int compareTo(Object o) {
        return name.compareTo(((ImageCategory) o).name);
    }
}