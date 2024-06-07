package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.projectsDataDir
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.toPath
import com.jetbrains.rider.projectView.workspace.*
import java.io.File
import kotlin.io.path.listDirectoryEntries

@Service(Service.Level.PROJECT)
class CssCompletionService(private val solutionProject: Project) {
    fun getCompletions(): Array<String> {
        //WorkspaceModel.getInstance(solutionProject).findProjects()[1].childrenEntities.filter { x -> x.isDependenciesFolder() }.first().childrenEntities.first().childrenEntities[3].childrenEntities[1].getLocationForItems().presentableUrl

        // 1. for each solutionProject
        //      find index.html / index.cshtml
        //      get .css file references
        //      
        //      get solutionProject package dependencies
        //      get solutionProject "solutionProject" dependencies
        //      
        // 2. check dependencies, if .css references match (folder name + file name)
        //      if yes => parse and store suggestions in dictionary (key is solutionProject)
        //      if no => try to find .css file in current solutionProject folder and parse + store in dictionary

        val projects = WorkspaceModel.getInstance(solutionProject).findProjects()

        WorkspaceModel.getInstance(solutionProject).findProjects().first().getAllNestedFilesAndThis()
        
        val cssFilesByProject = mutableMapOf<ProjectModelEntity, List<String>>()
        for (project in projects) {
            cssFilesByProject[project] = getCssFilesFromProject(project)
        }


        return arrayOf("test-1", "test-2")
    }

    private fun getCssFilesFromProject(project: ProjectModelEntity): List<String> {
        if (project.url == null)
            return emptyList()

        val cssFiles = mutableListOf<String>()
        File(project.url!!.parent!!.presentableUrl).walkTopDown().forEach {
            if (it.extension == "css") {
                cssFiles.add(it.path)
            }
        }

        val packageDependencies = getPackageDependencies(project)
        if (packageDependencies.isNotEmpty()) {
            cssFiles.addAll(getCssFilesFromPackageDependencies(packageDependencies))
        }

        val projectDependencies = getProjectDependencies(project)
        if (projectDependencies.isNotEmpty()) {
            for (projectDependency in projectDependencies)
                cssFiles.addAll(getCssFilesFromProject(projectDependency))
        }

        return cssFiles
    }

    private fun getProjectDependencies(project: ProjectModelEntity): List<ProjectModelEntity> {
        return getTargetFrameworkFolder(project)?.childrenEntities?.firstOrNull { p -> p.isProjectsFolder() }?.childrenEntities
            ?: emptyList()
    }

    private fun getPackageDependencies(project: ProjectModelEntity): List<ProjectModelEntity> {
        return getTargetFrameworkFolder(project)
            ?.childrenEntities?.firstOrNull { p -> p.isPackagesFolder() }
            ?.childrenEntities ?: emptyList()
    }

    private fun getTargetFrameworkFolder(project: ProjectModelEntity): ProjectModelEntity? {
        val dependencies = project.childrenEntities.firstOrNull { p -> p.isDependenciesFolder() }

        return dependencies?.childrenEntities?.firstOrNull { p -> p.isTargetFrameworkFolder() }
    }

    private fun getCssFilesFromPackageDependencies(packageDependencies: List<ProjectModelEntity>): List<String> {
        val cssFiles = mutableListOf<String>()
        for (packageDependency in packageDependencies) {
            cssFiles.addAll(packageDependency.url?.subTreeFileUrls?.filter { x -> x.fileName.endsWith(".css") }
                ?.map { x -> x.presentableUrl } ?: emptyList())
        }

        return cssFiles
    }
}   