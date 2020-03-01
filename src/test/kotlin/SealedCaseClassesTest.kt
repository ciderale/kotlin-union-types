import org.junit.Test

class SealedCaseClassesTest {
    // define one such interface in your domain module (and thus avoid dependency on jackson)
    interface Tagged {
        // this default property must be in-sync with the registered type naming strategy
        // in this concrete case, with 'SealedCaseClassesSimpleNameIdResolver'
        val tag: String get() = this.javaClass.simpleName
    }

    sealed class SealedClass : Tagged {
        data class A(val name: String) : SealedClass()
        data class B(val name: Double, val age: Int) : SealedClass()
        object C : SealedClass()
    }

    @Test
    fun testRoundTripPropertyForA() {
        val a = SealedClass.A("Class A")
        testRoundTripProperty(a, """{"name":"Class A","tag":"A"}""")
    }

    @Test
    fun testRoundTripPropertyForB() {
        val b = SealedClass.B(3.14, 23)
        testRoundTripProperty(b, """{"name":3.14,"age":23,"tag":"B"}""")
    }

    @Test
    fun testRoundTripPropertyForSingleton() {
        val c = SealedClass.C
        testRoundTripProperty(c, """{"tag":"C"}""")
    }

    @Test
    fun testPrettyPrinting() {
        val all:List<SealedClass> = listOf(
            SealedClass.A("Class A"),
            SealedClass.B(3.14, 23),
            SealedClass.C)
        assertRoundTrip(all, """[{"name":"Class A","tag":"A"},{"name":3.14,"age":23,"tag":"B"},{"tag":"C"}]""")
    }

    private inline fun <reified T : SealedClass> testRoundTripProperty(value: T, json: String) {
        assertRoundTrip(value, json) // uses the specific type
        assertRoundTrip<SealedClass>(value, json) // uses the base type (ie. not A,B,C)
    }
}