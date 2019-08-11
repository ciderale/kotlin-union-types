import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SealedCaseClassesTest {
    private val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        // handling of type ids in sealed case classes
        .registerModule(SimpleModule().apply {
            setMixInAnnotation(
                SealedCaseClassesTest.Tagged::class.java,
                SealedCaseClassesSimpleNameIdMixin::class.java)
        })
        // ensure the kotlin objects are treated as singletons
        .registerModule(SimpleModule().apply {
            setDeserializerModifier(object : BeanDeserializerModifier() {
                override fun modifyDeserializer(
                    config: DeserializationConfig,
                    beanDesc: BeanDescription,
                    deserializer: JsonDeserializer<*>
                ) = super.modifyDeserializer(config, beanDesc, deserializer)
                    .maybeSingleton(beanDesc.beanClass)
            })
        })

    interface Tagged

    sealed class SealedClass : Tagged {
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

    private inline fun <reified T : SealedClass> testRoundTripProperty(value: T, json: String) {
        assertRoundTrip(value, json)
        assertRoundTrip<SealedClass>(value, json)
    }

    private inline fun <reified T> assertRoundTrip(a: T, json: String) {
        val ja = mapper.writeValueAsString(a)
        assertThat(ja, equalTo(json))
        val oa = mapper.readValue<T>(ja)
        assertThat(oa, equalTo<T>(a))
    }
}