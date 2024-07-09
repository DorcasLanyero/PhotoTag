package com.sdgsystems.collector.photos.ui.activity

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.reflect.TypeToken
import com.sdgsystems.blueloggerclient.SDGLog
import com.sdgsystems.collector.photos.GenericPhotoApplication
import com.sdgsystems.collector.photos.GenericPhotoApplication.TAG
import com.sdgsystems.collector.photos.R
import com.sdgsystems.collector.photos.data.model.ImageCategory
import com.sdgsystems.collector.photos.sync.ImageLoaderRequestQueue
import com.sdgsystems.collector.photos.sync.authorizedVolleyRequests.AuthorizedJsonArrayRequest
import kotlinx.coroutines.runBlocking

class CategoriesActivity: Activity() {

    fun retrieveCategories(context: Context) {
        val request = AuthorizedJsonArrayRequest(
            GenericPhotoApplication.getInstance().getImageCategoryUrl(),
            { response ->
                SDGLog.d(TAG, "Response: $response")
                val categoryListType = object : TypeToken<ArrayList<ImageCategory>>() {}.type
                val gson = ImageCategory.getGson()
                val imageCategories =
                    gson.fromJson<List<ImageCategory>>(response.toString(), categoryListType)

                SDGLog.d(TAG, "Pulled remote categories: $imageCategories")

                val filteredImageCategories = mutableListOf<ImageCategory>()

                for (category in imageCategories) {
                    if (!category.hidden) {
                        filteredImageCategories.add(category)
                    }
                }

                GenericPhotoApplication.getInstance().categories =
                    filteredImageCategories as ArrayList<ImageCategory>
                SDGLog.d(TAG, "Loaded ${filteredImageCategories.size} categories")
            },
            { error ->
                SDGLog.d(TAG, "Category Retrieval Error: ${error.message}")

                if (context is ImageSetupActivity) {
                    Toast.makeText(this, "Failed to get image categories", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        )

        val q = ImageLoaderRequestQueue.getInstance(context).requestQueue
        q.add(request)
    }

    fun addCat(
        view: View,
        mImageCategories: List<String>,
        updateImageCategories: (ImageCategory) -> Unit
    ) {
        var dialog: CustomListViewDialog? = null


        //Filter out already set categories
        val availableCategories = mutableListOf<ImageCategory>()
        for (category in GenericPhotoApplication.getInstance().categories) {
            if (!mImageCategories.contains(category.name)) {
                availableCategories.add(category)
            }
        }

        val items = mutableListOf<String>()
        for (category in availableCategories) {
            items.add(category.name)
        }

        val adapter = DataAdapter(
            availableCategories.toTypedArray(),
            items.toTypedArray(),
            updateImageCategories
        )
        SDGLog.d(TAG, "Added ${adapter.itemsList.size} to the categories adapter")


        dialog = CustomListViewDialog(view.context, adapter, availableCategories.toTypedArray())

        dialog.show()
    }

    class DataAdapter(
        var availableCategories: Array<ImageCategory>,
        var itemsList: Array<String>,
        private val updateImageCategories: (ImageCategory) -> Unit
    ) : RecyclerView.Adapter<DataAdapter.CategoryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, i: Int): CategoryViewHolder {

            val v = LayoutInflater.from(parent.context).inflate(R.layout.choice_textview, parent, false)
            return CategoryViewHolder(v)
        }

        override fun onBindViewHolder(categoryViewHolder: CategoryViewHolder, i: Int) {
            categoryViewHolder.mTextView.text = itemsList[i]
        }

        override fun getItemCount(): Int {
            SDGLog.d(TAG, "Adapter size: ${itemsList.size}")
            return availableCategories.size
        }

        inner class CategoryViewHolder(v: View) : RecyclerView.ViewHolder(v), View.OnClickListener {
            var mTextView: TextView

            init {
                mTextView = v as TextView
                mTextView.textSize = 16f
                mTextView.setPadding(16, 10, 10, 16)
                v.setOnClickListener(this)
            }

            override fun onClick(v: View) {
                val cat = availableCategories[this.adapterPosition]
                SDGLog.d(TAG, "Category $cat selected")
                runBlocking { updateImageCategories(cat) }
            }
        }
    }

    class CustomListViewDialog(
        var activity: Context,
        var adapter: DataAdapter,
        var categories: Array<ImageCategory>
    ) : Dialog(activity),
        View.OnClickListener {
        var dialog: Dialog? = null

        private var recyclerView: RecyclerView? = null
        private var mLayoutManager: RecyclerView.LayoutManager? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            //requestWindowFeature(Window.FEATURE_CUSTOM_TITLE)

            mLayoutManager = LinearLayoutManager(activity)
            (mLayoutManager as LinearLayoutManager).orientation = LinearLayoutManager.VERTICAL

            recyclerView = RecyclerView(activity)
            recyclerView?.layoutManager = mLayoutManager
            recyclerView?.adapter = adapter

            recyclerView?.setOnClickListener { dismiss() }

            // Create a SearchView object
            val searchView = SearchView(activity)

            // Add a listener for changes to the search query
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Filter the list of categories based on the new text
                    val filteredCategories = categories.filter { it.name.contains(newText ?: "", true) }
                    val filteredItems = filteredCategories.map { it.name }

                    SDGLog.d(TAG, "filer by $newText")


                    adapter.availableCategories = filteredCategories.toTypedArray()
                    adapter.itemsList = filteredItems.toTypedArray()
                    adapter.notifyDataSetChanged()

                    SDGLog.d(TAG, " ${filteredItems.size } items match filter")

                    return true
                }
            })

            //create custom dialog layout
            val customDialogView = LinearLayout(activity)
            customDialogView.orientation = LinearLayout.VERTICAL

            val titleTextView = TextView(activity)
            titleTextView.text = "Choose a Category..."
            titleTextView.setPadding(16, 16, 16, 16)
            titleTextView.textSize = 20f
            titleTextView.setTypeface(null, Typeface.BOLD)

            val negativeButton = Button(activity)
            negativeButton.text = "Cancel"
            negativeButton.setOnClickListener {
                dismiss()
            }

            customDialogView.addView(titleTextView)
            customDialogView.addView(searchView)
            customDialogView.addView(recyclerView)
            customDialogView.addView(negativeButton)

            setContentView(customDialogView)
        }

        override fun onClick(v: View) {
            dismiss()
        }
    }
}
