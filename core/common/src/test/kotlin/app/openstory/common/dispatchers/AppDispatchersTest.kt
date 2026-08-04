package app.openstory.common.dispatchers

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertSame

class AppDispatchersTest {

    @Test
    fun fakeDispatchersExposeProvidedInstances() {
        val dispatcher = StandardTestDispatcher()

        val fake = FixedAppDispatchers(
            io = dispatcher,
            default = dispatcher,
            main = dispatcher,
        )

        assertSame(dispatcher, fake.io)
        assertSame(dispatcher, fake.default)
        assertSame(dispatcher, fake.main)
    }
}
