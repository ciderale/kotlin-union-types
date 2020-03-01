import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver

/** use the simple name as TypeId information in jackson serialization.
 *
 * usage:
 * - define an 'interface Tagged' to tag selected types
 * - the interface needs a default method 'val tag: String get() = this.javaClass.simpleName'
 * - register a mixin for that marker interface in jackson:
 * jacksonObjectMapper().registerModule(SimpleModule().apply {
 *   setMixInAnnotation(
 *     Tagged::class.java,
 *     SealedCaseClassesSimpleNameIdMixin::class.java)
 * })
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    property = "tag",
    include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonTypeIdResolver(value = SealedCaseClassesSimpleNameIdResolver::class)
interface SealedCaseClassesSimpleNameIdMixin

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
interface SealedCaseClassesAlmostSimpleNameIdMixin

object SealedCaseClassesSimpleNameIdResolver : SealedCaseClassesTypeIdResolver() {
    override fun namingStrategy(clazz: Class<*>): String = clazz.simpleName
}