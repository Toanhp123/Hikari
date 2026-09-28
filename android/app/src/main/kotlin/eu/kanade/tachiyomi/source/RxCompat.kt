package eu.kanade.tachiyomi.source

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun <T> Observable<T>.awaitSingleCompat(): T =
    suspendCancellableCoroutine { continuation ->
        var seen = false
        var value: T? = null
        val subscriber = object : Subscriber<T>() {
            override fun onNext(item: T) {
                if (seen) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Expected one value from legacy extension observable."),
                        )
                    }
                    unsubscribe()
                    return
                }
                seen = true
                value = item
            }

            override fun onCompleted() {
                if (!continuation.isActive) return
                if (!seen) {
                    continuation.resumeWithException(
                        NoSuchElementException("Legacy extension observable completed without a value."),
                    )
                    return
                }
                @Suppress("UNCHECKED_CAST")
                continuation.resume(value as T)
            }

            override fun onError(error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

        continuation.invokeOnCancellation { subscriber.unsubscribe() }
        subscribe(subscriber)
    }
