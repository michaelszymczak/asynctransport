package com.michaelszymczak.sample.sockets.nonblockingimpl;

import com.michaelszymczak.sample.sockets.api.commands.ConnectionCommand;
import com.michaelszymczak.sample.sockets.connection.Channel;
import com.michaelszymczak.sample.sockets.connection.Connection;
import com.michaelszymczak.sample.sockets.connection.ConnectionConfiguration;
import com.michaelszymczak.sample.sockets.connection.ConnectionEventsListener;
import com.michaelszymczak.sample.sockets.connection.ConnectionState;

public class ConnectionImpl implements AutoCloseable, Connection
{

    private Connection delegate;

    ConnectionImpl(final ConnectionConfiguration configuration, final Channel channel, final ConnectionEventsListener eventsListener)
    {
        delegate = new ValidatedConnection(
                configuration.connectionId,
                new SingleConnectionEvents(
                        eventsListener,
                        configuration.connectionId.port(),
                        configuration.connectionId.connectionId(),
                        configuration.maxInboundMessageSize
                ),
                new ChannelBackedConnection(
                        configuration,
                        channel,
                        new SingleConnectionEvents(
                                eventsListener,
                                configuration.connectionId.port(),
                                configuration.connectionId.connectionId(),
                                configuration.maxInboundMessageSize
                        )
                )
        );
    }

    @Override
    public int port()
    {
        return delegate.port();
    }

    @Override
    public long connectionId()
    {
        return delegate.connectionId();
    }

    @Override
    public boolean handle(final ConnectionCommand command)
    {
        return delegate.handle(command);
    }

    @Override
    public boolean isClosed()
    {
        return delegate.isClosed();
    }

    @Override
    public <C extends ConnectionCommand> C command(final Class<C> commandType)
    {
        return delegate.command(commandType);
    }

    @Override
    public ConnectionState state()
    {
        return delegate.state();
    }

    @Override
    public void close() throws Exception
    {
        delegate.close();
    }

    @Override
    public String toString()
    {
        return delegate.toString();
    }

}
