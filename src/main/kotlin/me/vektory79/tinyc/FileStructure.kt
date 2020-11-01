package me.vektory79.tinyc

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.*

/**
 * Base class for file structure index.
 *
 * @property root The root directory for file scanning
 * @property extension The extension of files that should be scanned
 */
abstract class FileStructure(protected val root: Path, private val extension: String) {
    /**
     * Files index.
     *
     * Mapping is: relative directory (packages) -> file name -> [file info][FileInfo]
     */
    protected val paths = HashMap<Path, HashMap<Path, FileInfo>>()

    /**
     * Count of founded packages.
     */
    val packagesCount
        get() = paths.size

    /**
     * Get content of separate package.
     */
    operator fun get(packageName: Path): HashMap<Path, FileInfo>? = paths[packageName]

    /**
     * Rebuild the whole files index.
     */
    fun rebuild() {
        paths.clear()
        Files
            .walk(root)
            .filter {
                // Delete the directories without files
                if (Files.isDirectory(it)) {
                    var checkDir = it
                    while (!Files.list(checkDir).anyMatch { true }) {
                        Files.delete(checkDir)
                        checkDir = checkDir.parent
                    }
                    false
                } else {
                    true
                }
            }
            .filter { it.toString().endsWith(extension) }
            .map { root.relativize(it) }
            .forEach { file ->
                paths.computeIfAbsent(file.parent ?: Paths.get(".")) {
                    HashMap<Path, FileInfo>()
                }[file.fileName] = createFileInfo(file)
            }
    }

    /**
     * Create object with file metadata, specific for particular file extension.
     */
    protected abstract fun createFileInfo(file: Path): FileInfo
}

/**
 * Index for source files.
 */
class SourceFileStructure(root: Path, extension: String = ".java") : FileStructure(root, extension) {
    override fun createFileInfo(file: Path): FileInfo {
        return FileInfo(
            root,
            file.parent ?: Paths.get("."),
            file.fileName,
            file.fileName,
        )
    }

    /**
     * Scanning all source files and select those, that haven't associated class files, for compile.
     */
    fun forCompile(): ArrayList<FileInfo> {
        val result = ArrayList<FileInfo>()
        paths.entries.stream()
            .flatMap { it.value.entries.stream() }
            .map { it.value }
            .filter { it.relateTo.isEmpty() }
            .forEach(result::add)
        return result
    }
}

/**
 * Index for class files.
 */
class ClassesFileStricture(root: Path) : FileStructure(root, ".class") {
    override fun createFileInfo(file: Path): FileInfo {
        val fullPath = root.resolve(file)
        return FileInfo(
            root,
            file.parent ?: Paths.get("."),
            file.fileName,
            extractSourceName(fullPath),
        )
    }

    /**
     * Associate the class files in index to respective source files.
     *
     * The association is established for non outdated class files only.
     */
    fun associate(sources: SourceFileStructure) {
        paths.forEach { (packagePath, files) ->
            files.forEach { (_, classInfo) ->
                val sourceInfo = sources[packagePath]?.get(classInfo.source)
                if (sourceInfo != null && sourceInfo olderThan classInfo) {
                    classInfo.relateTo.add(sourceInfo)
                    sourceInfo.relateTo.add(classInfo)
                }
            }
        }
    }

    /**
     * Cleaning all orphaned and outdated class files and folders.
     */
    fun cleanup() {
        val forRemove = ArrayList<FileInfo>()
        // Collecting all class files needed to be removed
        paths.entries.stream()
            .flatMap { it.value.entries.stream() }
            .map { it.value }
            .filter { it.relateTo.isEmpty() }
            .forEach(forRemove::add)
        // Remove collected files from disk and index
        forRemove.forEach { deletingFile ->
            val packageContent = paths[deletingFile.packageName]
            packageContent?.run {
                Files.delete(deletingFile.fullPath)
                remove(deletingFile.fileName)
                ifEmpty {
                    var cleanupFile = root.resolve(deletingFile.packageName).normalize()
                    // Removing empty directories recursively
                    while (!Files.list(cleanupFile).anyMatch { true }) {
                        Files.delete(cleanupFile)
                        cleanupFile = cleanupFile.parent
                    }
                    paths.remove(deletingFile.packageName)
                }
            }
            val pathToDelete = deletingFile.fullPath
            if (Files.exists(pathToDelete)) {
                Files.delete(pathToDelete)
            }
        }
    }
}

/**
 * File metadata.
 *
 * @property root the root folder for files scan.
 * @property packageName relative path from root to file
 * @property fileName Name of the file
 * @property source file source name (valid for class files only)
 */
data class FileInfo(
    val root: Path,
    val packageName: Path,
    val fileName: Path,
    val source: Path,
) {
    /**
     * List of respective files.
     *
     * The source file can have multiple relations to class files, but every
     * class file can have only single relation to source.
     */
    val relateTo = HashSet<FileInfo>()

    private val modifiedTime: FileTime = Files.getLastModifiedTime(fullPath)

    /**
     * Full path to the file.
     */
    val fullPath: Path
        get() = root.resolve(packageName).resolve(fileName).toAbsolutePath().normalize()

    /**
     * Check that current file is older than provided.
     */
    infix fun olderThan(otherFile: FileInfo): Boolean {
        return modifiedTime < otherFile.modifiedTime
    }
}