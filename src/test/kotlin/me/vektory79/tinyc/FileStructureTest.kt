package me.vektory79.tinyc

import me.vektory79.tinyc.abi.FileIndex
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class FileStructureTest {
    private val testClassesRoot: Path = Paths
        .get(this.javaClass.protectionDomain.codeSource.location.path)
    private val classesRoot: Path = testClassesRoot
        .resolve("..")
        .resolve("classes")
        .normalize()
    private val srcRoot: Path = testClassesRoot
        .resolve("..")
        .resolve("..")
        .resolve("src")
        .resolve("main")
        .resolve("kotlin")
        .normalize()

    @Test
    fun buildNewClasses() {
        val index = FileIndex.createNew(srcRoot, classesRoot, ".kt")
        assertEquals(2, index.packages.size, "This project should have 2 packages only")

        val pkg = index.packages[Paths.get(this.javaClass.`package`.name.replace('.', File.separatorChar))]
        assertNotNull(pkg, "This project should have package of the current test class")
        assertTrue(
            pkg.sources.size > 3, "This project should have more than 3 source files, " +
                    "but have: ${pkg.sources.size}"
        )
        assertTrue(
            pkg.compiled.size > 3, "This project should have more than 3 source files, " +
                    "but have: ${pkg.compiled.size}"
        )

        pkg.compiled.forEach { (_, clsFileInfo) ->
            clsFileInfo.source.apply {
                assertNotNull(this, "Compiled class file should have source file")
                assertTrue(this.classes.size >= 1, "Source file should have one or more classes")
            }
        }
    }
}