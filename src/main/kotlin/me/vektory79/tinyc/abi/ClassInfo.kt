package me.vektory79.tinyc.abi

import me.vektory79.tinyc.IndexInconsistencyException
import java.io.Serializable
import java.util.stream.Stream

class ClassInfo(
    val file: FileInfoCompiled,
    val access: Int,
    val name: String,
    val signature: String?,
    val superName: String?,
    val interfaces: Array<out String>?
) : Serializable {
    val visibility = Visibility.fromAccess(access)
    private val inheritance = HashSet<ClassInfo>()
    private val usages = HashSet<ClassInfo>()
    private val fields = HashMap<String, FieldInfo>()
    private val methods = HashMap<String, MethodInfo>()

    /**
     * The flag, that forces recompilation of this class.
     *
     * It may be set to true if other class, used by this one is disappeared.
     */
    var forceRecompile = false

    fun addInheritance(ref: ClassInfo) {
        inheritance += ref
    }

    fun walkInheritance(): Stream<ClassInfo> {
        return Stream.concat(
            Stream.of(this),
            inheritance.stream().flatMap { walkInheritance() }
        )
    }

    fun addUsage(ref: ClassInfo) {
        usages += ref
    }

    fun walkUsages(): Stream<ClassInfo> {
        return usages.stream()
    }

    fun addField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?
    ) {
        fields[name] = FieldInfo(this, access, name, descriptor, signature)
    }

    fun addFieldUsage(usageClass: ClassInfo, fieldName: String) {
        val fieldInfo = fields[fieldName] ?: throw IndexInconsistencyException(
            "Can't find field for usage: ${usageClass.name}.$fieldName"
        )
        fieldInfo.addUsage(usageClass)
    }

    fun walkFieldsUsages(): Stream<ClassInfo> {
        return fields.entries.stream().flatMap { it.value.walkUsages() }
    }

    fun addMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exception: Array<out String>?
    ) {
        methods
            .computeIfAbsent(name) { MethodInfo(this, it) }
            .addOverride(access, name, descriptor, signature, exception)
    }

    fun addMethodUsage(usageClass: ClassInfo, name: String, descriptor: String) {
        val overrideInfo = methods[name]?.get(descriptor) ?: throw IndexInconsistencyException(
            "Can't find method for usage: ${usageClass.name}.$name($descriptor)"
        )
        overrideInfo.addUsage(usageClass)
    }

    fun walkMethodsUsages(): Stream<ClassInfo> {
        return methods.entries.stream().flatMap { it.value.walkUsages() }
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