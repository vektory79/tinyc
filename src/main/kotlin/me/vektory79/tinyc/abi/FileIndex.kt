package me.vektory79.tinyc.abi

import me.vektory79.tinyc.*
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
        return Stream.concat(
            Stream.concat(
                orphanCompiled(breadCrumb),
                notCompiledSources(breadCrumb)
            ),
            outdated(breadCrumb)
        ).toList()
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
            val packagePath = file.parent ?: Paths.get(".")
            packages
                .computeIfAbsent(packagePath) { Package(this, packagePath) }
                .addSource(file.fileName)
        }

    private fun scanAllCompiled() = Files
        .walk(compiledRoot)
        .filter { it.toString().endsWith(COMPILED_EXTENSION) }
        .map { compiledRoot.relativize(it) }
        .forEach(this@FileIndex::scanAbiForClassFile)

    private fun scanAbiForClassFile(file: Path) {
        val packagePath = file.parent ?: Paths.get(".")
        val pkg = packages.computeIfAbsent(packagePath) { Package(this, packagePath) }
        val info = FileInfoCompiled(pkg, file.fileName)
        pkg.addCompiled(info)
        scanAbi(
            compiledRoot.resolve(file),
            object : AbiScannerCallback {
                override fun sourceFile(name: Path?) {
                    info.sourceFile = name
                }

                override fun clazz(
                    access: Int,
                    name: String,
                    signature: String?,
                    superName: String?,
                    interfaces: Array<out String>?
                ) {
                    info.clazz = ClassInfo(info, access, name, signature, superName, interfaces)
                }

                override fun field(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?
                ) {
                    info.clazz?.addField(access, name, descriptor, signature)
                }

                override fun method(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exception: Array<out String>?
                ) {
                    info.clazz?.addMethod(access, name, descriptor, signature, exception)
                }
            }
        )
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
                    compiledFile.clazz?.run {
                        if (targetClassFile != null) {
                            targetClassFile.clazz?.adder(this)
                        } else {
                            forceRecompile = true
                        }
                    }
                }

                private fun targetClassFile(className: String): FileInfoCompiled? {
                    val classPath = Paths.get("$className.class")
                    return packages[classPath.parent]?.getCompiled(classPath.fileName)
                }
            }
        )
    }

    private fun associateAll() = walkCompiled().forEach { it.associateToSource() }

    private fun walkCompiled(): Stream<FileInfoCompiled> = packages.entries.stream().flatMap { it.value.walkCompiled() }

    private fun walkSources(): Stream<FileInfoSource> = packages.entries.stream().flatMap { it.value.walkSources() }

    private fun orphanCompiled(breadCrumb: HashSet<FileInfoSource>): Stream<FileInfoSource> {
        return walkCompiled()
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
    }

    private fun outdated(breadCrumb: HashSet<FileInfoSource>): Stream<FileInfoSource> {
        return walkCompiled()
            .filter { it.source != null && it olderThan it.source!! }
            .peek { removeFile(it) }
            .map { it.source!! }
            .filter { breadCrumb.add(it) }
    }

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
