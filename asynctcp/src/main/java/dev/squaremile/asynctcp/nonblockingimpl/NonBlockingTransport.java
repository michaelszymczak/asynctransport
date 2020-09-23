package dev.squaremile.asynctcp.nonblockingimpl;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.agrona.CloseHelper;
import org.agrona.LangUtil;
import org.agrona.concurrent.EpochClock;


import dev.squaremile.asynctcp.domain.api.ConnectionId;
import dev.squaremile.asynctcp.domain.api.ConnectionIdValue;
import dev.squaremile.asynctcp.domain.api.Transport;
import dev.squaremile.asynctcp.domain.api.commands.CommandFactory;
import dev.squaremile.asynctcp.domain.api.commands.Connect;
import dev.squaremile.asynctcp.domain.api.commands.ConnectionUserCommand;
import dev.squaremile.asynctcp.domain.api.commands.ConnectionCommand;
import dev.squaremile.asynctcp.domain.api.commands.Listen;
import dev.squaremile.asynctcp.domain.api.commands.NoOpCommand;
import dev.squaremile.asynctcp.domain.api.commands.ReadData;
import dev.squaremile.asynctcp.domain.api.commands.SendData;
import dev.squaremile.asynctcp.domain.api.commands.StopListening;
import dev.squaremile.asynctcp.domain.api.commands.TransportCommand;
import dev.squaremile.asynctcp.domain.api.events.EventListener;
import dev.squaremile.asynctcp.domain.api.events.StartedListening;
import dev.squaremile.asynctcp.domain.api.events.StoppedListening;
import dev.squaremile.asynctcp.domain.api.events.TransportCommandFailed;
import dev.squaremile.asynctcp.domain.connection.Connection;
import dev.squaremile.asynctcp.domain.connection.ConnectionConfiguration;
import dev.squaremile.asynctcp.domain.connection.ConnectionState;
import dev.squaremile.asynctcp.encodings.StandardEncodingsAwareConnectionEventDelegates;

// TODO [perf]: make sure all commands and events can be used without generating garbage
public class NonBlockingTransport implements AutoCloseable, Transport
{
    private final ConnectionIdSource connectionIdSource;
    private final Selector selector = Selector.open();
    private final CommandFactory commandFactory = new CommandFactory();
    private final EventListener eventListener;
    private final Connections connections;
    private final Servers servers;
    private final PendingConnections pendingConnections;
    private final EpochClock clock;
    private final String role;
    private final StandardEncodingsAwareConnectionEventDelegates connectionEventDelegates = new StandardEncodingsAwareConnectionEventDelegates();

    public NonBlockingTransport(final EventListener eventListener, final EpochClock clock, final String role) throws IOException
    {
        this.role = role;
        this.clock = clock;
        this.servers = new Servers(connectionEventDelegates);
        this.connections = new Connections(eventListener::onEvent);
        this.eventListener = eventListener;
        this.pendingConnections = new PendingConnections(clock, eventListener);
        connectionIdSource = new ConnectionIdSource();
    }

    private void handle(final ConnectionUserCommand command)
    {
        Connection connection = connections.get(command.connectionId());
        if (connection == null)
        {
            eventListener.onEvent(new TransportCommandFailed(command, "Connection id not found"));
            return;
        }
        connection.handle(command);
        updateSelectionKeyInterest(connection.state(), connections.getSelectionKey(command.connectionId()));
        if (connection.state() == ConnectionState.CLOSED)
        {
            connections.remove(command.connectionId());
        }
    }

    @Override
    public void work()
    {
        pendingConnections.work();
        try
        {
            if (selector.selectNow() > 0)
            {
                // TODO [perf]: replace this iteration with a zero allocation solution
                final Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext())
                {
                    final SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (!key.isValid())
                    {
                        continue;
                    }
                    if (key.isAcceptable())
                    {
                        int port = ((ListeningSocketConductor)key.attachment()).port();
                        final Server server = servers.serverListeningOn(port);
                        final SocketChannel acceptedSocketChannel = server.acceptChannel();
                        long connectionId = registerConnection(acceptedSocketChannel, server.createConnection(acceptedSocketChannel));
                        connections.get(connectionId).accepted(server.commandIdThatTriggeredListening());
                    }
                    else if (key.isConnectable())
                    {
                        ConnectedNotification connectedNotification = pendingConnections.pendingConnection(key);
                        SocketChannel socketChannel = connectedNotification.socketChannel;
                        try
                        {
                            socketChannel.finishConnect();
                        }
                        catch (ConnectException e)
                        {
                            eventListener.onEvent(new TransportCommandFailed(
                                    connectedNotification.port, connectedNotification.commandId, e.getMessage(), Connect.class
                            ));
                            pendingConnections.removePendingConnection(key);
                            key.cancel();
                        }
                        if (socketChannel.isConnected())
                        {
                            Socket socket = socketChannel.socket();
                            // TODO [perf]: size buffers correctly
                            ConnectionIdValue connectionId = new ConnectionIdValue(socket.getLocalPort(), connectionIdSource.newId());
                            final ConnectionConfiguration configuration = new ConnectionConfiguration(
                                    connectionId,
                                    connectedNotification.remoteHost,
                                    socket.getPort(),
                                    socket.getSendBufferSize(),
                                    // TODO [perf]: decide how to select buffer size (prod and test performance)
                                    socket.getSendBufferSize() * 2,
                                    socket.getReceiveBufferSize()
                            );
                            registerConnection(
                                    socketChannel,
                                    new ConnectionImpl(
                                            configuration,
                                            new SocketBackedChannel(socketChannel),
                                            connectionEventDelegates.createFor(
                                                    connectionId,
                                                    connectedNotification.protocolName,
                                                    eventListener
                                            )
                                    )
                            );
                            connections.get(connectionId.connectionId()).connected(connectedNotification.commandId);
                            pendingConnections.removePendingConnection(key);
                        }
                    }
                    else
                    {
                        ConnectionCommand command = ((ConnectionConductor)key.attachment()).command(key);
                        Connection connection = connections.get(command.connectionId());
                        connection.handle(command);
                        if (connection.state() == ConnectionState.CLOSED)
                        {
                            connections.remove(command.connectionId());
                        }
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handle(final TransportCommand command)
    {
//        System.out.println("T@" + command);
        try
        {
            tryHandle(command);
        }
        catch (Exception e)
        {
            eventListener.onEvent(new TransportCommandFailed(command, e.getMessage() == null || e.getMessage().isEmpty() ? e.getClass().getSimpleName() : e.getMessage()));
        }
        work();
    }

    @Override
    public <C extends TransportCommand> C command(final Class<C> commandType)
    {
        return commandFactory.create(commandType);
    }

    @Override
    public <C extends ConnectionUserCommand> C command(final ConnectionId connectionId, final Class<C> commandType)
    {
        Connection connection = connections.get(connectionId.connectionId());
        return connection != null ? connection.command(commandType) : null;
    }

    private void tryHandle(final TransportCommand command)
    {
        if (command instanceof ConnectionUserCommand)
        {
            handle((ConnectionUserCommand)command);
        }
        else if (command instanceof Listen)
        {
            handle((Listen)command);
        }
        else if (command instanceof StopListening)
        {
            handle((StopListening)command);
        }
        else if (command instanceof Connect)
        {
            handle((Connect)command);
        }
        else
        {
            throw new UnsupportedOperationException(command.getClass().getCanonicalName());
        }
    }

    @Override
    public void close()
    {
        CloseHelper.close(connections);
        CloseHelper.closeAll(servers);
        CloseHelper.close(selector);
    }

    private long registerConnection(final SocketChannel socketChannel, final Connection connection) throws ClosedChannelException
    {
        connections.add(connection, socketChannel.register(
                selector,
                SelectionKey.OP_READ,
                new ConnectionConductor(
                        commandFactory.create(connection, ReadData.class),
                        commandFactory.create(connection, SendData.class),
                        commandFactory.create(connection, NoOpCommand.class)
                )
        ));
        return connection.connectionId();
    }

    private void updateSelectionKeyInterest(final ConnectionState state, final SelectionKey key)
    {
        switch (state)
        {
            case NO_OUTSTANDING_DATA:
                if ((key.interestOps() & SelectionKey.OP_WRITE) != 0)
                {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
                break;
            case DATA_TO_SEND_BUFFERED:
                if ((key.interestOps() & SelectionKey.OP_WRITE) == 0)
                {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                break;
            case CLOSED:
                key.cancel();
                key.attach(null);
                break;
        }
    }

    private void handle(final Listen command)
    {
        if (servers.isListeningOn(command.port()))
        {
            eventListener.onEvent(new TransportCommandFailed(command, "Address already in use"));
            return;
        }
        try
        {
            servers.start(command.port(), command.commandId(), command.encodingName(), connectionIdSource, eventListener, commandFactory);
            Server server = servers.serverListeningOn(command.port());
            final ServerSocketChannel serverSocketChannel = server.serverSocketChannel();
            final SelectionKey selectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            selectionKey.attach(new ListeningSocketConductor(server.port()));
            eventListener.onEvent(new StartedListening(command.port(), command.commandId()));
        }
        catch (IOException e)
        {
            servers.stop(command.port());
            eventListener.onEvent(new TransportCommandFailed(command, e.getMessage()));
        }
    }

    private void handle(final StopListening command)
    {
        if (!servers.isListeningOn(command.port()))
        {
            eventListener.onEvent(new TransportCommandFailed(command, "No listening socket found on this port"));
            return;
        }

        servers.stop(command.port());
        eventListener.onEvent(new StoppedListening(command.port(), command.commandId()));
    }

    private void handle(final Connect command)
    {
        try
        {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            long connectionId = connectionIdSource.newId();
            socketChannel.connect(new InetSocketAddress(command.remoteHost(), command.remotePort()));
            final SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_CONNECT);
            pendingConnections.add(new ConnectedNotification(connectionId, socketChannel, command, clock.time() + command.timeoutMs(), selectionKey, command.encodingName()));
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public String toString()
    {
        return "NonBlockingTransport{" +
               "role='" + role + '\'' +
               '}';
    }
}
