import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.ResolvableDeserializer

fun JsonDeserializer<*>.maybeSingleton(clazz: Class<*>) =
    when (val singleton = objectSingletonInstance(clazz)) {
        null -> this
        else -> this.withSingleton(singleton)
    }

private fun objectSingletonInstance(beanClass: Class<*>): Any? = beanClass.kotlin.objectInstance

private fun JsonDeserializer<*>.withSingleton(singleton: Any) =
    KotlinObjectSingletonDeserializer(singleton, this)

/*
 * deserialize as normal, but return the canonical singleton instance
 */
private class KotlinObjectSingletonDeserializer(
    private val singletonInstance: Any,
    private val defaultDeserializer: JsonDeserializer<*>
) : JsonDeserializer<Any>(),
    // BeanSerializer implements the following interfaces and need to be forwarded
    ContextualDeserializer,
    ResolvableDeserializer {

    override fun resolve(ctxt: DeserializationContext?) {
        if (defaultDeserializer is ResolvableDeserializer) {
            defaultDeserializer.resolve(ctxt)
        }
    }

    override fun createContextual(ctxt: DeserializationContext?, property: BeanProperty?): JsonDeserializer<*> =
        if (defaultDeserializer is ContextualDeserializer) {
            defaultDeserializer.createContextual(ctxt, property)
                .withSingleton(singletonInstance)
        } else {
            this
        }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any {
        defaultDeserializer.deserialize(p, ctxt)
        return singletonInstance
    }
}