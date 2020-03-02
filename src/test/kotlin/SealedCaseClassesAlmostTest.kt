import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.readValue
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SealedCaseClassesAlmostTest {
    interface TaggedAlmost

    sealed class SealedClass : TaggedAlmost {
        data class A(val name: String) : SealedClass()
        data class B(val name: Double, val age: Int) : SealedClass()
        object C : SealedClass()
    }

    @Test
    fun testRoundTripPropertyForA() {
        val a = SealedClass.A("Class A")
        testRoundTripProperty(a, """{"tag":"A","name":"Class A"}""")
    }

    @Test
    fun testRoundTripPropertyForB() {
        val b = SealedClass.B(3.14, 23)
        testRoundTripProperty(b, """{"tag":"B","name":3.14,"age":23}""")
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

        // unfortunately, serializing top-level lists does not work
        // problem: the list element type is erased and so the "TaggedAlmost" is lost
        // hence, the type information "C" is not in the serialized json string
        assertThat(mapper.writeValueAsString(all), equalTo("""
           [{"name":"Class A"},{"name":3.14,"age":23},{}]
        """.trimIndent()))
        // and consequently a serialization would fail (thus commented out)
        // assertRoundTrip(all, json)

        // this approach does work however, if explicit type information is provided
        // by using jacksonTypeRef to get serialization of tag
        val json = mapper.writerFor(jacksonTypeRef<List<SealedClass>>())
            .withDefaultPrettyPrinter()
            .writeValueAsString(all)

        assertThat(json, equalTo("""
           [ {
             "tag" : "A",
             "name" : "Class A"
           }, {
             "tag" : "B",
             "name" : 3.14,
             "age" : 23
           }, {
             "tag" : "C"
           } ]
        """.trimIndent()))
        assertThat(mapper.readValue(json), equalTo(all))
    }

    private inline fun <reified T : SealedClass> testRoundTripProperty(value: T, json: String) {
        assertRoundTrip(value, json) // uses the specific type
        assertRoundTrip<SealedClass>(value, json) // uses the base type (ie. not A,B,C)
    }
}