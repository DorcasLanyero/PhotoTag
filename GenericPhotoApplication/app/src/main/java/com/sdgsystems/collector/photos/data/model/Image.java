package com.sdgsystems.collector.photos.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.annotation.NonNull;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.sdgsystems.blueloggerclient.SDGLog;
import com.sdgsystems.collector.photos.data.dao.ImageDao;
import com.sdgsystems.collector.photos.data.dao.ImageFileDao;
import com.sdgsystems.synchableapplication.SynchronizableModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by jay on 5/16/17.
 */

@Entity
public class Image extends SynchronizableModel {
    private static final String TAG = "Image";
    static SimpleDateFormat rfc3339Date = new SimpleDateFormat("yyyy-MM-dd");
    static SimpleDateFormat rfc3339Time = new SimpleDateFormat("HH:mm:ss");

    public boolean pendingUpload = false;

    @Ignore
    private static Gson imageGson = null;

    @Ignore
    private boolean isInflated = false;

    @Expose
    @ColumnInfo(name = "mongo_id")
    @SerializedName("id")
    public String mongo_id = null;

    @Expose
    @Ignore
    public List<String> owners;

    @Expose
    @Ignore
    public Date timestamp;

    @Expose
    @Ignore
    public List<ImageCategory> categories;

    @Expose
    @Ignore
    public String annotation;

    @Expose
    @Ignore
    public List<String> tags;

    @Expose
    @Ignore
    public Location location;

    @Expose
    @Ignore
    public Map<String, Datapoint> exif_data;

    @Expose
    @Ignore
    public List<CloudPhoto> cloud_photos;

    @Expose
    @Ignore
    public String uploader = "";

    @Expose
    @Ignore
    public String uploaderName = "Unknown Uploader";

    @ColumnInfo(name = "json")
    public String json_representation;

    public Image (Image image) {

        if(image.mongo_id != null) {
            this.mongo_id = new String(image.mongo_id);
        }


        this.owners = new ArrayList<>();
        for(String  owner : image.owners) {
            this.owners.add(owner);
        }

        this.timestamp = new Date(image.timestamp.getTime());

        this.categories = new ArrayList<>();
        for(ImageCategory category : image.categories) {
            this.categories.add(category);
        }

        if(image.annotation != null) {
            this.annotation = new String(image.annotation);
        }

        this.tags = new ArrayList<>();
        for(String tag : image.tags) {
            this.tags.add(tag);
        }

        this.location = image.location;

        this.cloud_photos = new ArrayList<>();

        if(image.cloud_photos != null) {
            for (CloudPhoto cloud_photo : image.cloud_photos) {
                this.cloud_photos.add(cloud_photo);
            }
        }

        if(image.json_representation != null) {
            this.json_representation = new String(image.json_representation);
        }

        if(image.exif_data != null) {
            this.exif_data = new HashMap<>(image.exif_data);
        }
    }

    public Image() {
        owners = new ArrayList<String>();
        tags = new ArrayList<String>();
        categories = new ArrayList<ImageCategory>();

        timestamp = new Date();

//        SDGLog.d(TAG, "Setting timestamp to " + timestamp.toString());
//        SDGLog.d(TAG, "Zulu timestamp is " + getZuluTimeStamp(timestamp));
    }

    public String toString() {
        return Image.getGson().toJson(this);
    }

    /**
     * Call before save()
     */
    public void compressToDatabase() {
        json_representation = toString();
        SDGLog.d(TAG, "Compressing to db: " + json_representation);
    }


    /**
     * Call after load()
     */
    public void inflateFromDatabase() {

        if(json_representation != null && !isInflated) {
            SDGLog.d(TAG, "Inflating " + json_representation);
            Image temp = Image.getGson().fromJson(json_representation, Image.class);
            mongo_id = temp.mongo_id;
            owners = temp.owners;
            timestamp = temp.timestamp;
            annotation = temp.annotation;
            tags = temp.tags;
            location = temp.location;
            cloud_photos = temp.cloud_photos;
            categories = temp.categories;
            exif_data = temp.exif_data;
            if(temp.categories == null) {
                SDGLog.d(TAG, "No categories set");
            } else{
                SDGLog.d(TAG, "categories: " + ImageCategory.getGson().toJson(temp.categories));
            }
            isInflated = true;
        }
    }

    public android.location.Location getLocation() {
        android.location.Location l = new android.location.Location("database");

        // location.coordinates is GeoJSON, so Lon-Lat
        l.setLongitude(location.coordinates.get(0));
        l.setLatitude(location.coordinates.get(1));

        return l;
    }

    public void ensureCategory(ImageCategory category) {
        if(categories == null) categories = new ArrayList<>();

        boolean hasCategory = false;
        for(ImageCategory c : categories) {
            if(c.id.equals(category.id)) {
                hasCategory = true;
            }
        }

        if(!hasCategory) {
            categories.add(category);
        }
    }

    public String getFormattedTimestamp() {
        String details = "";

/*
        for(int index = 0; index < owners.size(); index++) {
            details += owners.get(index);

            if(index < owners.size() - 1) {
                details += ", ";
            }
        }
*/

        rfc3339Date.setTimeZone(TimeZone.getDefault());
        rfc3339Time.setTimeZone(TimeZone.getDefault());


        details += rfc3339Date.format(timestamp) + " " + rfc3339Time.format(timestamp);

        return details;
    }

    public void deleteLocalImage(ImageDao dao, ImageFileDao fileDao) {
        if(getUid() != null && getUid() != -1) {
            ImageFile imageFile = dao.getImageFile(getUid());
            if(imageFile != null) {
                SDGLog.d(TAG, "Deleting image " + this.getUid());
                imageFile.deleteLocalFile();
                fileDao.delete(imageFile);
                dao.delete(this);
            } else {
                SDGLog.d(TAG, "Missing local file for " + this.getUid() + "!");
                dao.delete(this);
            }
        }
    }

    public int getExifRotation() {
        if(exif_data == null) return 1;
        if(!exif_data.containsKey("orientation")) return 1;
        if(!exif_data.get("orientation").isInt()) return 1;
        if(exif_data.get("orientation").asInt() == 0) return 1;

        return exif_data.get("orientation").asInt();
    }

    // Serialize and deserialize
    public static class DateSerializer implements JsonSerializer<Date>, JsonDeserializer<Date> {

        private Gson gson;

        public DateSerializer(Gson gson) {
            this.gson = gson;
        }

        @Override
        public JsonElement serialize(Date src, Type typeOfSrc, JsonSerializationContext context) {
            if(src == null) return null;

            String rfc3339 = getZuluTimeStamp(src);

            return context.serialize(rfc3339);
        }

        @Override
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            SimpleDateFormat rfc3339 = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");

            rfc3339.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date d = null;
            try {
                d = rfc3339.parse(json.getAsString().replace("Z", "").replace("T", "-"));
            } catch (ParseException e) {
                d = null;
            }

            return d;
        }
    }

    @NonNull
    private static String getZuluTimeStamp(Date src) {
        rfc3339Date.setTimeZone(TimeZone.getTimeZone("UTC"));
        rfc3339Time.setTimeZone(TimeZone.getTimeZone("UTC"));

        String rfc3339 = "";

        rfc3339 += rfc3339Date.format(src);
        rfc3339 += "T";
        rfc3339 += rfc3339Time.format(src);
        rfc3339 += "Z";
        return rfc3339;
    }

    public static Gson getGson() {
        if (imageGson == null) {
            Gson gson = new Gson();
            GsonBuilder gb = new GsonBuilder();
            gb.registerTypeAdapter(Date.class, new Image.DateSerializer(gson));
            gb.excludeFieldsWithoutExposeAnnotation();
            imageGson = gb.create();
        }
        return imageGson;
    }

    /**
     * We need to wrap up the model sync string method in a custom object so that it gets uploaded properly
     * @return
     */
    @Override
    public String getModelSynchString() {
        String syncstring = "";

        super.getModelSynchString();

        String currentSyncString = getGson().toJson(this);

        JSONObject object = new JSONObject();

        SDGLog.d(TAG, "using Synch String: " + currentSyncString);

        try {
            object.put("photo", new JSONObject(currentSyncString));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        syncstring = object.toString();

        return syncstring;
    }

    public static class Location {
        @Expose
        String type = "Point";

        @Expose
        public List<Double> coordinates;
    }

    public static class CloudPhoto {
        @Expose
        int version;

        @Expose
        String format;

        @Expose
        int bytes;

        @Expose
        Size size;
    }

    public static class Size {
        @Expose
        int X;

        @Expose
        int Y;
    }

    public static class Datapoint {
        @Expose
        public String type;

        @Expose
        public Object value;

        public Datapoint() { }

        public Datapoint(int value) {
            this.type = "integer";
            this.value = value;
        }

        public int asInt() {
            if(isInt()) {
                // Gson turns all numeric types into doubles
                if(value instanceof Double) {
                    return ((Double) value).intValue();
                }
                else if(value instanceof Integer) {
                    return (int) value;
                }
            }
            else {
                throw new IllegalStateException();
            }
            throw new IllegalStateException();
        }

        public boolean isInt() {
            return type.toLowerCase().equals("integer");
        }

        public String toString() {
            return value.toString();
        }
    }
}
