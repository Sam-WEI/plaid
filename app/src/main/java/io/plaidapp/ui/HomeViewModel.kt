/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.plaidapp.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.plaidapp.core.data.DataManager
import io.plaidapp.core.data.Source
import io.plaidapp.core.data.prefs.SourcesRepository
import io.plaidapp.core.designernews.data.login.LoginRepository
import io.plaidapp.core.ui.filter.FiltersChangedCallback
import io.plaidapp.core.ui.filter.SourceUiModel
import java.util.Collections

class HomeViewModel(
    val dataManager: DataManager,
    private val loginRepository: LoginRepository,
    private val sourcesRepository: SourcesRepository
) : ViewModel() {

    // TODO keeping this one temporarily, until we deal with [FeedAdapter]
    private val _sourceRemoved = MutableLiveData<Source>()
    val sourceRemoved: LiveData<Source>
        get() = _sourceRemoved

    private val _sources = MutableLiveData<List<SourceUiModel>>()
    val sources: LiveData<List<SourceUiModel>>
        get() = _sources

    // listener for notifying adapter when data sources are deactivated
    private val filtersChangedCallbacks = object : FiltersChangedCallback() {
        override fun onFiltersChanged(changedFilter: Source) {
            if (!changedFilter.active) {
                _sourceRemoved.value = changedFilter
            }
        }

        override fun onFilterRemoved(removed: Source) {
            _sourceRemoved.value = removed
        }

        override fun onFiltersUpdated(sources: List<Source>) {
            Log.d("flo", "filters updated $sources")
            updateSources(sources)
        }
    }

    init {
        sourcesRepository.registerFilterChangedCallback(filtersChangedCallbacks)
        getSources()
    }

    fun isDesignerNewsUserLoggedIn() = loginRepository.isLoggedIn

    fun logoutFromDesignerNews() {
        loginRepository.logout()
    }

    fun loadData() {
        dataManager.loadAllDataSources()
    }

    override fun onCleared() {
        dataManager.cancelLoading()
        super.onCleared()
    }

    fun addSources(query: String, isDribbble: Boolean, isDesignerNews: Boolean){
        val filters = sources.value ?: return

        val sources = mutableListOf<Source>()
        if(isDribbble){
            sources.add(Source.DribbbleSearchSource(query, true))
        }
        if(isDesignerNews){
            sources.add(Source.DesignerNewsSearchSource(query, true))
        }
        sources.forEach { toAdd ->
            // first check if it already exists
            for (i in 0 until filters.size) {
                val existing = filters[i]
                if (existing.source.javaClass == toAdd.javaClass &&
                        existing.source.key.equals(toAdd.key, ignoreCase = true)
                ) {
                    // already exists, just ensure it's active
                    if (!existing.source.active) {
                        sourcesRepository.changeSourceActiveState(existing.source)
                    }
                    sources.remove(toAdd)
                }
            }
        }
        // didn't already exist, so add it
        sourcesRepository.addSources(sources)
    }

    private fun getSources() {
        updateSources(sourcesRepository.getSources())
    }

    private fun updateSources(sources: List<Source>) {
        val mutableSources = sources.toMutableList()
        Collections.sort(mutableSources, Source.SourceComparator())
        _sources.value = mutableSources.map {
            SourceUiModel(
                    it,
                    { source -> sourcesRepository.changeSourceActiveState(source) },
                    { source ->
                        if (source.isSwipeDismissable) {
                            sourcesRepository.removeSource(source)
                        }
                    }
            )
        }
    }
}