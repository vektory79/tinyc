package me.vektory79.tinyc.abi

import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path

sealed class FileInfo(
    val pkg: Package,
    val name: Path,
) : Serializable {
    private val modifiedTime by lazy { Files.getLastModifiedTime(fullPath) }

    abstract val fullPath: Path

    /**
     * Check that current file is older than provided.
     */
    infix fun olderThan(otherFile: FileInfo): Boolean = modifiedTime < otherFile.modifiedTime

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileInfo) return false

        if (pkg != other.pkg) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pkg.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

class FileInfoSource(
    pkg: Package,
    name: Path,
) : FileInfo(pkg, name) {
    internal val classes = HashMap<String, ClassInfo>()

    operator fun get(name: String): ClassInfo? = classes[name]

    fun addClass(clazz: ClassInfo) {
        classes[clazz.name] = clazz
    }

    val notCompiled: Boolean
        get() = classes.isEmpty()

    override val fullPath: Path
        get() = pkg.index.sourceRoot.resolve(pkg.path).resolve(name)
}

class FileInfoCompiled(
    pkg: Package,
    name: Path,
) : FileInfo(pkg, name) {
    var clazz: ClassInfo? = null
    var sourceFile: Path? = null
    val source: FileInfoSource?
        get() = pkg.getSource(sourceFile!!)

    fun associateToSource() {
        if (sourceFile != null) {
            source?.addClass(clazz!!)
        } else {
            clazz?.forceRecompile = true
        }
    }

    override val fullPath: Path
        get() = pkg.index.compiledRoot.resolve(pkg.path).resolve(name).normalize()
}
