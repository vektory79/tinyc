package me.vektory79.tinyc

import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestMethodOrder(OrderAnnotation::class)
internal class TinycIT {

    @Test
    @Order(1)
    fun test1() {
        assertEquals(0, compileTest(1), "The compilation should be completed successfully")
        assertEquals("Hello, world!!!\n", runTest("Main"))
        assertClassesPresent(
            "Main",
            "Main$1"
        )
    }

    @Test
    @Order(2)
    fun test2() {
        assertEquals(0, compileTest(2), "The compilation should be completed successfully")
        assertEquals("Hello, world!!!\n", runTest("Main"))
        assertClassesPresent(
            "Main",
        )
        assertClassesNotPresent(
            "Main$1"
        )
    }

    @Test
    @Order(3)
    fun test3() {
        assertEquals(0, compileTest(3), "The compilation should be completed successfully")
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
    @Order(4)
    fun test4() {
        // Ensure, that the file modification is countable between two tests
        Thread.sleep(1)
        assertEquals(0, compileTest(4, clean = false), "The compilation should be completed successfully")
        assertEquals("Hello, world, ever!!!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
            "test.package2.Controller",
        )
        assertClassesNotPresent(
            "Main"
        )

        assertTrue(
            Files.getLastModifiedTime(classToPath("test.package1.Main")) <
            Files.getLastModifiedTime(classToPath("test.package2.Controller")),
            "The class Main should be older, than Controller"
        )
    }

    @Test
    @Order(5)
    fun test5() {
        // Ensure, that the file modification is countable between two tests
        Thread.sleep(1)
        assertEquals(0, compileTest(5), "The compilation should be completed successfully")
        assertEquals("Hello, world!!!\n", runTest("test.package1.Main"))
        assertClassesPresent(
            "test.package1.Main",
        )
        assertClassesNotPresent(
            "test.package2.Controller",
        )

        assertFalse(
            Files.exists(classToPath("test.package2.Controller").parent),
            "The package 'package2' should be not exists"
        )
    }

    private fun compileTest(num: Int, classpath: String? = null, clean: Boolean = true): Int {
        if (Files.exists(SRC_DIR) && clean) {
            FileUtils.cleanDirectory(SRC_DIR.toFile())
        }
        FileUtils.copyDirectory(
            TESTS_DIR.resolve("test$num").toFile(),
            SRC_DIR.toFile(),
            false
        )
        return ProcessExecutor()
            .command(
                JAVA_PATH.toString(),
                "-jar",
                TINYC_JAR.toString(),
                "--sourceroot",
                SRC_DIR.toString(),
                "--builddir",
                BUILD_DIR.toString(),
                * if (classpath != null) arrayOf("--classpath", classpath) else arrayOf()
            )
            .redirectOutput(Slf4jStream.of(Tinyc::class.java).asInfo())
            .readOutput(true)
            .execute()
            .exitValue
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
            assertTrue(Files.exists(classToPath(clazz)), "The class should be exists: $clazz")
        }
    }

    private fun assertClassesNotPresent(vararg classes: String) {
        for (clazz in classes) {
            assertFalse(Files.exists(classToPath(clazz)), "The class should not be exists: $clazz")
        }
    }

    private fun classToPath(clazz: String) = CLASSES_DIR.resolve(clazz.replace('.', File.separatorChar) + ".class")

    companion object {
        private val JAVA_PATH = Tinyc().checkEnvironment().javaHome.resolve("bin").resolve("java")
        private val TEST_CLASS_DIR = Paths
            .get(TinycIT::class.java.protectionDomain.codeSource.location.toURI())
        private val TESTS_DIR = TEST_CLASS_DIR.resolve("tests")
        private val SRC_DIR = TEST_CLASS_DIR
            .resolve("..")
            .resolve("src")
            .normalize()
        private val BUILD_DIR = TEST_CLASS_DIR
            .resolve("..")
            .resolve("build")
            .normalize()
        private val CLASSES_DIR = BUILD_DIR
            .resolve("classes")
            .normalize()
        private val TINYC_JAR = TEST_CLASS_DIR
            .resolve("..")
            .resolve("tinyc.jar")
            .normalize()
    }
}