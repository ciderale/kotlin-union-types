# Serialization of Kotlin Sealed Case Classes

This repository shows how to serialize and deserialize kotlin sealed cases
classes using Jackson. 'jackson-module-kotlin' does a good job in general,
but lacks some functionality for handling sealed case classes. Those issues
can however be resolved with custom configurations.

The goal is to properly handle _sealed case classes_ like the following:
```kotlin
sealed class SealedClass {
  data class A(val name: String) : SealedClass()
  data class B(val name: Double, val age: Int) : SealedClass()
  object C : SealedClass()
}
```
Some desirable properties for an object mapper are:
* (MUST) A value that is serialized and deserialized compares equal to the initial value, i.e.
`assertThat(deserialize(serialized(value)), equalTo(value))`
* (SHOULD) The amount of boiler plate on such domain classes is kept minimal (ideally none at all)
* (COULD) The class name ("A", "B", or "C") is used as-is in the json string, without further prefixes.

## Issues without custom mapper configuration

Without a custom configuration of the object mapper, there are several issues:
* Deserialization of 'kotlin object' creates new instances, ie. 
`assertThat(deserialize(serialized(C)), equalTo(C))` will fail. That means, the must property is not fulfilled.
* Deserialization of a value into `SealedClass` fails because the serialized json value does not contain type information (i.e. is it A, B, or C).

These issues might be resolvable by annotations on `SealedClass` and the subtypes `A`, `B`, and `C`.
However, that would be verbose and clutter an otherwise concise domain representation. 
Therefore, we aim for a solution that works that configures the object mapper to work without annotations.

# Proposed solution for handling sealed cases classes

This repository presents a solution based on a single marker interface that fulfills the three initially
stated properties. The solution has minimal boilerplate and the class naming strategy can be 
configured once and for all case classes. Most importantly, it correctly deserializes an previously serialized value.

The following example demonstrates the solution. A fully runnable example is provided
in `SealedCaseClassesTest`. Implementation details and discussion are provided after
the example.

## Example

Define a single marker interface in your domain module.
```kotlin
interface Tagged  { // any name would work
  // with the desired class naming strategy
  val tag: String get() = this.javaClass.simpleName
}
```
This needs to be done once and can be reused for every sealed case class.

Annotate all your sealed case classes with this marker interface
```kotlin
sealed class SealedClass : Tagged { // mark the class
  data class A(val name: String) : SealedClass()
  data class B(val name: Double, val age: Int) : SealedClass()
  object C : SealedClass()
}
```
Not that this marking is the only thing that needs to be repeated for every case class.
The marking is however almost invisible and thus not very distracting.

Another one-time setup is the configuration of the object mapper.
It ensures the correct handling of kotlin's "object" type and provides the necessary
type information needed for deserialization:
```kotlin
val mapper: ObjectMapper = jacksonObjectMapper()
        // handling of type ids in sealed case classes
        .registerModule(SimpleModule().apply {
            setMixInAnnotation(Tagged::class.java,
                SealedCaseClassesSimpleNameIdMixin::class.java)
        })
        // ensure that kotlin objects are treated as singletons
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

Given this configuration, the mapper is ready to be used, like
```kotlin
  val someList:List<SealedClass> = listOf(
            SealedClass.A("Class A"),
            SealedClass.B(3.14, 23),
            SealedClass.C)
  println(mapper.writeValueAsString(someList))
```
which results in
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

### Pro
- The solution properly serializes and deserializes sealed cases classes (see Tests).
- The solution has almost no boilerplate on the domain classes. 
  Sealed case classes only need to implement the marker interface, no further configuration needed.
- The one-time setup for this solution is small. It suffices to
  * define the marker interface (with default method), and to
  * configure the object mapper, both almost copy-paste activities.
- The configuration effort is independent of the actual number of sealed case classes used.
The maker interface fully decouples the domain objects from the serialization logic.
That means, adding a new case class does not require additional configuration, besides implementing
the marker interface.
- The naming strategy for the included type information can be defined as needed
- The domain code does not depend on the jackson library. This is interesting from a dependency 
inversion point of view -- the core logic is independent of the serialization. However, the marker
interface could also be provided by jackson, if the dependency is not an issue.

### Cons

There is one minor caveat with this solution. 
There is some duplication between the marker interface and the `@JsonTypeInfo` annotation used
on the `SealedCaseClassesSimpleNameIdMixin`. Both, the tag name and the naming strategy must be in-sync. 
This seems to be benign though, as those two things are usually defined once and unlikely to change. 
And even changing them would be simple.

### Other options

Maybe there are other options to achieve the same. If you know a better solution, please let me know.

# Implementation details

## Handling of 'object' Singleton Type

Kotlin 'object' are meant to be singletons. However, the standard jackson
deserialization yields new object instances which are not considered equal ('=='). 
This can cause subtle problem when values are not compared with the 'is' operator.

This repository provides a BeanDeserializerModifier that ensures that no "new
singletons" are exposed. The `KotlinObjectSingletonDeserializer` uses the
normal deserializer, but always returns the "canonical" singleton object (that
kotlin defines).

This ensures that there is just one singleton object accessible and hence
comparison (using `==`) work as expected.

By wrapping the standard deserializer, the 'object' internals (in case of mutable state) 
are deserialized as without using `KotlinObjectSingletonDeserializer`. One can argue that 
object singletons with mutable state should not be serialized directly, but that
discussion is not the scope of this expose. In fact, typical `object` in case classes
are more like an enum constant without mutable member. In that case, deserialization could 
also skip any json content, but the type information.

## @JsonTypeInfo to include type information

This annotation allows to include type information into the serialized value.
This is crucial to deserialize a json object into the appropriate case class.
However, there are two issues that will be addressed in the next two subsections.

First, the type information is not included when type information is lost due to type erasure. 
Second, there are only three built-in naming strategies and none of which uses the inner most class name alone. 
All three naming strategies seem to include to include at least the name of the outer classes 
separated by `$` (e.g. `SealedClass$B`) which is a java specific implementation detail.

### Type information despite type erasure for List<T>

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

### @JsonTypeIdResolver for custom naming strategy

The benefit of sealed case classes is that the possible sub-types are statically known.

This can be leveraged to define almost arbitrary naming strategies. Since all sub types
can be enumerated from the base class (e.g. `SealedClass`), the naming strategy must only
guarantee to differentiate between the possible sub types. That is, it does not have to 
be unique across the entire code base. Hence, neither outer class or package information 
needs to be included.

Hence, it is possible to use just the name of the inner most classes. 
This works as long as all cases of the sealed case class have unique names.
But of course, more elaborated nameing schemes can be deployed as needed.
