package app.openstory.reader.assets

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.CachePolicy
import coil3.request.Options
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import okio.source

internal class ReaderAssetCoilFetcher(
    private val request: ReaderPageAssetRequest,
    private val options: Options,
    private val requestPage: suspend (ReaderPageAssetRequest) -> ReaderAssetLoadOutcome,
) : Fetcher {
    override suspend fun fetch(): FetchResult = when (val outcome = requestPage(request)) {
        is ReaderAssetLoadOutcome.Local -> SourceFetchResult(
            source = localSource(outcome.lease),
            mimeType = null,
            dataSource = DataSource.DISK,
        )

        is ReaderAssetLoadOutcome.Remote -> SourceFetchResult(
            source = ImageSource(
                source = outcome.payload.bytes().inputStream().source().buffer(),
                fileSystem = options.fileSystem,
            ),
            mimeType = outcome.payload.mimeType,
            dataSource = DataSource.NETWORK,
        )

        is ReaderAssetLoadOutcome.Failure -> throw ReaderAssetLoadException(outcome.failure)
    }

    private fun localSource(lease: ReaderAssetReadLease): ImageSource {
        var stream: InputStream? = null
        var source: BufferedSource? = null
        var ownershipTransferred = false
        try {
            val openedStream = lease.openStream()
            stream = openedStream
            val leaseClosed = AtomicBoolean(false)
            val bufferedSource = object : ForwardingSource(openedStream.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { read ->
                        if (read == -1L) closeOnce()
                    }

                override fun close() = closeOnce()

                private fun closeOnce() {
                    if (leaseClosed.compareAndSet(false, true)) {
                        try {
                            super.close()
                        } finally {
                            lease.close()
                        }
                    }
                }
            }.buffer()
            source = bufferedSource
            return ImageSource(
                source = bufferedSource,
                fileSystem = options.fileSystem,
            ).also { ownershipTransferred = true }
        } finally {
            if (!ownershipTransferred) {
                if (source != null) {
                    source.close()
                } else {
                    try {
                        stream?.close()
                    } finally {
                        lease.close()
                    }
                }
            }
        }
    }

    class Factory internal constructor(
        private val requestPage: suspend (ReaderPageAssetRequest) -> ReaderAssetLoadOutcome,
    ) : Fetcher.Factory<ReaderPageAssetRequest> {
        override fun create(
            data: ReaderPageAssetRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            require(options.diskCachePolicy == CachePolicy.DISABLED) {
                "Reader assets must disable Coil disk cache because RICC owns persistent encoded bytes"
            }
            return ReaderAssetCoilFetcher(data, options, requestPage)
        }
    }
}

internal class ReaderPageAssetKeyer : Keyer<ReaderPageAssetRequest> {
    override fun key(data: ReaderPageAssetRequest, options: Options): String =
        "reader-asset:${data.descriptor.key.hash.value}"
}
