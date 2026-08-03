package app.openstory.common.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

data class FixedAppDispatchers(
    override val io: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
    override val main: CoroutineDispatcher,
) : AppDispatchers
