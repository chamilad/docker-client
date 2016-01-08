package io.fabric8.docker.client.impl;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.ws.WebSocketCall;
import io.fabric8.docker.client.Config;
import io.fabric8.docker.client.DockerClientException;
import io.fabric8.docker.client.InputOutputErrorHandle;
import io.fabric8.docker.client.dsl.container.ContainerErrorOrStreamOrGetLogsInterface;
import io.fabric8.docker.client.dsl.container.ContainerInputOrContainerOutputOrContainerErrorOrStreamOrGetLogsInterface;
import io.fabric8.docker.client.dsl.container.ContainerOutputOrContainerErrorOrStreamOrGetLogsInterface;
import io.fabric8.docker.client.dsl.container.StreamOrGetLogsInterface;
import io.fabric8.docker.client.utils.URLUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.TimeUnit;

public class ContainerAttach extends BaseContainerOperation implements
        ContainerInputOrContainerOutputOrContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle>,
        ContainerOutputOrContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle>,
        ContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle>,
        StreamOrGetLogsInterface<InputOutputErrorHandle> {

    private static final String STDIN = "stdin";
    private static final String STDOUT = "stdout";
    private static final String STDERR = "stderr";
    private static final String STREAM = "stream";
    private static final String LOGS = "logs";

    private final InputStream in;
    private final OutputStream out;
    private final OutputStream err;

    private final PipedOutputStream inPipe;
    private final PipedInputStream outPipe;
    private final PipedInputStream errPipe;

    public ContainerAttach(OkHttpClient client, Config config, String name, InputStream in, OutputStream out, OutputStream err, PipedOutputStream inPipe, PipedInputStream outPipe, PipedInputStream errPipe) {
        super(client, config, name, "attach");
        this.in = in;
        this.out = out;
        this.err = err;
        this.inPipe = inPipe;
        this.outPipe = outPipe;
        this.errPipe = errPipe;
    }

    private InputOutputErrorHandle doAttach(Boolean logs, Boolean stream) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(URLUtils.join(getOperationUrl().toString(), "ws"));

            sb.append("?").append(STREAM).append("=").append(true);
            sb.append("?").append(LOGS).append("=").append(true);
            sb.append("&").append(STDIN).append("=").append(in != null || inPipe != null);
            sb.append("&").append(STDOUT).append("=").append(out != null || outPipe != null);
            sb.append("&").append(STDERR).append("=").append(err != null || errPipe != null);
            Request.Builder r = new Request.Builder().url(sb.toString()).get();
            OkHttpClient clone = client.clone();
            clone.setReadTimeout(0, TimeUnit.MILLISECONDS);
            WebSocketCall webSocketCall = WebSocketCall.create(clone, r.build());
            final ContainerInputOutputErrorHandle handle = new ContainerInputOutputErrorHandle(in, out, err, inPipe, outPipe, errPipe);
            webSocketCall.enqueue(handle);
            handle.waitUntilReady();
            return handle;
        } catch (Throwable t) {
            throw DockerClientException.launderThrowable(t);
        }
    }

    @Override
    public InputOutputErrorHandle getLogs() {
        return doAttach(true, false);
    }

    @Override
    public InputOutputErrorHandle stream() {
        return doAttach(false,true);
    }

    @Override
    public StreamOrGetLogsInterface<InputOutputErrorHandle> readingError(PipedInputStream errPipe) {
        return new ContainerAttach(client, config, name, in, out, err, inPipe, outPipe, errPipe);
    }

    @Override
    public StreamOrGetLogsInterface<InputOutputErrorHandle> writingError(OutputStream err) {
        return new ContainerAttach(client, config, name, in, out, err, inPipe, outPipe, errPipe);
    }

    @Override
    public StreamOrGetLogsInterface<InputOutputErrorHandle> redirectingError() {
        return readingError(new PipedInputStream());
    }

    @Override
    public ContainerOutputOrContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle> readingInput(InputStream in) {
        return new ContainerAttach(client, config, name, in, out, err, inPipe, outPipe, errPipe);
    }

    @Override
    public ContainerOutputOrContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle> writingInput(PipedOutputStream inPipe) {
        return new ContainerAttach(client, config, name, in, out, err, inPipe, outPipe, errPipe);
    }

    @Override
    public ContainerOutputOrContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle> redirectingInput() {
        return writingInput(new PipedOutputStream());
    }

    @Override
    public ContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle> readingOutput(PipedInputStream outPipe) {
        return new ContainerAttach(client, config, name, in, out, err, inPipe, outPipe, errPipe);
    }

    @Override
    public ContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle> writingOutput(OutputStream out) {
        return new ContainerAttach(client, config, name, in, out, err, inPipe, outPipe, errPipe);
    }

    @Override
    public ContainerErrorOrStreamOrGetLogsInterface<InputOutputErrorHandle> redirectingOutput() {
        return readingOutput(new PipedInputStream());
    }
}