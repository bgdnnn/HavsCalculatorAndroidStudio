package com.homeclouds.havs.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.homeclouds.havs.data.HavsStore
import com.homeclouds.havs.data.ToolsJsonStore

class HavsViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val havsStore = HavsStore(appContext)
        val toolsStore = ToolsJsonStore(appContext)
        @Suppress("UNCHECKED_CAST")
        return HavsViewModel(havsStore, toolsStore) as T
    }
}