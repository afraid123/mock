package com.dj.dailyjobs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dj.dailyjobs.DJApp
import com.dj.dailyjobs.data.CategoryEntity
import com.dj.dailyjobs.data.TaskEntity
import com.dj.dailyjobs.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskUiState(
    val pending: List<TaskEntity> = emptyList(),
    val completed: List<TaskEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val search: String = "",
    val selectedCategoryId: Long? = null,
    val showCompleted: Boolean = false
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DJApp
    private val repo = app.repository
    private val scheduler = ReminderScheduler(application)

    private val search = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val showCompleted = MutableStateFlow(false)

    val uiState: StateFlow<TaskUiState> = combine(
        repo.pendingTasks(),
        repo.completedTasks(),
        repo.categories(),
        search,
        selectedCategoryId,
        showCompleted
    ) { pending, completed, cats, q, selectedCat, completedTab ->
        val pendingFiltered = pending.filter {
            (selectedCat == null || it.categoryId == selectedCat) &&
                (q.isBlank() || it.text.contains(q, ignoreCase = true))
        }
        val completedFiltered = completed.filter {
            (selectedCat == null || it.categoryId == selectedCat) &&
                (q.isBlank() || it.text.contains(q, ignoreCase = true))
        }
        TaskUiState(
            pending = pendingFiltered,
            completed = completedFiltered,
            categories = cats,
            search = q,
            selectedCategoryId = selectedCat,
            showCompleted = completedTab
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskUiState())

    init {
        viewModelScope.launch { repo.ensureDefaultCategories() }
    }

    fun setSearch(q: String) { search.value = q }
    fun setCategoryFilter(id: Long?) { selectedCategoryId.value = id }
    fun showCompleted(show: Boolean) { showCompleted.value = show }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addCategory(name) }
    }

    fun addTask(text: String, categoryId: Long, reminderAt: Long?, repeatMinutes: Long?) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val id = repo.addTask(
                TaskEntity(
                    text = text.trim(),
                    categoryId = categoryId,
                    reminderAtMillis = reminderAt,
                    repeatIntervalMinutes = repeatMinutes
                )
            )
            repo.getTask(id)?.let { scheduler.schedule(it) }
        }
    }

    fun markDone(task: TaskEntity) {
        viewModelScope.launch {
            scheduler.cancel(task.id)
            repo.updateTask(task.copy(isCompleted = true, completedAtMillis = System.currentTimeMillis()))
        }
    }

    fun restore(task: TaskEntity) {
        viewModelScope.launch {
            val restored = task.copy(isCompleted = false, completedAtMillis = null)
            repo.updateTask(restored)
            scheduler.schedule(restored)
        }
    }
}
