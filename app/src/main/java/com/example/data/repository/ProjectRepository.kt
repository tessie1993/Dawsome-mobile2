package com.example.data.repository

import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Long): ProjectEntity? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(id)
    }

    suspend fun saveProject(project: ProjectEntity): Long = withContext(Dispatchers.IO) {
        if (project.id > 0) {
            projectDao.updateProject(project)
            project.id
        } else {
            projectDao.insertProject(project)
        }
    }

    suspend fun insertProject(project: ProjectEntity): Long = withContext(Dispatchers.IO) {
        projectDao.insertProject(project)
    }

    suspend fun updateProject(project: ProjectEntity) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProjectById(id: Long) = withContext(Dispatchers.IO) {
        projectDao.deleteProjectById(id)
    }

    suspend fun getProjectCount(): Int = withContext(Dispatchers.IO) {
        projectDao.getProjectCount()
    }
}
