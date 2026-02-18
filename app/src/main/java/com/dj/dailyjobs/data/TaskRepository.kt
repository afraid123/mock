package com.dj.dailyjobs.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao,
    private val categoryDao: CategoryDao
) {
    fun pendingTasks(): Flow<List<TaskEntity>> = taskDao.observeByCompletion(false)
    fun completedTasks(): Flow<List<TaskEntity>> = taskDao.observeByCompletion(true)
    fun categories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() == 0) {
            listOf("Study", "Office", "Home").forEach {
                categoryDao.insert(CategoryEntity(name = it))
            }
        }
    }

    suspend fun addCategory(name: String) {
        categoryDao.insert(CategoryEntity(name = name.trim()))
    }

    suspend fun addTask(task: TaskEntity): Long = taskDao.insert(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun getAllTasksNow(): List<TaskEntity> = taskDao.getAllNow()
}
