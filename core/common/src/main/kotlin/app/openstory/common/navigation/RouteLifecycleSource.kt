package app.openstory.common.navigation

import kotlinx.coroutines.flow.Flow

interface RouteLifecycleSource {
    val changes: Flow<RouteLifecycleChange>
}
