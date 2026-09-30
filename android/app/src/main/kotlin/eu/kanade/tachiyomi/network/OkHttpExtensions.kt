@file:Suppress("Unused")

package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import rx.Observable
import rx.subscriptions.Subscriptions
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun Call.asObservable(): Observable<Response> = Observable.create { subscriber ->
    subscriber.add(Subscriptions.create { cancel() })
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (!subscriber.isUnsubscribed) subscriber.onError(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (subscriber.isUnsubscribed) {
                    response.close()
                    return
                }
                subscriber.onNext(response)
                subscriber.onCompleted()
            }
        },
    )
}

fun Call.asObservableSuccess(): Observable<Response> = asObservable().map { response ->
    if (response.isSuccessful) return@map response
    val code = response.code
    response.close()
    throw HttpException(code)
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        },
    )
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (response.isSuccessful) return response
    val code = response.code
    response.close()
    throw HttpException(code)
}
