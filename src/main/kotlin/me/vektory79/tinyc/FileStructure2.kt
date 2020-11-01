package me.vektory79.tinyc

import org.objectweb.asm.Opcodes
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.collections.set

internal fun <T> T?.check(): T {
    if (this != null) {
        return this
    }
    throw TinycErrorException(8, R["IndexCorruptedError"])
}

class FileIndex2 private constructor(
    val sourceRoot: Path,
    val compiledRoot: Path,
    private val sourceExtension: String,
) : Serializable {
    private val packages = HashMap<Path, Package>()

    private fun build() {
        scanAllSources()
        scanAllCompiled()
        usageScan()
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
        .forEach { file ->
            val packagePath = file.parent ?: Paths.get(".")
            val pkg = packages.computeIfAbsent(packagePath) { Package(this, packagePath) }
            val info = FileInfoCompiled(pkg, file.fileName)
            pkg.addCompiled(info)
            scanAbi(
                file,
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

    private fun usageScan() = packages.entries
        .stream()
        .flatMap { it.value.walkCompiled() }
        .forEach { compiledFile ->
            classUsageScan(
                compiledFile.fullPath,
                object : UsageScannerCallback {
                    override fun baseType(className: String) = addToTargetSource(className) { addInheritance(it) }
                    override fun typeUsage(className: String) = addToTargetSource(className) { addUsage(it) }

                    override fun field(owner: String, name: String) {
                        TODO("Not yet implemented")
                    }

                    override fun method(owner: String, name: String, descriptor: String) {
                        TODO("Not yet implemented")
                    }

                    private fun addToTargetSource(
                        className: String,
                        adder: FileInfoSource.(FileInfoSource) -> Unit
                    ) {
                        val classPath = Paths.get("$className.class")
                        val targetClass = packages[classPath.parent]?.getCompiled(classPath.fileName)
                        if (targetClass != null) {
                            val targetSource = targetClass.source
                            if (targetSource == null) {
                                compiledFile.forceRecompile = true
                            } else {
                                val sourceFile = compiledFile.source
                                if (sourceFile != null) {
                                    targetSource.adder(sourceFile)
                                }
                            }
                        }
                    }
                }
            )
        }

    companion object {
        private const val COMPILED_EXTENSION = ".class"
        fun createNew(
            sourceRoot: Path,
            compiledRoot: Path,
            sourceExtension: String = ".java",
        ): FileIndex2 {
            val result = FileIndex2(sourceRoot, compiledRoot, sourceExtension)
            result.build()
            return result
        }
    }
}

class Package(val index: FileIndex2, val path: Path) : Serializable {
    private val sources = HashMap<Path, FileInfoSource>()
    private val compiled = HashMap<Path, FileInfoCompiled>()

    fun addSource(name: Path) {
        sources[name] = FileInfoSource(this, name)
    }

    fun getSource(name: Path): FileInfoSource? = sources[name]

    fun walkSources(): Stream<FileInfoSource> = sources.entries.stream().map { it.value }

    fun addCompiled(value: FileInfoCompiled) {
        compiled[value.name] = value
    }

    fun getCompiled(name: Path): FileInfoCompiled? = compiled[name]

    fun walkCompiled(): Stream<FileInfoCompiled> = compiled.entries.stream().map { it.value }
}

sealed class FileInfo2(
    val pkg: Package,
    val name: Path,
) : Serializable {
    private val modifiedTime by lazy { Files.getLastModifiedTime(fullPath) }

    abstract val fullPath: Path

    /**
     * Check that current file is older than provided.
     */
    infix fun olderThan(otherFile: FileInfo2): Boolean = modifiedTime < otherFile.modifiedTime

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileInfo2) return false

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
) : FileInfo2(pkg, name) {
    private val compiled = ArrayList<FileInfoCompiled>()
    private val inheritance = HashSet<FileInfoSource>()
    private val classUsages = HashSet<FileInfoSource>()
    private val classes = HashMap<String, ClassInfo>()

    fun addCompiled(fileInfo: FileInfoCompiled) {
        compiled += fileInfo
    }

    fun addInheritance(ref: FileInfoSource) {
        inheritance += ref
    }

    fun addUsage(ref: FileInfoSource) {
        classUsages += ref
    }

    fun addClass(clazz: ClassInfo) {
        classes[clazz.name] = clazz
    }

    override val fullPath: Path
        get() = pkg.index.sourceRoot.resolve(pkg.path).resolve(name)
}

class FileInfoCompiled(
    pkg: Package,
    name: Path,
) : FileInfo2(pkg, name) {
    var clazz: ClassInfo? = null
    var sourceFile: Path? = null

    /**
     * The flag, that forces recompilation of this class.
     *
     * It may be set to true if other class, used by this one is disappeared.
     */
    var forceRecompile = false

    val source: FileInfoSource? by lazy {
        val infoSource = pkg.getSource(sourceFile.check())
        infoSource?.addCompiled(this)
        infoSource
    }

    override val fullPath: Path
        get() = pkg.index.compiledRoot.resolve(pkg.path).resolve(name)
}

enum class Visibility(private val scope: Int) {
    PRIVATE(0),
    PROTECTED(1),
    INTERNAL(2),
    PUBLIC(3);

    infix fun lessThan(other: Visibility): Boolean {
        return this.scope < other.scope
    }

    companion object {
        fun fromAccess(access: Int): Visibility {
            return when (access and (Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED or Opcodes.ACC_PUBLIC)) {
                0 -> INTERNAL
                Opcodes.ACC_PRIVATE -> PRIVATE
                Opcodes.ACC_PROTECTED -> PROTECTED
                Opcodes.ACC_PUBLIC -> PUBLIC
                // TODO: Exception message should have class or/and member name
                else -> throw TinycErrorException(
                    7,
                    R["IncorrectAccessFlagsError", Integer.toBinaryString(access).padStart(Int.SIZE_BITS, '0')]
                )
            }
        }
    }
}

class ClassInfo(
    private val file: FileInfo2,
    val access: Int,
    val name: String,
    val signature: String?,
    val superName: String?,
    val interfaces: Array<out String>?
) : Serializable {
    val visibility = Visibility.fromAccess(access)
    private val fields = HashMap<String, FieldInfo>()
    private val methods = HashMap<String, MethodInfo>()

    fun addField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?
    ) {
        fields[name] = FieldInfo(this, access, name, descriptor, signature)
    }

    fun addMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exception: Array<out String>?
    ) {
        methods.computeIfAbsent(name) { MethodInfo(this, it) }
            .addOverride(access, name, descriptor, signature, exception)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassInfo) return false

        if (access != other.access) return false
        if (name != other.name) return false
        if (signature != other.signature) return false
        if (superName != other.superName) return false
        if (interfaces != null) {
            if (other.interfaces == null) return false
            if (!interfaces.contentEquals(other.interfaces)) return false
        } else if (other.interfaces != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = access
        result = 31 * result + name.hashCode()
        result = 31 * result + (signature?.hashCode() ?: 0)
        result = 31 * result + (superName?.hashCode() ?: 0)
        result = 31 * result + (interfaces?.contentHashCode() ?: 0)
        return result
    }

}

class FieldInfo(
    private val clazz: ClassInfo,
    val access: Int,
    val name: String,
    val descriptor: String,
    val signature: String?
) : Serializable {
    val visibility = Visibility.fromAccess(access)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FieldInfo) return false

        if (name != other.name) return false
        if (descriptor != other.descriptor) return false
        if (signature != other.signature) return false
        if (access != other.access) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + descriptor.hashCode()
        result = 31 * result + signature.hashCode()
        result = 31 * result + access
        return result
    }
}

class MethodInfo(
    private val clazz: ClassInfo,
    val name: String
) : Serializable {
    private val overrides = HashMap<String, MethodOverrideInfo>()

    fun addOverride(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exception: Array<out String>?
    ) {
        overrides[descriptor] = MethodOverrideInfo(this, access, name, descriptor, signature, exception)
    }
}

class MethodOverrideInfo(
    private val method: MethodInfo,
    val access: Int,
    val name: String,
    val descriptor: String,
    val signature: String?,
    val exception: Array<out String>?
) : Serializable {
    val visibility = Visibility.fromAccess(access)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodOverrideInfo) return false

        if (name != other.name) return false
        if (descriptor != other.descriptor) return false
        if (signature != other.signature) return false
        if (access != other.access) return false
        if (!exception.contentEquals(other.exception)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + descriptor.hashCode()
        result = 31 * result + signature.hashCode()
        result = 31 * result + access
        result = 31 * result + exception.contentHashCode()
        return result
    }
}