package me.vektory79.tinyc.abi

import java.io.Serializable
import java.util.stream.Stream

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

    operator fun get(descriptor: String): MethodOverrideInfo? {
        return overrides[descriptor]
    }

    fun walkUsages(): Stream<ClassInfo> {
        return overrides.entries.stream().flatMap { it.value.walkUsages() }
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
    private val usages = HashSet<ClassInfo>()

    fun addUsage(ref: ClassInfo) {
        usages += ref
    }

    fun walkUsages(): Stream<ClassInfo> {
        return usages.stream()
    }

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
