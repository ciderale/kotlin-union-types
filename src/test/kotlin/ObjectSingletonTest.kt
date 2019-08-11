import com.fasterxml.jackson.module.kotlin.readValue
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ObjectSingletonTest {
    object Singleton {
        var content = 1 // mutable state
    }

    @Test
    fun deserializationPreservesSingletonProperty() {
        val js = mapper.writeValueAsString(Singleton)
        val newSingleton = mapper.readValue<Singleton>(js)
        assertThat(newSingleton, equalTo(Singleton))
    }

    @Test
    fun deserializationResetsSingletonObjectState() {
        // persist current singleton state
        val js = mapper.writeValueAsString(Singleton)
        val initial = Singleton.content

        // mutate the in-memory singleton state
        val after = initial + 1
        Singleton.content = after
        assertThat(Singleton.content, equalTo(after))

        // read back persisted state resets singleton state
        val newSingleton = mapper.readValue<Singleton>(js)
        assertThat(newSingleton.content, equalTo(initial))
        assertThat(Singleton.content, equalTo(initial))
    }

    @Test
    fun deserializedObjectsBehaveLikeSingletons() {
        val js = mapper.writeValueAsString(Singleton)
        val newSingleton = mapper.readValue<Singleton>(js)
        assertThat(newSingleton.content, equalTo(Singleton.content))

        newSingleton.content += 1;

        assertThat(Singleton.content, equalTo(newSingleton.content))
    }

}