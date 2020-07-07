package com.michaelszymczak.sample.sockets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

public class Acceptor implements AutoCloseable
{
    private final long sessionId;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;

    Acceptor(final long sessionId)
    {
        this.sessionId = sessionId;
    }

    long id()
    {
        return sessionId;
    }

    void listen(final int serverPort) throws IOException
    {
        serverSocketChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        serverSocketChannel.bind(new InetSocketAddress(serverPort));
    }

    @Override
    public void close()
    {
        Resources.close(serverSocketChannel);
        Resources.close(selector);
    }

    @Override
    public String toString()
    {
        return "Acceptor{" +
               "sessionId=" + sessionId +
               ", serverSocketChannel=" + serverSocketChannel +
               ", selector=" + selector +
               '}';
    }
}