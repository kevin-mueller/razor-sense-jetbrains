package com.kevinmueller.razorsensejetbrains.cssClassCompletion

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
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
    var cssCompletionsByProjectPath: Map<String, CssClassCompletions> = emptyMap()

    fun loadAllCompletions() {
        val totalCssClassNames: Int
        val executionTime = measureTimeMillis {
            totalCssClassNames = updateCompletions()
        }

        var totalFiles = 0
        for (item in cssCompletionsByProjectPath) {
            totalFiles += item.value.getTotalFileCount()
        }

        // test commit
        StatusBar.Info.set(
            "Parsed $totalCssClassNames css classes from $totalFiles files in $executionTime ms", solutionProject
        )
    }

    private fun updateCompletions(): Int {
        val projects = WorkspaceModel.getInstance(solutionProject).findProjects()

        val cssCompletionsByProjectPath = mutableMapOf<String, CssClassCompletions>()
        var totalCssClassNames = 0
        for (project in projects) {
            if (project.url == null) continue

            val localCssFiles = getLocalCssFilesFromProject(project)

            val indexHtmlFileInfo = getIndexHtmlInfoFromProject(project)

            val remoteCssFiles =
                getRemoteCssFilesFromUrls(indexHtmlFileInfo.referencedCssFilePaths)
            val allCssClassNames = localCssFiles.union(remoteCssFiles)

            totalCssClassNames += allCssClassNames.flatMap { x -> x.cssClassNames }.count()
            val cssClassCompletions = CssClassCompletions(
                allCssClassNames.toMutableSet(), indexHtmlFileInfo
            )

            cssCompletionsByProjectPath[project.url!!.presentableUrl] = cssClassCompletions
        }
        this.cssCompletionsByProjectPath = cssCompletionsByProjectPath

        return totalCssClassNames
    }

    private fun getIndexHtmlInfoFromProject(project: ProjectModelEntity): IndexHtmlFileInfo {
        if (project.url == null) return IndexHtmlFileInfo(emptyList(), false)

        var foundIndexHtmlFile = false

        val referencedCssFilePaths = mutableListOf<String>()
        VfsUtil.collectChildrenRecursively(project.getVirtualFileAsParent()!!).filter {
            (it.extension == "html" || it.extension == "cshtml") && it.path.contains("wwwroot") && !isInArtifactFolder(
                it.path
            )
        }.forEach {
            foundIndexHtmlFile = true

            val parsed = Jsoup.parse(it.toIOFile())
            val allLinkTags = parsed.select("link")

            for (linkTag in allLinkTags) {
                if (!linkTag.hasAttr("href"))
                    continue

                val href = linkTag.attribute("href")
                if (href?.value?.endsWith(".css") == true) {
                    referencedCssFilePaths.add(href.value)
                }
            }
        }

        for (projectDependency in getProjectDependencies(project)) referencedCssFilePaths.addAll(
            getIndexHtmlInfoFromProject(projectDependency).referencedCssFilePaths
        )

        return IndexHtmlFileInfo(referencedCssFilePaths, foundIndexHtmlFile)
    }

    private fun getLocalCssFilesFromProject(project: ProjectModelEntity): MutableList<CssFile> {
        val cssFiles = getAllCssFilesFromProject(project)

        return cssFiles.map { cssFile -> getClassNamesForCssFile(cssFile) }.toMutableList()
    }

    private fun getRemoteCssFilesFromUrls(
        referencedCssFilePaths: List<String>
    ): List<CssFile> {
        val cssFiles = referencedCssFilePaths.filter { x -> x.startsWith("https://") || x.startsWith("http://") }

        return cssFiles.map { cssFile -> getClassNamesForCssFile(cssFile) }.toMutableList()
    }

    private fun getAllCssFilesFromProject(project: ProjectModelEntity): Set<String> {
        val resolvedProject =
            WorkspaceModel.getInstance(solutionProject).findProjectsByName(project.name).firstOrNull()

        if (resolvedProject?.url == null) {
            return emptySet()
        }

        val cssFiles = mutableSetOf<String>()
        VfsUtil.collectChildrenRecursively(
            resolvedProject.getVirtualFileAsContentRoot()!!
        ).filter { it.extension == "css" && !isInArtifactFolder(it.path) }.forEach {
            cssFiles.add(it.path)
        }

        val packageDependencies = getPackageDependencies(resolvedProject)
        if (packageDependencies.isNotEmpty()) {
            cssFiles.addAll(getCssFilesFromPackageDependencies(packageDependencies))
        }

        val projectDependencies = getProjectDependencies(resolvedProject)
        if (projectDependencies.isNotEmpty()) {
            for (projectDependency in projectDependencies) {
                cssFiles.addAll(getAllCssFilesFromProject(projectDependency))
            }
        }

        return cssFiles
    }

    private fun getClassNamesForCssFile(cssFile: String): CssFile {

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

        val fileName = File(cssFile).name

        return CssFile(
            classNames,
            fileName,
            cssFile,
            fileName.endsWith(".razor.css")
        )
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
}

class CssClassCompletions(
    private val cssFiles: MutableSet<CssFile>,
    private val indexHtmlFileInfo: IndexHtmlFileInfo
) {
    fun getReferencedCssClasses(fileName: String?): Set<CssFile> {

        val result = mutableSetOf<CssFile>()

        if (!indexHtmlFileInfo.hasIndexHtmlFile) {
            result.addAll(cssFiles.filter { x -> !x.isScopedCssFile }.toSet())
        }

        for (cssFile in cssFiles) {
            if (cssFile.isScopedCssFile && fileName != null) {
                if (cssFile.fileName.startsWith(fileName)) {
                    result.add(cssFile)
                }
            }

            // is relevant for completion, if the css file is actually referenced by the index.html file 
            if (indexHtmlFileInfo.hasIndexHtmlFile && indexHtmlFileInfo.referencedCssFilePaths.any { relativeFilePath ->
                    cssFile.filePath.contains(relativeFilePath)
                }) {
                result.add(cssFile)
            }
        }
        return result
    }

    fun getTotalFileCount(): Int {
        return cssFiles.distinctBy { x -> x.filePath }.count()
    }
}

class CssFile(
    val cssClassNames: Set<String>,
    val fileName: String,
    val filePath: String,
    val isScopedCssFile: Boolean
)

class IndexHtmlFileInfo(val referencedCssFilePaths: List<String>, val hasIndexHtmlFile: Boolean)