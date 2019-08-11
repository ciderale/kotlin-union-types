import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.fasterxml.jackson.databind.module.SimpleModule
import java.lang.reflect.Type


@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "tag")
@JsonTypeIdResolver(value = SealedClassesTypeIdResolver::class)
interface TaggedMix

/** resolve type names to nice names */
private open class SealedClassesTypeIdResolver(
        private val namingStrategy: (Class<*>) -> String = { it.simpleName }
) : TypeIdResolverBase() {
    private var idResolution: Map<String, Type> = mapOf()

    override fun init(base: JavaType) {
        val classes = if (base.isAbstract) {
            base.rawClass.kotlin
                    .sealedSubclasses
                    .map { it.java }
        } else {
            listOf(base.rawClass)
        }
        idResolution = classes.map { namingStrategy(it) to it }.toMap()
    }

    override fun typeFromId(context: DatabindContext, id: String): JavaType =
            context.constructType(idResolution[id])

    override fun idFromValue(value: Any): String = namingStrategy(value.javaClass)

    override fun idFromValueAndType(value: Any, suggestedType: Class<*>?): String = idFromValue(value)

    override fun getMechanism(): JsonTypeInfo.Id = JsonTypeInfo.Id.CUSTOM
}

