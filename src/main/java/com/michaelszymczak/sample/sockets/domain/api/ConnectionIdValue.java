package com.michaelszymczak.sample.sockets.domain.api;

import com.michaelszymczak.sample.sockets.domain.api.commands.ConnectionCommand;

public class ConnectionIdValue implements ConnectionId
{
    private final int port;
    private final long connectionId;

    public ConnectionIdValue(final int port, final long connectionId)
    {
        this.port = port;
        this.connectionId = connectionId;
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

    public String validate(final ConnectionCommand command)
    {
        if (command.connectionId() != connectionId)
        {
            return "Incorrect connection id";
        }
        if (command.port() != port)
        {
            return "Incorrect port";
        }
        return null;
    }
}