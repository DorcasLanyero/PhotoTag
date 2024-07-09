package com.sdgsystems.collector.photos.tasks
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sdgsystems.blueloggerclient.SDGLog
import com.sdgsystems.collector.photos.Constants
import com.sdgsystems.collector.photos.GenericPhotoApplication
import com.sdgsystems.collector.photos.ui.activity.ImageSetupActivity
import java.util.concurrent.TimeUnit


object TagListCleaner {
    private val workManager = WorkManager.getInstance(GenericPhotoApplication.getInstance().applicationContext)
    private const val tag = "TagListCleaner"
    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(
        GenericPhotoApplication.getInstance().applicationContext
    )

    // Resets the timer for activity by canceling and rescheduling the worker
    private fun resetTimer() {
        // re-read the values in case they changed!
        // timer will start the first call (when the user takes a photo or sets a tag)
        var delayDuration = preferences.getString(Constants.PREF_CLEAR_TAGS_TIMEOUT, "120").toString().toLong()
        if (delayDuration < 30) {
            delayDuration = 30
        }
        if (preferences.getBoolean(Constants.PREF_ENABLE_CLEAR_TAGS, false)) {
            val workRequest = OneTimeWorkRequestBuilder<TagListCleanerWorker>()
                .setInitialDelay(delayDuration, TimeUnit.SECONDS)
                .addTag(tag)
                .build()
            workManager.cancelAllWorkByTag(tag)
            workManager.enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }

    // Checks if we need to clean up the tags
    private fun shouldCleanTags(): Boolean {
        // Check if PREF_ENABLE_CLEAR_TAGS is set to true; don't store, as pref may have changed
        if (!preferences.getBoolean(Constants.PREF_ENABLE_CLEAR_TAGS, false)) {
            return false
        }

        // Might need to add more logic later?
        return true
    }

    // Worker that cleans up the preference ArrayList for the Tags
    class TagListCleanerWorker(context: Context, workerParams: WorkerParameters) :
        Worker(context, workerParams) {

        override fun doWork(): Result {
            if (shouldCleanTags()) {
                clearPreferenceTags()
                clearTags.postValue(true)
                SDGLog.d(tag, "cleared tags!")
            }
            return Result.success()
        }


        // Clears the tag ArrayList
        private fun clearPreferenceTags() {
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val tags = ArrayList(preferences.getStringSet(ImageSetupActivity.PREF_DEFAULT_TAGS, HashSet())!!)
            tags!!.clear()
            val editor = preferences.edit()
            editor.putStringSet(ImageSetupActivity.PREF_DEFAULT_TAGS, HashSet(tags))
            editor.commit()
        }
    }

    private val clearTags = MutableLiveData<Boolean>()
    val getClearTags: LiveData<Boolean> = clearTags

    fun setTagActivity() {
        SDGLog.d(tag, "Tag activity. resetTimer()")
        // only use the timer if we're supposed to clean up tags
        if (shouldCleanTags()) {
            resetTimer()
        }
    }
}