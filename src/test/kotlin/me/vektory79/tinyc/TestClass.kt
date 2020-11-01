package me.vektory79.tinyc

annotation class TestAnnotation(val str: String = "Hello")

@TestAnnotation
class TestClass : HashMap<String, String>() {
    @TestAnnotation
    val prop = "world"

    @TestAnnotation
    fun method(@TestAnnotation param: String, vararg extra: String): String {
        var result = param.toLowerCase()
        extra.forEach { result += ", $it" }
        return result
    }
}