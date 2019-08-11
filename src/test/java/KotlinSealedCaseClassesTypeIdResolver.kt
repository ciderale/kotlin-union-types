import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import java.lang.reflect.Type

/** use the simple name as TypeId information in jackson serialization.
 *
 * usage:
 * - define an 'interface Tagged' to tag selected types
 * - register a mixin for that marker interface in jackson:
 * jacksonObjectMapper().registerModule(SimpleModule().apply {
 *   setMixInAnnotation(
 *     Tagged::class.java,
 *     SealedCaseClassesSimpleNameIdMixin::class.java)
 * })
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "tag")
@JsonTypeIdResolver(value = SealedCaseClassesSimpleNameIdResolver::class)
interface SealedCaseClassesSimpleNameIdMixin

object SealedCaseClassesSimpleNameIdResolver
    : SealedCaseClassesTypeIdResolver({ it.simpleName })

/** Resolves sealedSubclasses based on given 'namingStrategy' */
open class SealedCaseClassesTypeIdResolver(
    private val namingStrategy: (Class<*>) -> String
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

