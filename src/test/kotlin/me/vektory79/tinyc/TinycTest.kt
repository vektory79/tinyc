package me.vektory79.tinyc

import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import picocli.CommandLine
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TinycTest {

    @Test
    @DisplayName("Just compile the single class")
    fun test1() {
        compileTest(1, compileResults = arrayOf(
            CompileResult(0, 1, 0)
        ))
        assertEquals("Hello, world!!!\n", runTest("Main"))
        assertClassesPresent(
            "Main",
            "Main$1"
        )
    }

    @Test
    @DisplayName("Reducing the anonymous classes count")
    fun test2() {
        compileTest(2, compileResults = arrayOf(
            CompileResult(0, 1, 0),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello, world!!!\n", runTest("Main"))
        assertClassesPresent(
            "Main",
        )
        assertClassesNotPresent(
            "Main$1"
        )
    }

    @Test
    @DisplayName("Refactoring the package structure")
    fun test3() {
        compileTest(3, clean = true, compileResults = arrayOf(
            CompileResult(0, 1, 0),
            CompileResult(0, 2, 0),
        ))
        assertEquals("Hello, world!!!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
        assertClassesNotPresent(
            "Main"
        )
    }

    @Test
    @DisplayName("Recompiling the single class on it's changing")
    fun test4() {
        compileTest(4, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello, world, ever!!!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
        assertClassesNotPresent(
            "Main"
        )
    }

    @Test
    @DisplayName("Cleaning up the empty package directories")
    fun test5() {
        compileTest(5, clean = true, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello, world!!!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
        )
        assertClassesNotPresent(
            "test.package2.Controller",
        )

        assertFalse(
            classToPath("test.package2.Controller").parent.exists,
            "The package 'package2' should be not exists"
        )
    }

    @Test
    @DisplayName("Recompiling with error")
    fun test6() {
        compileTest(6, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(1, 1, 0),
        ))
    }

    @Test
    @DisplayName("Recompiling the call sight on method signature change")
    fun test7() {
        compileTest(7, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(0, 1, 1),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Recompiling the call sight on field signature change")
    fun test8() {
        compileTest(8, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(0, 1, 1),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Not recompiling the call sight on static field value change")
    fun test9() {
        compileTest(9, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello, world!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Not recompiling the call sight on static final field value change")
    fun test10() {
        compileTest(10, compileResults = arrayOf(
            CompileResult(0, 2, 0),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello, world!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Recompiling with error on method signature change on interface inheritance")
    fun test11() {
        compileTest(11, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(1, 1, 1),
        ))
    }

    @Test
    @DisplayName("Recompiling with error on method signature change on class inheritance")
    fun test12() {
        compileTest(12, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(1, 1, 1),
        ))
    }

    @Test
    @DisplayName("Escape circular recompilation")
    fun test13() {
        compileTest(13, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(0, 1, 1),
        ))
        assertEquals("Hello world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Messager",
            "test.package2.Messager2",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("If the actual changes not present then escape incremental compilation")
    fun test14() {
        compileTest(14, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Messager",
            "test.package2.Messager2",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Incremental recompile on appear generic type in base class")
    fun test15() {
        compileTest(15, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(1, 1, 1),
            CompileResult(0, 1, 0),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Messager",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Incremental recompile on base class generic change")
    fun test16() {
        compileTest(16, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            // TODO: In theory, recompilation in phase 2 can be avoided here
            CompileResult(0, 1, 1),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Messager",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Incremental recompile on typing change of class generic")
    fun test17() {
        compileTest(17, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(0, 1, 1),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Messager",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Incremental recompile on typing change of method generic")
    fun test18() {
        compileTest(18, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            // TODO: In theory, recompilation in phase 2 can be avoided here
            CompileResult(0, 1, 1),
            CompileResult(1, 1, 1),
            CompileResult(0, 1, 1),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Messager",
            "test.package2.Controller",
        )
    }

    @Test
    @DisplayName("Error on annotation lost on class")
    fun test19() {
        compileTest(19, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(1, 1, 0),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
        assertClassesNotPresent(
            "test.package2.Metadata",
        )
    }

    @Test
    @DisplayName("Error on annotation lost on method")
    fun test20() {
        compileTest(20, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(1, 1, 0),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
        assertClassesNotPresent(
            "test.package2.Metadata",
        )
    }

    @Test
    @DisplayName("Error on annotation lost on parameters")
    fun test21() {
        compileTest(21, compileResults = arrayOf(
            CompileResult(0, 3, 0),
            CompileResult(1, 1, 0),
        ))
        assertEquals("Hello, world\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
        assertClassesNotPresent(
            "test.package2.Metadata",
        )
    }

    private fun compileTest(
        testId: Int,
        classpath: String? = null,
        clean: Boolean = false,
        vararg compileResults: CompileResult
    ) {
        println("---Run test: $testId")
        if (SRC_DIR.exists) {
            FileUtils.cleanDirectory(SRC_DIR.toFile())
        }
        if (BUILD_DIR.exists) {
            FileUtils.cleanDirectory(BUILD_DIR.toFile())
        }
        val tinyc = Tinyc()
        for (i in 1..compileResults.size) {
            if (SRC_DIR.exists && clean) {
                FileUtils.cleanDirectory(SRC_DIR.toFile())
            }
            val testDir = TESTS_DIR / "test$testId" / i.toString()

            val deleteLst = testDir / "delete.lst"
            if (deleteLst.exists) {
                deleteByDescr(deleteLst)
            }

            FileUtils.copyDirectory(testDir.toFile(), SRC_DIR.toFile(), false)

            val errorCode = CommandLine(tinyc).setCommandName("tinyc").execute(
                "--sourceroot",
                SRC_DIR.toString(),
                "--builddir",
                BUILD_DIR.toString(),
                * if (classpath != null) arrayOf("--classpath", classpath) else arrayOf()
            )
            assertEquals(
                compileResults[i - 1].filesToCompile1,
                tinyc.compiledFilesPhase1,
                "In phase 1 should be compiled ${compileResults[i - 1].filesToCompile1} files, but compiled ${tinyc.compiledFilesPhase1}"
            )
            assertEquals(
                compileResults[i - 1].filesToCompile2,
                tinyc.compiledFilesPhase2,
                "In phase 2 should be compiled ${compileResults[i - 1].filesToCompile2} files, but compiled ${tinyc.compiledFilesPhase2}"
            )
            assertEquals(
                compileResults[i - 1].errorCode,
                errorCode,
                "Compile error code should be equals ${compileResults[i - 1].errorCode}, but is $errorCode"
            )
            println("------------------")
        }
    }

    private fun deleteByDescr(descrPath: Path) {
        Files
            .readAllLines(descrPath, Charsets.UTF_8)
            .forEach { Files.delete(SRC_DIR / it) }
    }

    private fun runTest(mainClass: String, classpath: String? = null): String {
        return ProcessExecutor()
            .command(
                JAVA_PATH.toString(),
                * if (classpath != null)
                    arrayOf("-classpath", CLASSES_DIR.toString() + File.pathSeparator + classpath)
                else
                    arrayOf("-classpath", CLASSES_DIR.toString()),
                mainClass
            )
            .redirectOutput(Slf4jStream.of(Tinyc::class.java).asInfo())
            .readOutput(true)
            .execute()
            .outputUTF8()
    }

    private fun assertClassesPresent(vararg classes: String) {
        for (clazz in classes) {
            assertTrue(classToPath(clazz).exists, "The class should be exists: $clazz")
        }
    }

    private fun assertClassesNotPresent(vararg classes: String) {
        for (clazz in classes) {
            assertFalse(classToPath(clazz).exists, "The class should not be exists: $clazz")
        }
    }

    private fun classToPath(clazz: String) = CLASSES_DIR / (clazz.replace('.', File.separatorChar) + ".class")

    data class CompileResult(
        val errorCode: Int,
        val filesToCompile1: Int,
        val filesToCompile2: Int
    )

    companion object {
        private val JAVA_PATH = Tinyc().checkEnvironment().javaHome / "bin" / "java"
        private val TEST_CLASS_DIR = path(TinycTest::class.java.protectionDomain.codeSource.location.toURI())
        private val TESTS_DIR = TEST_CLASS_DIR / "tests"
        private val SRC_DIR = (TEST_CLASS_DIR / ".." / "src").normalize()
        private val BUILD_DIR = (TEST_CLASS_DIR / ".." / "build").normalize()
        private val CLASSES_DIR = (BUILD_DIR / "classes").normalize()
    }
}