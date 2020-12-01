package me.vektory79.tinyc.abi

import java.io.Serializable
import java.util.stream.Stream

class FieldInfo(
    private val clazz: ClassInfo,
    access: Int,
    name: String,
    private val descriptor: String,
    signature: String?,
) : AbiElement<FieldInfo>(access, name, signature), Serializable {
    private val usages = HashSet<ClassInfo>()

    fun addUsage(ref: ClassInfo) {
        usages += ref
    }

    fun walkUsages(): Stream<ClassInfo> {
        return usages.stream()
    }

    override fun significantChangeIn(other: FieldInfo?): Boolean =
        super.significantChangeIn(other)
                || descriptor != other?.descriptor

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as FieldInfo

        if (descriptor != other.descriptor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + descriptor.hashCode()
        return result
    }
}