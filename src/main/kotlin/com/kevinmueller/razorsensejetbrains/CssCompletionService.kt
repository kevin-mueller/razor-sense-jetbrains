package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.projectsDataDir
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rider.projectView.workspace.*
import java.io.File

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

        for (project in projects) {
            val dependencies = project.childrenEntities.firstOrNull { p -> p.isDependenciesFolder() }

            val targetFrameworkFolder = dependencies?.childrenEntities?.firstOrNull { p -> p.isTargetFrameworkFolder() }

            val packageDependencies =
                targetFrameworkFolder
                    ?.childrenEntities?.firstOrNull { p -> p.isPackagesFolder() }
                    ?.childrenEntities ?: emptyList()

            val projectDependencies =
                targetFrameworkFolder?.childrenEntities?.firstOrNull { p -> p.isProjectsFolder() }?.childrenEntities

            // to get actual referenced project: projects.firstOrNull {p -> p.name == projectDependencies.first().name}.url
            
            val cssFiles = mutableListOf<String>()
            for (packageDependency in packageDependencies) {
                cssFiles.addAll(packageDependency.url?.subTreeFileUrls?.filter { x -> x.fileName.endsWith(".css") }
                    ?.map { x -> x.url } ?: emptyList())
            }
        }


        return arrayOf("test-1", "test-2")
    }
}   