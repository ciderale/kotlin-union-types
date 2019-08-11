# Serialization of Kotlin Sealed Case Classes

This repository shows how to serialize and deserialize kotlin sealed cases
classes using Jackson.

The functionality provided by 'jackson-module-kotlin' does a good job for
serialization and deserialization, but has a few inconveniences when it comes
to sealed cases classes.

Namely, the handling of 'object' types and using a custom type naming strategy.
The following sections detail the problem and propose a solution.

## Handling of 'object' Singleton Type

In particular, cases that are declared as 'object' are meant to be singletons.
However, the deserialization yields new object instances which are not
considered equal ('=='). This can cause subtle problem when values are not
compared with the 'is' operator.

This repository provides a BeanDeserializerModifier that ensures that no "new
singletons" are exposed. The `KotlinObjectSingletonDeserializer` uses the
normal deserializer, but always returns the "canonical" singleton object (that
kotlin exposes).

This ensures that there is just one singleton object accessible and yet
deserializes the 'object' internals (in case of mutable state) as without using
`KotlinObjectSingletonDeserializer`.


## TypeId Naming Strategy

Secondly, the deserialization must include a type id in order to allow for a
correct deserialization.

This is possible with the `@JsonTypeInfo` annotation, but can be a bit verbose,
especially when a custom type naming strategy shall be used. This repository
demonstrates how to use a 'marker interface' to choose an appropriate naming
strategy with minimal boiler-plate.

The key idea is that "domain classes" implement a marker interface.  This
defines which classes shall be serialized with type information, without
actually defining the naming strategy. This is very convenient as it is less
verbose than the `@JsonTypInfo` annotation and does not incur a dependency on
the jackson module.

The actual choice of the naming strategy is defined when constructing the
jackson object mapper. Specifically, jacksons 'mixin' functionality is used to
define how instances of the above defined marker interface shall be serialized.

# Usage Example

The `SealedCaseClassesTest` includes a fully runnable example to demonstrate the solution.
There are only two things to be added in main code:
* A marker interface needs to be defined, and
* Sealed Case Classes need to be marked with that interface

This is almost no boiler-plate as can be seen in the following example:

```kotlin
interface Tagged  // marker interface, any name would work

sealed class SealedClass : Tagged { // mark the class
  data class A(val name: String) : SealedClass()
  data class B(val name: Double, val age: Int) : SealedClass()
  object C : SealedClass()
}
```

Note that there is no dependency on the Jackson library which is nice from a
dependency inversion point of view -- the core logic is independent of the
serialization.

In addition, the jackson object mapper needs to be configured to deploy the
actual serialization and deserialization. This is a one time configuration that
is typically included in the server startup procedure.

```kotlin
val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        // handling of type ids in sealed case classes
        .registerModule(SimpleModule().apply {
            setMixInAnnotation(Tagged::class.java,
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
```

Note that this code only depends on the marker interface `Tagged` and _not_ on
the various business objects.  Hence, adding a new business object will not
cause a change in this module.  In other words, the maker interface fully
decouples the domain objects from the serialization logic.
