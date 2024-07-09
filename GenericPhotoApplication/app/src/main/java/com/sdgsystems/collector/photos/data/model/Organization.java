package com.sdgsystems.collector.photos.data.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

/**
 * Created by bfriedberg on 10/4/17
 */

public class Organization {

    @Expose
    public String id;

    @Expose
    public String name;

    @Expose
    public String description;

    @Override
    public String toString() {
        return new Gson().toJson(this, this.getClass());
    }

    public static Gson getGson() {
        GsonBuilder gb = new GsonBuilder();
        gb.excludeFieldsWithoutExposeAnnotation();
        return gb.create();
    }
}