package com.ncviewer.idea.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "NcViewerSettings", storages = [Storage("ncViewerSettings.xml")])
class NcViewerSettings : PersistentStateComponent<NcViewerSettings.State> {

    data class State(
        var excludeCodes: MutableList<String> = DEFAULT_EXCLUDE_CODES.toMutableList(),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getExcludedCodes(): List<String> = state.excludeCodes.ifEmpty { DEFAULT_EXCLUDE_CODES }

    companion object {
        private val DEFAULT_EXCLUDE_CODES = listOf(
            "G10",
            "G28",
            "G30",
            "G53",
            "G90",
            "M00",
            "M01",
            "M02",
            "M30",
        )

        fun getInstance(): NcViewerSettings =
            ApplicationManager.getApplication().getService(NcViewerSettings::class.java)
    }
}
