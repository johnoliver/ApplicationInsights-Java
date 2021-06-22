package com.microsoft.applicationinsights;

import com.azure.core.http.*;
import com.azure.core.util.tracing.Tracer;
import com.azure.monitor.opentelemetry.exporter.implementation.models.TelemetryItem;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.internal.authentication.AadAuthentication;
import com.microsoft.applicationinsights.internal.channel.common.LazyHttpClient;
import com.microsoft.applicationinsights.internal.persistence.LocalFileWriter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

// TODO performance testing
public class TelemetryChannel {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryChannel.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final AppInsightsByteBufferPool byteBufferPool = new AppInsightsByteBufferPool();

    static {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private final HttpPipeline pipeline;
    private final URL endpoint;
    private final LocalFileWriter localFileWriter;

    public static TelemetryChannel create(URL endpoint, AadAuthentication aadAuthentication, LocalFileWriter localFileWriter) {
        HttpPipeline httpPipeline = LazyHttpClient.newHttpPipeLine(true, aadAuthentication);
        return new TelemetryChannel(httpPipeline, endpoint, localFileWriter);
    }

    public CompletableResultCode sendRawBytes(ByteBuffer buffer) {
        return internalSend(Arrays.asList(buffer));
    }

    // used by tests only
    public TelemetryChannel(HttpPipeline pipeline, URL endpoint, LocalFileWriter localFileWriter) {
        this.pipeline = pipeline;
        this.endpoint = endpoint;
        this.localFileWriter = localFileWriter;
    }

    public CompletableResultCode send(List<TelemetryItem> telemetryItems) {
        List<ByteBuffer> byteBuffers;
        try {
            byteBuffers = encode(telemetryItems);
        } catch (Throwable t) {
            logger.error("Error encoding telemetry items: {}", t.getMessage(), t);
            return CompletableResultCode.ofFailure();
        }
        try {
            return internalSend(byteBuffers);
        } catch (Throwable t) {
            logger.error("Error sending telemetry items: {}", t.getMessage(), t);
            return CompletableResultCode.ofFailure();
        }
    }

    List<ByteBuffer> encode(List<TelemetryItem> telemetryItems) throws IOException {
        ByteBufferOutputStream out = new ByteBufferOutputStream(byteBufferPool);

        try (JsonGenerator jg = mapper.createGenerator(new GZIPOutputStream(out))) {
            for (Iterator<TelemetryItem> i = telemetryItems.iterator(); i.hasNext();) {
                mapper.writeValue(jg, i.next());
                if (i.hasNext()) {
                    jg.writeRaw('\n');
                }
            }
        } catch (IOException e) {
            byteBufferPool.offer(out.getByteBuffers());
            throw e;
        }

        out.close(); // closing ByteBufferOutputStream is a no-op, but this line makes LGTM happy

        List<ByteBuffer> byteBuffers = out.getByteBuffers();
        for (ByteBuffer byteBuffer : byteBuffers) {
            byteBuffer.flip();
        }
        return byteBuffers;
    }

    /**
     * Object can be a list of {@link ByteBuffer} or a raw byte array.
     * Regular telemetries will be sent as {@code List<ByteBuffer>}.
     * Persisted telemetries will be sent as byte[]
     */
    private CompletableResultCode internalSend(List<ByteBuffer> byteBuffers) {
        HttpRequest request = new HttpRequest(HttpMethod.POST, endpoint + "v2.1/track");

        request.setBody(Flux.fromIterable(byteBuffers));
        int contentLength = byteBuffers.stream().mapToInt(ByteBuffer::limit).sum();

        request.setHeader("Content-Length", Integer.toString(contentLength));

        // need to suppress the default User-Agent "ReactorNetty/dev", otherwise Breeze ingestion service will put that
        // User-Agent header into the client_Browser field for all telemetry that doesn't explicitly set it's own
        // UserAgent (ideally Breeze would only have this behavior for ingestion directly from browsers)
        // TODO(trask)
        //  not setting User-Agent header at all would be a better option, but haven't figured out how to do that yet
        request.setHeader("User-Agent", "");
        request.setHeader("Content-Encoding", "gzip");

        // TODO(trask) subscribe with listener
        //  * retry on first failure (may not need to worry about this if retry policy in pipeline already, see above)
        //  * write to disk on second failure
        CompletableResultCode result = new CompletableResultCode();
        List<ByteBuffer> finalByteBuffers = byteBuffers;
        pipeline.send(request)
                .contextWrite(Context.of(Tracer.DISABLE_TRACING_KEY, true))
                .subscribe(response -> {
                    parseResponseCode(response.getStatusCode());
                }, error -> {
                    if (!localFileWriter.writeToDisk(byteBuffers)) {
                        logger.warn("Fail to write {} to disk.", (finalByteBuffers != null ? "List<ByteBuffers>" : "byte[]"));
                        // TODO (heya) track # of write failure via Statsbeat
                    }

                    if (finalByteBuffers != null) {
                        byteBufferPool.offer(finalByteBuffers);
                    }
                    result.fail();
                }, () -> {
                    if (finalByteBuffers != null) {
                        byteBufferPool.offer(finalByteBuffers);
                    }
                    result.succeed();
                });
        return result;
    }

    // TODO (heya) this method name doesn't match what it does
    private static void parseResponseCode(int statusCode) {
        switch (statusCode) {
            // TODO (trask) need constants for these
            case 401:
            case 403: {
                logger.warn("Failed to send telemetry with status code:{}, please check your credentials", statusCode);
                break;
            }
            case 408:
            case 500:
            case 503:
            case BreezeStatusCode.CLIENT_SIDE_EXCEPTION:
                // TODO exponential backoff and retry to a limit
                // TODO (heya) track failure count via Statsbeat
                break;
            case BreezeStatusCode.THROTTLED_OVER_EXTENDED_TIME:
            case BreezeStatusCode.THROTTLED:
                // TODO handle throttling
                // TODO (heya) track throttling count via Statsbeat
                break;
            case BreezeStatusCode.PARTIAL_SUCCESS:
                // TODO handle partial success
                break;
            default:
                // ok
        }
    }
}
