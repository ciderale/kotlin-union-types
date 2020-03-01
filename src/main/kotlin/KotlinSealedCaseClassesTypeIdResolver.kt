import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import java.lang.reflect.Type

/** Resolves sealedSubclasses based on given 'namingStrategy' */
abstract class SealedCaseClassesTypeIdResolver : TypeIdResolverBase() {
    abstract fun namingStrategy(clazz: Class<*>): String
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
