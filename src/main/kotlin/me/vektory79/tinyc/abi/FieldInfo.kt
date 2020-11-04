package me.vektory79.tinyc.abi

import java.io.Serializable
import java.util.stream.Stream

class FieldInfo(
    private val clazz: ClassInfo,
    val access: Int,
    val name: String,
    val descriptor: String,
    val signature: String?
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