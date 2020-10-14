package me.vektory79.tinyc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

internal class UtilsKtTest {

    @Test
    fun extractSourceName() {
        val classPath = Paths.get(
            this.javaClass.protectionDomain.codeSource.location.path,
            "${this.javaClass.name.replace('.', File.separatorChar)}.class"
        )
        assertEquals(
            "${this.javaClass.simpleName}.kt",
            extractSourceName(classPath).toString(),
            "The extracted source name should equals to this file name"
        )
    }
}