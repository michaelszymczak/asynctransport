package dev.squaremile.asynctcp.domain.api.events;

public class ConnectionAccepted implements ConnectionEvent, TransportCorrelatedEvent
{
    private final int port;
    private final long commandId;
    private final int remotePort;
    private final long connectionId;
    private final int inboundPduLimit;
    private final int outboundPduLimit;

    public ConnectionAccepted(
            final int port,
            final long commandId,
            final int remotePort,
            final long connectionId,
            final int inboundPduLimit,
            final int outboundPduLimit
    )
    {
        this.port = port;
        this.commandId = commandId;
        this.remotePort = remotePort;
        this.connectionId = connectionId;
        this.inboundPduLimit = inboundPduLimit;
        this.outboundPduLimit = outboundPduLimit;
    }

    @Override
    public int port()
    {
        return port;
    }

    @Override
    public long commandId()
    {
        return commandId;
    }

    public int remotePort()
    {
        return remotePort;
    }

    @Override
    public long connectionId()
    {
        return connectionId;
    }

    public int inboundPduLimit()
    {
        return inboundPduLimit;
    }

    public int outboundPduLimit()
    {
        return outboundPduLimit;
    }

    @Override
    public String toString()
    {
        return "ConnectionAccepted{" +
               "port=" + port +
               ", commandId=" + commandId +
               ", remotePort=" + remotePort +
               ", connectionId=" + connectionId +
               ", inboundPduLimit=" + inboundPduLimit +
               ", outboundPduLimit=" + outboundPduLimit +
               '}';
    }

    @Override
    public TransportEvent copy()
    {
        return new ConnectionAccepted(port, commandId, remotePort, connectionId, inboundPduLimit, outboundPduLimit);
    }
}