import com.exactpro.th2.common.grpc.MessageID;
import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel;
import com.exactpro.th2.conn.dirty.tcp.core.api.IContext;
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandlerSettings;
import com.exactpro.th2.handler.HttpHandler;
import com.exactpro.th2.handler.HttpHandlerSettings;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestClient implements IChannel {
    private final HttpHandlerSettings httpHandlerSettings;
    private final HttpHandler httpHandler;
    private final List<ByteBuf> queue = new ArrayList<>();
    public boolean isOpen = true;

    TestClient() {
        this.httpHandlerSettings = new HttpHandlerSettings();
        httpHandlerSettings.setPassword("password");
        httpHandlerSettings.setUsername("username");
        HashMap<String, List<String>> defaultHeaders = new HashMap<>();
        List<String> headerValue = new ArrayList<>();
        headerValue.add("gzip,deflate");
        defaultHeaders.put("Accept-Encoding", headerValue);
        httpHandlerSettings.setDefaultHeaders(defaultHeaders);
        IContext<IProtocolHandlerSettings> context = Mockito.mock(IContext.class);
        Mockito.when(context.getSettings()).thenReturn(httpHandlerSettings);
        Mockito.when(context.getChannel()).thenReturn(this);

        this.httpHandler = new HttpHandler(context);
    }

    @Override
    public void open() {

    }

    @NotNull
    @Override
    public MessageID send(@NotNull ByteBuf byteBuf, @NotNull Map<String, String> map, @NotNull IChannel.SendMode sendMode) {
        queue.add(byteBuf);
        return null;
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public void close() {

    }

    public HttpHandlerSettings getFixHandlerSettings() {
        return httpHandlerSettings;
    }

    public HttpHandler getHttpHandler() {
        return httpHandler;
    }

    public List<ByteBuf> getQueue() {
        return queue;
    }

    public void clearQueue() {
        this.queue.clear();
    }

    @NotNull
    @Override
    public InetSocketAddress getAddress() {
        return null;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public void open(@NotNull InetSocketAddress inetSocketAddress, boolean b) {

    }
}
