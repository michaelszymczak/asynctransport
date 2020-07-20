package com.michaelszymczak.sample.sockets.api.events;

import java.util.Arrays;

public class DataReceived implements ConnectionEvent
{
    private final int port;
    private final long connectionId;
    private final long totalBytesReceived;
    private final int length;
    private final byte[] data;

    public DataReceived(final int port, final long connectionId, final long totalBytesReceived, final byte[] data, final int length)
    {
        this.port = port;
        this.connectionId = connectionId;

        this.totalBytesReceived = totalBytesReceived;
        this.data = data;
        this.length = length;
    }

    @Override
    public int port()
    {
        return port;
    }

    @Override
    public long connectionId()
    {
        return connectionId;
    }

    public byte[] data()
    {
        return data;
    }

    public int length()
    {
        return length;
    }

    public long totalBytesReceived()
    {
        return totalBytesReceived;
    }

    @Override
    public String toString()
    {
        return "DataReceived{" +
               "port=" + port +
               ", connectionId=" + connectionId +
               ", totalBytesReceived=" + totalBytesReceived +
               ", length=" + length +
               ", data=" + Arrays.toString(data) +
               '}';
    }

    @Override
    public TransportEvent copy()
    {
        return new DataReceived(port, connectionId, totalBytesReceived, Arrays.copyOf(data, data.length), length);
    }
}
