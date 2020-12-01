package me.vektory79.tinyc.abi

import java.io.Serializable
import java.util.stream.Stream

class MethodInfo(
    private val clazz: ClassInfo,
    val name: String
) : Serializable {
    private val _overrides = HashMap<String, MethodOverrideInfo>()

    fun addOverride(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exception: Array<out String>?
    ) {
        _overrides[descriptor] = MethodOverrideInfo(this, access, name, descriptor, signature, exception)
    }

    val overrides: Stream<MethodOverrideInfo>
        get() = _overrides.entries.stream().map { it.value }

    operator fun get(descriptor: String): MethodOverrideInfo? {
        return _overrides[descriptor]
    }
}

class MethodOverrideInfo(
    private val method: MethodInfo,
    access: Int,
    name: String,
    val descriptor: String,
    signature: String?,
    val exception: Array<out String>?
) : AbiElement<MethodOverrideInfo>(access, name, signature), Serializable {
    private val usages = HashSet<ClassInfo>()

    fun addUsage(ref: ClassInfo) {
        usages += ref
    }

    fun walkUsages(): Stream<ClassInfo> {
        return usages.stream()
    }

    override fun significantChangeIn(other: MethodOverrideInfo?): Boolean =
        super.significantChangeIn(other)
                || descriptor != other?.descriptor
                || exception contentEquals other.exception

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
