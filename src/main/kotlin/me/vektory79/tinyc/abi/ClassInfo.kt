package me.vektory79.tinyc.abi

import java.io.Serializable
import java.util.stream.Stream

class ClassInfo(
    val file: FileInfoCompiled,
    access: Int,
    name: String,
    signature: String?,
    private val superName: String?,
    private val interfaces: Array<out String>?
) : AbiElement<ClassInfo>(access, name, signature), Serializable {
    private val inheritance = HashSet<ClassInfo>()
    private val usages = HashSet<ClassInfo>()
    private val _fields = HashMap<String, FieldInfo>()
    private val _methods = HashMap<String, MethodInfo>()

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
        val inheritance = this
            .file
            .source
            ?.get(name)
            ?.inheritance
            ?.stream()
            ?.flatMap {
                Stream.concat(Stream.of(it), it.walkInheritance())
            }
        if (inheritance != null) {
            return inheritance
        }
        return Stream.empty()
    }

    fun addUsage(ref: ClassInfo) {
        usages += ref
    }

    fun walkUsages(): Stream<ClassInfo> {
        return usages.stream()
    }

    val fields: Set<FieldInfo>
        get() = _fields.values.toSet()

    fun field(name: String): FieldInfo? = _fields[name]

    fun addField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
    ) {
        _fields[name] = FieldInfo(this, access, name, descriptor, signature)
    }

    fun addFieldUsage(usageClass: ClassInfo, fieldName: String) {
        _fields[fieldName]?.addUsage(usageClass)
    }

    val methods: Set<MethodOverrideInfo>
        get() {
            val result = HashSet<MethodOverrideInfo>()
            _methods.entries.stream().flatMap { it.value.overrides }.forEach { result.add(it) }
            return result
        }

    fun method(name: String): MethodInfo? = _methods[name]

    fun addMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exception: Array<out String>?
    ) {
        _methods
            .computeIfAbsent(name) { MethodInfo(this, it) }
            .addOverride(access, name, descriptor, signature, exception)
    }

    fun addMethodUsage(usageClass: ClassInfo, name: String, descriptor: String) {
        _methods[name]?.get(descriptor)?.addUsage(usageClass)
    }

    override fun significantChangeIn(other: ClassInfo?): Boolean =
        super.significantChangeIn(other)
                || this != other
                || this.fields.filter { !it.static } != other.fields.filter { !it.static }
                || this.methods.filter { !it.static } != other.methods.filter { !it.static }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as ClassInfo

        if (superName != other.superName) return false
        if (interfaces != null) {
            if (other.interfaces == null) return false
            if (!interfaces.contentEquals(other.interfaces)) return false
        } else if (other.interfaces != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + (superName?.hashCode() ?: 0)
        result = 31 * result + (interfaces?.contentHashCode() ?: 0)
        return result
    }
}