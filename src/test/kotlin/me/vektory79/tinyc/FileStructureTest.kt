package me.vektory79.tinyc

import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun scanTest() {
        classUsageScan(
            Paths.get("/media/data/java/tinyc/target/test-classes/me/vektory79/tinyc/TestClass.class"),
            object: UsageScannerCallback {
                override fun baseType(className: String) {

                }
                override fun typeUsage(className: String) {

                }
            }
        )
    }

    @Test
    fun buildNewProps() {
        val sourceProps = PropertiesFileStructure(srcRoot.resolve("..").resolve("resources"))
        sourceProps.rebuild()
        assertEquals(2, sourceProps.packagesCount, "This project should have 2 packages")

        val srcPackageContent: HashMap<Path, FileInfo>? =
            sourceProps[Paths.get(this.javaClass.`package`.name.replace('.', File.separatorChar))]
        assertNotNull(srcPackageContent, "This project should have package of the current test class")
        assertEquals(2, srcPackageContent.size, "This project should have two translation bundle files")

        val classesProps = PropertiesFileStructure(classesRoot)
        classesProps.rebuild()
        assertEquals(2, classesProps.packagesCount, "This project should have 2 packages")

        val clsPackageContent: HashMap<Path, FileInfo>? =
            classesProps[Paths.get(this.javaClass.`package`.name.replace('.', File.separatorChar))]
        assertNotNull(clsPackageContent, "This project should have package of the current test class")
        assertEquals(2, clsPackageContent.size, "This project should have two translation bundle files")

        clsPackageContent.forEach { (clsFileName, clsFileInfo) ->
            srcPackageContent[clsFileName].apply {
                assertNotNull(this)
                assertTrue(
                    this olderThan clsFileInfo, "The file in classes directory should be modified after respective " +
                            "file in the source directory: $clsFileName"
                )
            }
        }
    }

    @Test
    fun buildNewClasses() {
        val sourceProps = SourceFileStructure(srcRoot, ".kt")
        sourceProps.rebuild()
        assertEquals(1, sourceProps.packagesCount, "This project should have single package only")

        val srcPackageContent: HashMap<Path, FileInfo>? =
            sourceProps[Paths.get(this.javaClass.`package`.name.replace('.', File.separatorChar))]
        assertNotNull(srcPackageContent, "This project should have package of the current test class")
        assertTrue(
            srcPackageContent.size > 3, "This project should have more than 3 source files, " +
                    "but have: ${srcPackageContent.size}"
        )

        Files.createDirectories(classesRoot.resolve("testDir").resolve("testDir"))
        val classesProps = ClassesFileStricture(classesRoot)
        classesProps.rebuild()
        assertFalse(Files.exists(classesRoot.resolve("testDir").resolve("testDir")), "The empty directory should not exists")
        assertFalse(Files.exists(classesRoot.resolve("testDir")), "The empty directory should not exists")
        classesProps.associate(sourceProps)
        assertEquals(1, classesProps.packagesCount, "This project should have single package only")

        val clsPackageContent: HashMap<Path, FileInfo>? =
            classesProps[Paths.get(this.javaClass.`package`.name.replace('.', File.separatorChar))]
        assertNotNull(clsPackageContent, "This project should have package of the current test class")
        assertTrue(
            clsPackageContent.size > srcPackageContent.size, "This project should have more class files than source " +
                    "files, but have sources: ${srcPackageContent.size} and classes: ${clsPackageContent.size}"
        )

        clsPackageContent.forEach { (clsFileName, clsFileInfo) ->
            srcPackageContent[clsFileInfo.source].apply {
                assertNotNull(this)
                assertTrue(
                     this olderThan clsFileInfo, "The file in classes directory should be modified after respective " +
                            "file in the source directory: $clsFileName"
                )
                assertEquals(1, clsFileInfo.relateTo.size, "Compiled class file should have only one relation file")
                assertTrue(relateTo.size >= 1, "Source file should have one or more relation files")
            }
        }
    }

    private class PropertiesFileStructure(root: Path) : FileStructure(root, ".properties") {
        override fun createFileInfo(file: Path): FileInfo {
            return FileInfo(root, file.parent ?: Paths.get("."), file.fileName, file.fileName)
        }
    }
}