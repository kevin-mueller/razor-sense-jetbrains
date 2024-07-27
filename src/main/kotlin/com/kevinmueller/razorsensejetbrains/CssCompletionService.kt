package com.kevinmueller.razorsensejetbrains

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.jetbrains.rdclient.util.idea.toIOFile
import com.jetbrains.rider.projectView.workspace.*
import org.jsoup.Jsoup
import java.io.File
import java.net.URI
import kotlin.system.measureTimeMillis

@Service(Service.Level.PROJECT)
class CssCompletionService(private val solutionProject: Project) {
    var cssCompletionItemsByProjectPath: Map<String, CssCompletionItem> = emptyMap()

    fun loadCompletions() {
        val totalCssClassNames: Int
        val executionTime = measureTimeMillis {
            totalCssClassNames = updateCompletions()
        }

        val allFiles = mutableListOf<String>()
        for (item in cssCompletionItemsByProjectPath) {
            for (cssClassName in item.value.allCssClassNames) {
                allFiles.add(cssClassName.filePath)
            }
        }

        StatusBar.Info.set(
            "Parsed $totalCssClassNames css classes from ${
                allFiles.distinct().count()
            } files in $executionTime ms", solutionProject
        )
    }

    private fun updateCompletions(): Int {
        val projects = WorkspaceModel.getInstance(solutionProject).findProjects()

        WorkspaceModel.getInstance(solutionProject).findProjects().first().getAllNestedFilesAndThis()

        val cssCompletionItemsByProjectPath = mutableMapOf<String, CssCompletionItem>()
        var totalCssClassNames = 0;
        for (project in projects) {
            if (project.url == null) continue

            val localCssClassNames = getLocalCssClassNamesFromProject(project)

            val allReferencedCssFilePaths = getAllReferencedCssFilePathsFromProject(project)

            val remoteCssClassNames = getRemoteCssClassNamesFromUrls(allReferencedCssFilePaths.referencedCssFilePaths)
            val allCssClassNames = localCssClassNames.union(remoteCssClassNames)

            totalCssClassNames += allCssClassNames.flatMap { x -> x.cssClassReferences }.count()
            val cssCompletionItem = CssCompletionItem(
                allCssClassNames, allReferencedCssFilePaths
            )

            cssCompletionItemsByProjectPath[project.url!!.presentableUrl] = cssCompletionItem
        }
        this.cssCompletionItemsByProjectPath = cssCompletionItemsByProjectPath

        return totalCssClassNames;
    }

    private fun getAllReferencedCssFilePathsFromProject(project: ProjectModelEntity): IndexFileInfo {
        if (project.url == null) return IndexFileInfo(emptyList(), false)

        var foundIndexHtmlFile = false

        val referencedCssFilePaths = mutableListOf<String>()
        VfsUtil.collectChildrenRecursively(project.getVirtualFileAsParent()!!).filter {
            (it.extension == "html" || it.extension == "cshtml") && it.path.contains("wwwroot") && !isInArtifactFolder(
                it
            )
        }.forEach {
            foundIndexHtmlFile = true

            val parsed = Jsoup.parse(it.toIOFile())
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

        for (projectDependency in getProjectDependencies(project)) referencedCssFilePaths.addAll(
            getAllReferencedCssFilePathsFromProject(projectDependency).referencedCssFilePaths
        )

        return IndexFileInfo(referencedCssFilePaths, foundIndexHtmlFile)
    }

    private fun getLocalCssClassNamesFromProject(project: ProjectModelEntity): MutableList<CssClassName> {
        val cssFiles = getAllCssFilesFromProject(project)
        return getCssClassNamesFromCssFiles(cssFiles).toMutableList()
    }

    private fun getRemoteCssClassNamesFromUrls(
        referencedCssFilePaths: List<String>
    ): List<CssClassName> {
        val cssFiles = referencedCssFilePaths.filter { x -> x.startsWith("https://") || x.startsWith("http://") }
        return getCssClassNamesFromCssFiles(cssFiles)
    }

    private fun getAllCssFilesFromProject(project: ProjectModelEntity): List<String> {
        if (project.url == null) return emptyList()

        val cssFiles = mutableListOf<String>()
        VfsUtil.collectChildrenRecursively(
            project.parentEntity?.getVirtualFileAsContentRoot()!!
        ).filter { it.extension == "css" && !isInArtifactFolder(it) }.forEach {
            cssFiles.add(it.path)
        }

        val packageDependencies = getPackageDependencies(project)
        if (packageDependencies.isNotEmpty()) {
            cssFiles.addAll(getCssFilesFromPackageDependencies(packageDependencies))
        }

        val projectDependencies = getProjectDependencies(project)
        if (projectDependencies.isNotEmpty()) {
            for (projectDependency in projectDependencies) cssFiles.addAll(getAllCssFilesFromProject(projectDependency))
        }

        return cssFiles
    }

    private fun getCssClassNamesFromCssFiles(cssFilePaths: List<String>): List<CssClassName> {
        val cssClassNames = mutableListOf<CssClassName>()
        for (cssFile in cssFilePaths) {
            if (cssClassNames.any { x -> x.fileName == cssFile }) {
                continue
            }

            var cssContent = ""
            try {
                cssContent =
                    if (cssFile.startsWith("https://") || cssFile.startsWith("http://")) URI(cssFile).toURL().readText()
                    else File(cssFile).readText()
            } catch (_: Exception) {
                // ignored
            }

            val classPattern = Regex("\\.([a-zA-Z0-9_-]+)\\s*\\{")

            val classNames = classPattern.findAll(cssContent).map { it.groupValues[1] }.toSet().sorted().toSet()

            cssClassNames.add(CssClassName(classNames, File(cssFile).name, cssFile))
        }

        return cssClassNames
    }

    private fun getProjectDependencies(project: ProjectModelEntity): List<ProjectModelEntity> {
        return getTargetFrameworkFolder(project)?.childrenEntities?.firstOrNull { p -> p.isProjectsFolder() }?.childrenEntities
            ?: emptyList()
    }

    private fun getPackageDependencies(project: ProjectModelEntity): List<ProjectModelEntity> {
        return getTargetFrameworkFolder(project)?.childrenEntities?.firstOrNull { p -> p.isPackagesFolder() }?.childrenEntities
            ?: emptyList()
    }

    private fun getTargetFrameworkFolder(project: ProjectModelEntity): ProjectModelEntity? {
        val dependencies = project.childrenEntities.firstOrNull { p -> p.isDependenciesFolder() }

        return dependencies?.childrenEntities?.firstOrNull { p -> p.isTargetFrameworkFolder() }
    }

    private fun getCssFilesFromPackageDependencies(packageDependencies: List<ProjectModelEntity>): List<String> {
        val cssFiles = mutableListOf<String>()
        for (packageDependency in packageDependencies) {
            // cannot use VFS, because its null for package dependencies
            var cssFilesFromPackage = packageDependency.getFile()?.walkTopDown()?.filter { x -> x.extension == "css" }
                ?.map { x -> x.invariantSeparatorsPath }?.toList()
            
            if (cssFilesFromPackage == null)
                cssFilesFromPackage = emptyList()

            cssFiles.addAll(cssFilesFromPackage)
        }

        return cssFiles
    }

    private fun isInArtifactFolder(file: VirtualFile): Boolean {
        return file.path.contains("/bin/") || file.path.contains("/obj/")
    }
}

class CssCompletionItem(
    val allCssClassNames: Set<CssClassName>, private val indexFileInfo: IndexFileInfo
) {
    fun getReferencedCssClassNames(): Set<CssClassName> {
        return if (indexFileInfo.hasIndexHtmlFile) {
            allCssClassNames.filter { cssClassName ->
                indexFileInfo.referencedCssFilePaths.contains(
                    cssClassName.filePath
                )
            }.toSet()
        } else {
            allCssClassNames.toSet()
        }
    }
}

class CssClassName(val cssClassReferences: Set<String>, val fileName: String, val filePath: String)

class IndexFileInfo(val referencedCssFilePaths: List<String>, val hasIndexHtmlFile: Boolean);