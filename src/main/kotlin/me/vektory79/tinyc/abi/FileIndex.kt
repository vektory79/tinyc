package me.vektory79.tinyc.abi

import me.vektory79.tinyc.path
import me.vektory79.tinyc.scanners.AbiScannerCallback
import me.vektory79.tinyc.scanners.UsageScannerCallback
import me.vektory79.tinyc.scanners.classUsageScan
import me.vektory79.tinyc.scanners.scanAbi
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.toList

class FileIndex private constructor(
    val sourceRoot: Path,
    val compiledRoot: Path,
    private val sourceExtension: String,
) : Serializable {
    internal val packages = HashMap<Path, Package>()

    fun compileListPhase1(): List<FileInfoSource> {
        val breadCrumb = HashSet<FileInfoSource>()
        return Stream.of(
            orphanCompiled(breadCrumb),
            notCompiledSources(breadCrumb),
            outdated(breadCrumb),
            forcedToRecompile(breadCrumb)
        ).flatMap { it }.toList()
    }

    fun compileListPhase2(forCompile1: List<FileInfoSource>): List<FileInfoSource> {
        val result = ArrayList<FileInfoSource>()
        val compiled = scanAllCompiled(forCompile1)
        val breadCrumb = HashSet<FileInfoSource>()
        compiled
            .mapNotNull { it.source }
            .forEach { breadCrumb.add(it) }

        compiled.forEach { recompiledClass ->
            val newClass = recompiledClass.clazz!!
            val oldClass = recompiledClass.source!![newClass.name]

            val newFields = newClass.fields
            val oldFields = oldClass?.fields

            val newMethods = newClass.methods
            val oldMethods = oldClass?.methods

            if (newClass significantChangeIn oldClass) {
                newClass
                    .walkInheritance()
                    .map { it.file.source }
                    .filter { it != null }
                    .filter { !breadCrumb.contains(it) }
                    .forEach {
                        breadCrumb.add(it!!)
                        result.add(it)
                    }
            }

            oldFields
                ?.stream()
                // Skip all unchanged fields
                ?.filter { !newFields.contains(it) }
                // Filter the significant changes in field declaration only
                ?.filter { it significantChangeIn recompiledClass.clazz?.field(it.name) }
                // Walk all classes, that uses changed field
                ?.flatMap { it.walkUsages() }
                ?.map { it.file.source }
                ?.filter { it != null }
                // Skip the repeated entries
                ?.filter { !breadCrumb.contains(it) }
                ?.forEach {
                    breadCrumb.add(it!!)
                    result.add(it)
                }

            oldMethods
                ?.stream()
                // Skip all unchanged methods
                ?.filter { !newMethods.contains(it) }
                // Filter the significant changes in method declaration only
                ?.filter { it significantChangeIn recompiledClass.clazz?.method(it.name)?.get(it.descriptor) }
                // Walk all classes, that uses changed method
                ?.flatMap { it.walkUsages() }
                ?.map { it.file.source }
                ?.filter { it != null }
                // Skip the repeated entries
                ?.filter { !breadCrumb.contains(it) }
                ?.forEach {
                    breadCrumb.add(it!!)
                    result.add(it)
                }
        }
        return result
    }

    private fun build() {
        scanAllSources()
        scanAllCompiled()
        usageScan()
        associateAll()
    }

    private fun scanAllSources() = Files
        .walk(sourceRoot)
        .filter { it.toString().endsWith(sourceExtension) }
        .map { sourceRoot.relativize(it) }
        .forEach { file ->
            val packagePath = file.parent ?: path(".")
            packages
                .computeIfAbsent(packagePath) { Package(this, packagePath) }
                .addSource(file.fileName)
        }

    private fun scanAllCompiled(forCompile1: List<FileInfoSource>? = null): List<FileInfoCompiled> {
        val affectingClassFiles = ArrayList<FileInfoCompiled>()
        Files
            .walk(compiledRoot)
            .filter { it.toString().endsWith(COMPILED_EXTENSION) }
            .forEach { file ->
                this@FileIndex.scanAbiForClassFile(file, forCompile1)?.apply {
                    affectingClassFiles.add(this)
                }
            }
        return affectingClassFiles
    }

    private fun scanAbiForClassFile(file: Path, forCompile1: List<FileInfoSource>?): FileInfoCompiled? {
        val affectingSources = HashSet<Path>()
        forCompile1?.forEach { affectingSources.add(it.name) }

        val packagePath = compiledRoot.relativize(file).parent ?: path(".")
        val pkg = packages.computeIfAbsent(packagePath) { Package(this, packagePath) }
        val info = FileInfoCompiled(pkg, file.fileName)
        var doScan = false
        scanAbi(
            file,
            object : AbiScannerCallback {
                override fun sourceFile(name: Path?) {
                    info.sourceFile = name
                    doScan = forCompile1 == null || name == null || affectingSources.contains(name)
                    if (doScan) {
                        pkg.addCompiled(info)
                    }
                }

                override fun clazz(
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    // Always creating ClassInfo because source file will be determined after this point
                    info.clazz = ClassInfo(info, access, name, signature, superName, interfaces)
                }

                override fun field(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                ) {
                    if (doScan) {
                        info.clazz?.addField(access, name, descriptor, signature)
                    }
                }

                override fun method(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exception: Array<out String>?
                ) {
                    if (doScan) {
                        info.clazz?.addMethod(access, name, descriptor, signature, exception)
                    }
                }
            }
        )
        return if (doScan) info else null
    }

    private fun usageScan() = walkCompiled().forEach { compiledFile ->
        classUsageScan(
            compiledFile.fullPath,
            object : UsageScannerCallback {
                override fun baseType(className: String) = addToTarget(className) { addInheritance(it) }
                override fun typeUsage(className: String) = addToTarget(className) { addUsage(it) }
                override fun field(owner: String, name: String) = addToTarget(owner) { addFieldUsage(it, name) }

                override fun method(owner: String, name: String, descriptor: String) =
                    addToTarget(owner) { addMethodUsage(it, name, descriptor) }

                private fun addToTarget(
                    className: String,
                    adder: ClassInfo.(ClassInfo) -> Unit
                ) {
                    val targetClassFile = targetClassFile(className)
                    compiledFile.clazz?.apply {
                        if (targetClassFile != null) {
                            targetClassFile.clazz?.adder(this)
                        }
                    }
                }

                private fun targetClassFile(className: String): FileInfoCompiled? {
                    val classPath = path("$className.class")
                    return packages[classPath.parent]?.getCompiled(classPath.fileName)
                }
            }
        )
    }

    private fun associateAll() = walkCompiled().forEach { it.associateToSource() }

    private fun walkCompiled(): Stream<FileInfoCompiled> = packages.entries.stream().flatMap { it.value.walkCompiled() }

    private fun walkSources(): Stream<FileInfoSource> = packages.entries.stream().flatMap { it.value.walkSources() }

    private fun orphanCompiled(breadCrumb: HashSet<FileInfoSource>): Stream<FileInfoSource> =
        walkCompiled()
            // Filtering orphaned class files
            .filter { it.source == null }
            .peek { removeFile(it) }
            .flatMap { compiledFile ->
                // Collect all classes, that depended from orphaned one
                Stream
                    .concat(
                        compiledFile.clazz!!.walkInheritance(),
                        compiledFile.clazz!!.walkUsages()
                    ).map { it.file.source }
                    .filter { it != null && breadCrumb.add(it) }
            }

    private fun forcedToRecompile(breadCrumb: HashSet<FileInfoSource>): Stream<FileInfoSource> =
        walkCompiled()
            .map { it.clazz }
            .filter { it?.forceRecompile ?: false }
            .map { it?.file?.source }
            .filter { it != null && breadCrumb.add(it) }
            .map { it as FileInfoSource }

    private fun outdated(breadCrumb: HashSet<FileInfoSource>): Stream<FileInfoSource> =
        walkCompiled()
            .filter { it.source != null && it olderThan it.source!! }
            .peek { removeFile(it) }
            .map { it.source!! }
            .filter { breadCrumb.add(it) }

    private fun removeFile(compiledFile: FileInfoCompiled) {
        // Remove orphaned class file
        Files.delete(compiledFile.fullPath)
        // Removing empty directories recursively
        var cleanupDir = compiledFile.fullPath.parent
        while (!Files.list(cleanupDir).anyMatch { true }) {
            Files.delete(cleanupDir)
            cleanupDir = cleanupDir.parent
        }
    }

    private fun notCompiledSources(breadCrumb: HashSet<FileInfoSource>): Stream<FileInfoSource> {
        return walkSources()
            .filter { it.notCompiled }
            .filter { breadCrumb.add(it) }
    }

    companion object {
        private const val COMPILED_EXTENSION = ".class"
        fun createNew(
            sourceRoot: Path,
            compiledRoot: Path,
            sourceExtension: String = ".java",
        ): FileIndex {
            val result = FileIndex(sourceRoot, compiledRoot, sourceExtension)
            result.build()
            return result
        }
    }
}
