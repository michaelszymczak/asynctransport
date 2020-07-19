package com.michaelszymczak.sample.sockets.connection;

import com.michaelszymczak.sample.sockets.api.ConnectionIdValue;

public class ConnectionConfiguration
{
    public final ConnectionIdValue connectionId;
    public final int remotePort;
    public final int maxMsgSize;
    public final int sendBufferSize;

    public ConnectionConfiguration(final ConnectionIdValue connectionId, final int remotePort, final int maxMsgSize, final int sendBufferSize)
    {
        this.connectionId = connectionId;
        this.remotePort = remotePort;
        this.maxMsgSize = maxMsgSize;
        this.sendBufferSize = sendBufferSize;
    }
}
