package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.projectDataStore by preferencesDataStore(name = "saved_projects_store")

class ProjectRepository(private val context: Context) {

    private val projectsJsonKey = stringPreferencesKey("saved_projects_json")
    private val firstLaunchKey = booleanPreferencesKey("first_launch")

    val isFirstLaunch: Flow<Boolean> = context.projectDataStore.data.map { prefs ->
        prefs[firstLaunchKey] ?: true
    }

    val savedProjects: Flow<List<AppProject>> = context.projectDataStore.data.map { prefs ->
        val rawJson = prefs[projectsJsonKey].orEmpty()
        parseProjectsJson(rawJson)
    }

    suspend fun setFirstLaunchCompleted() {
        context.projectDataStore.edit { prefs ->
            prefs[firstLaunchKey] = false
        }
    }

    suspend fun saveProject(project: AppProject) {
        context.projectDataStore.edit { prefs ->
            val currentList = parseProjectsJson(prefs[projectsJsonKey].orEmpty())
            val updatedList = if (currentList.any { it.id == project.id }) {
                currentList.map { if (it.id == project.id) project else it }
            } else {
                listOf(project) + currentList
            }
            prefs[projectsJsonKey] = serializeProjectsJson(updatedList)
        }
    }

    suspend fun deleteProject(projectId: String) {
        context.projectDataStore.edit { prefs ->
            val currentList = parseProjectsJson(prefs[projectsJsonKey].orEmpty())
            val updatedList = currentList.filterNot { it.id == projectId }
            prefs[projectsJsonKey] = serializeProjectsJson(updatedList)
        }
    }

    suspend fun clearAllProjects() {
        context.projectDataStore.edit { prefs ->
            prefs[projectsJsonKey] = "[]"
        }
    }

    internal fun parseProjectsJson(rawJson: String): List<AppProject> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            (0 until array.length()).mapNotNull { idx ->
                array.optJSONObject(idx)?.let { AppProject.fromJsonObject(it) }
            }
        }.getOrDefault(emptyList())
    }

    internal fun serializeProjectsJson(projects: List<AppProject>): String {
        val array = JSONArray()
        projects.forEach { project ->
            array.put(project.toJsonObject())
        }
        return array.toString()
    }
}
