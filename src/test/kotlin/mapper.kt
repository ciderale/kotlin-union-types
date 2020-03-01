import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert

val mapper: ObjectMapper = jacksonObjectMapper()
    .configure(SerializationFeature.INDENT_OUTPUT, false)
    // handling of type ids in sealed case classes
    .registerModule(SimpleModule().apply {
        setMixInAnnotation(
            SealedCaseClassesTest.Tagged::class.java,
            SealedCaseClassesSimpleNameIdMixin::class.java)
        setMixInAnnotation(
            SealedCaseClassesAlmostTest.TaggedAlmost::class.java,
            SealedCaseClassesAlmostSimpleNameIdMixin::class.java)
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


inline fun <reified T> assertRoundTrip(a: T, json: String) {
    val ja = mapper.writeValueAsString(a)
    MatcherAssert.assertThat(ja, CoreMatchers.equalTo(json))
    val oa = mapper.readValue<T>(ja)
    MatcherAssert.assertThat(oa, CoreMatchers.equalTo<T>(a))
}