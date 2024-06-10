package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rider.projectView.workspace.*
import org.jsoup.Jsoup
import java.io.File

@Service(Service.Level.PROJECT)
class CssCompletionService(private val solutionProject: Project) {
    var cssCompletionItemsByProjectPath: Map<String, CssCompletionItem> = emptyMap()

    fun loadCompletions() {
        val projects = WorkspaceModel.getInstance(solutionProject).findProjects()

        WorkspaceModel.getInstance(solutionProject).findProjects().first().getAllNestedFilesAndThis()

        val cssCompletionItemsByProjectPath = mutableMapOf<String, CssCompletionItem>()
        for (project in projects) {
            if (project.url == null)
                continue

            //TODO: parse the css class from the files. Don't parse twice!
            // bool flag (isReferenced) ?
            
            val cssCompletionItem =
                CssCompletionItem(
                    project.url!!.presentableUrl,
                    getCssFilesFromProject(project),
                    getAllReferencedCssFilePathsFromProject(project)
                )

            cssCompletionItemsByProjectPath[project.url!!.presentableUrl] = cssCompletionItem
        }

        this.cssCompletionItemsByProjectPath = cssCompletionItemsByProjectPath
    }

    private fun getAllReferencedCssFilePathsFromProject(project: ProjectModelEntity): List<String> {
        if (project.url == null)
            return emptyList()

        val referencedCssFilePaths = mutableListOf<String>()
        File(project.url!!.parent!!.presentableUrl).walkTopDown().forEach {
            if ((it.extension == "html" || it.extension == "cshtml") && it.path.contains("wwwroot") && !isInArtifactFolder(
                    it
                )
            ) {
                val parsed = Jsoup.parse(it)
                val allLinkTags = parsed.select("link")

                for (linkTag in allLinkTags) {
                    if (!linkTag.hasAttr("href"))
                        continue

                    val href = linkTag.attribute("href")
                    if (!href.value.endsWith(".css"))
                        continue

                    referencedCssFilePaths.add(href.value)
                }
            }
        }

        return referencedCssFilePaths
    }

    private fun getCssFilesFromProject(project: ProjectModelEntity): List<String> {
        if (project.url == null)
            return emptyList()

        //TODO: Actually parse the classes?

        val cssFiles = mutableListOf<String>()
        File(project.url!!.parent!!.presentableUrl).walkTopDown().forEach {
            if (it.extension == "css" && !isInArtifactFolder(it)) {
                cssFiles.add(it.invariantSeparatorsPath)
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

    private fun isInArtifactFolder(file: File): Boolean {
        return file.invariantSeparatorsPath.contains("/bin/") || file.invariantSeparatorsPath.contains("/obj/")
    }
}

class CssCompletionItem(projectPath: String, allCssFilePaths: List<String>, allReferencedCssFilesPaths: List<String>) {
    val ProjectPath: String = projectPath
    val AllCssFilePaths: List<String> = allCssFilePaths
    val AllReferencedCssFilePaths: List<String> = allCssFilePaths
}