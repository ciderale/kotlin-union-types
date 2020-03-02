# Serialization of Kotlin Sealed Case Classes

This repository shows how to serialize and deserialize kotlin sealed cases
classes using Jackson. 'jackson-module-kotlin' does a good job in general,
but lacks some functionality for handling sealed case classes. Those issues
can however be resolved with custom configurations.

The goal is to properly handle sealed case classes like the following:
```kotlin
sealed class SealedClass {
  data class A(val name: String) : SealedClass()
  data class B(val name: Double, val age: Int) : SealedClass()
  object C : SealedClass()
}
```
Some desired requirements are:
* A value that is seralized and deserialized compares equal to the initial value, i.e.
`assertThat(deserialize(serialized(value)), equalTo(value))`
* The amount of boiler plate on such domain classes is kept minimal (ideally none at all)

## Issues without custom mapper configuration

Without a custom configuration of the object mapper, there are several issues:
* Deserialization of 'kotlin object' creates new instances, ie. 
`assertThat(deserialize(serialized(C)), equalTo(C))` will fail.
* Deserialization of a value into `SealedClass` fails because the serialized json value does not contain type information (i.e. is it A, B, or C).

These issues might be resolvable by annotations on `SealedClass` and the subtypes `A`, `B`, and `C`.
However, that would be verbose and clutter an otherwise concise domain representation. 
Therefore, we seek a solution that generally applies the necessary adjustments.

# Proposed solution for handling sealed cases classes

We propose a solution based on a single marker interface. Besides the configuration of 
the object mapper, only a single interface needs to be added to the main code. Sealed
classes only have to be marked with that interface, thus having minimal boilerplate.

The following example demonstrates the solution. A fully runnable example is provided
in `SealedCaseClassesTest`. Implementation details and discussion are provided after
the example.

## Example

Define a marker interface once (any name would work).
```kotlin
interface Tagged  { 
  // with the desired class naming strategy
  val tag: String get() = this.javaClass.simpleName
}
```

Annotate all your sealed case classes with the marker interface
```kotlin
sealed class SealedClass : Tagged { // mark the class
  data class A(val name: String) : SealedClass()
  data class B(val name: Double, val age: Int) : SealedClass()
  object C : SealedClass()
}
```

Configure the object mapper: 
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

Usage:
```kotlin
 val someList:List<SealedClass> = listOf(
            SealedClass.A("Class A"),
            SealedClass.B(3.14, 23),
            SealedClass.C)
  println(mapper.writeValueAsString(someList))
```
results in
```json
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
```

## Discussion

- The solution properly serializes and deserializes sealed cases classes (see Tests).
- The solution has almost no boilerplate on the domain classes. 
  Sealed case classes only need to be annotated, no additional configuration needed.
- The one-time setup for this solution is small. It suffices to
  * define the marker interface (with default method), and to
  * configure the object mapper, both almost copy-paste activities and independent of the actual number of sealed case classes used.
- Adding new business domain classes does not require additional configuration.
The maker interface fully decouples the domain objects from the serialization logic.
- The naming strategy for the included type information can be defined
- The domain code does not depend on the jackson library. This is interesting from a dependency 
inversion point of view -- the core logic is independent of the serialization.
- There is one minor caveat with this solution. 
There is some duplication between the marker interface and the typeid annotation.
Both, the tag name and the naming strategy must be in-sync. This seems to be benign however
as those two things are usually defined once and unlikely to change. And even changing them would be simple.

## Handling of 'object' Singleton Type

Kotlin 'object' are meant to be singletons. However, the standard jackson
deserialization yields new object instances which are not considered equal ('=='). 
This can cause subtle problem when values are not compared with the 'is' operator.

This repository provides a BeanDeserializerModifier that ensures that no "new
singletons" are exposed. The `KotlinObjectSingletonDeserializer` uses the
normal deserializer, but always returns the "canonical" singleton object (that
kotlin exposes).

This ensures that there is just one singleton object accessible and yet
deserializes the 'object' internals (in case of mutable state) as without using
`KotlinObjectSingletonDeserializer`. 

One can argue that object singletons with mutable state
should not be serialized directly. 
Then deserialisation could ignore any content but the type information.
This discussion is however out of scope.

## @JsonTypeInfo annotation

This annotation allows to include type information into the serialized value.
There are 3 builtin options for the generated type information, all having some
downsides:
* CLASS: Fully qualified class names: 
  - The package name might be unnecessary detail
* MINIMAL_CLASS: 
  - includes a leading '.' which seems like an implementation detail for external clients (like js frontent)
  - documentation warns that it may not always work
* NAME:
  - TODO
  
In the concrete case of sealed case classes, we have some additional information to exploit.
The base class knows all potential subclasses due to the "sealed" property. This additional
information can be exploited with a custom naming strategy.

## Type information despite type erasure for List<T>

Due to type erasure, serialization of (top-level) `List<T>` and similar constructs 
requires additional tricks. The problem is that the concrete type `T` will be erased. 
Hence, the sub type information will not always be included in the serialization output. 

There are two solutions to handle this problem:
* provide explicit type information to the serializer 
(`mapper.writerFor(jacksonTypeRef<List<SealedClass>>())`)
* explicitly define type information as class property

The former is maybe cleaner, but maybe not always possible, depending on the web framework in use.
The latter can be achieved with a default implementation in the marker interface. This works well,
but requires a duplication of the naming strategy, which seems to be only a minor caveat.

This issue is demonstrates in `SealedCaseClassesAlmostTest`.
That solution has a true marker interface (without default method)
but fails at serialization of top-level lists without explicitly 
providing type information to the object mapper.

##  TypeId Naming Strategy

TODO - outdated
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
