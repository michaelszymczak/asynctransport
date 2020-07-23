package com.michaelszymczak.sample.sockets.nonblockingimpl;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.michaelszymczak.sample.sockets.domain.api.ConnectionId;
import com.michaelszymczak.sample.sockets.domain.api.Transport;
import com.michaelszymczak.sample.sockets.domain.api.commands.CommandFactory;
import com.michaelszymczak.sample.sockets.domain.api.commands.ConnectionCommand;
import com.michaelszymczak.sample.sockets.domain.api.commands.Listen;
import com.michaelszymczak.sample.sockets.domain.api.commands.NoOpCommand;
import com.michaelszymczak.sample.sockets.domain.api.commands.ReadData;
import com.michaelszymczak.sample.sockets.domain.api.commands.SendData;
import com.michaelszymczak.sample.sockets.domain.api.commands.StopListening;
import com.michaelszymczak.sample.sockets.domain.api.commands.TransportCommand;
import com.michaelszymczak.sample.sockets.domain.api.events.EventListener;
import com.michaelszymczak.sample.sockets.domain.api.events.StartedListening;
import com.michaelszymczak.sample.sockets.domain.api.events.StoppedListening;
import com.michaelszymczak.sample.sockets.domain.api.events.TransportCommandFailed;
import com.michaelszymczak.sample.sockets.domain.connection.Connection;
import com.michaelszymczak.sample.sockets.domain.connection.ConnectionService;
import com.michaelszymczak.sample.sockets.domain.connection.ConnectionState;

import org.agrona.CloseHelper;
import org.agrona.collections.Long2ObjectHashMap;

import static org.agrona.LangUtil.rethrowUnchecked;

public class NonBlockingTransport implements AutoCloseable, Transport
{
    private final ConnectionIdSource connectionIdSource = new ConnectionIdSource();
    private final List<ListeningSocket> listeningSockets;
    private final Selector acceptingSelector;
    private final Selector connectionsSelector;
    private final ConnectionService connectionService;
    private final CommandFactory commandFactory;
    private final Long2ObjectHashMap<SelectionKey> selectionKeyByConnectionId = new Long2ObjectHashMap<>();
    private final EventListener eventListener;

    public NonBlockingTransport(final EventListener eventListener) throws IOException
    {
        this.eventListener = eventListener;
        this.acceptingSelector = Selector.open();
        this.connectionsSelector = Selector.open();
        this.listeningSockets = new ArrayList<>(10);
        this.commandFactory = new CommandFactory();
        this.connectionService = new ConnectionService(eventListener);
    }

    private void handleConnectionCommand(final ConnectionCommand command)
    {
        ConnectionState state = connectionService.handle(command);
        // means error (connection not found or invalid command)
        if (state == null)
        {
            return;
        }

        SelectionKey key = selectionKeyByConnectionId.get(command.connectionId());
        if (key == null)
        {
            return;
        }
        updateSelectionKeyInterest(state, key);
        if (state == ConnectionState.CLOSED)
        {
            selectionKeyByConnectionId.remove(command.connectionId());
        }
    }

    private void handleTransportCommand(final TransportCommand command) throws Exception
    {
        if (command instanceof Listen)
        {
            handle((Listen)command);
        }
        else if (command instanceof StopListening)
        {
            handle((StopListening)command);
        }
        else
        {
            throw new UnsupportedOperationException(command.getClass().getCanonicalName());
        }
    }

    @Override
    public void work()
    {
        try
        {
            acceptingWork();
            connectionsWork();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handle(final TransportCommand command)
    {
        try
        {
            if (command instanceof ConnectionCommand)
            {
                handleConnectionCommand((ConnectionCommand)command);
            }
            else
            {
                handleTransportCommand(command);
            }
        }
        catch (Exception e)
        {
            eventListener.onEvent(new TransportCommandFailed(command, e.getMessage()));
            rethrowUnchecked(e);
        }
    }

    @Override
    public <C extends TransportCommand> C command(final Class<C> commandType)
    {
        return commandFactory.create(commandType);
    }

    @Override
    public <C extends ConnectionCommand> C command(final ConnectionId connectionId, final Class<C> commandType)
    {
        return connectionService.command(connectionId, commandType);
    }

    private void connectionsWork() throws IOException
    {
        final int availableCount;

        availableCount = connectionsSelector.selectNow();

        if (availableCount > 0)
        {
            final Iterator<SelectionKey> keyIterator = connectionsSelector.selectedKeys().iterator();
            while (keyIterator.hasNext())
            {
                final SelectionKey key = keyIterator.next();
                keyIterator.remove();
                ConnectionCommand command = ((ConnectionConductor)key.attachment()).command(key);
                ConnectionState state = connectionService.handle(command);
                updateSelectionKeyInterest(state, key);
                if (state == ConnectionState.CLOSED)
                {
                    selectionKeyByConnectionId.remove(command.connectionId());
                }
            }
        }
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

    private void acceptingWork() throws IOException
    {
        final int availableCount;

        availableCount = acceptingSelector.selectNow();

        if (availableCount > 0)
        {
            final Iterator<SelectionKey> keyIterator = acceptingSelector.selectedKeys().iterator();
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
                    final ListeningSocket listeningSocket = (ListeningSocket)key.attachment();
                    final SocketChannel acceptedSocketChannel = listeningSocket.acceptChannel();
                    final Connection connection = connectionService.newConnection(listeningSocket.createConnection(acceptedSocketChannel));
                    selectionKeyByConnectionId.put(connection.connectionId(), acceptedSocketChannel.register(
                            connectionsSelector,
                            SelectionKey.OP_READ,
                            new ConnectionConductor(
                                    commandFactory.create(connection, ReadData.class),
                                    commandFactory.create(connection, SendData.class),
                                    commandFactory.create(connection, NoOpCommand.class)
                            )
                    ));
                }
            }
        }
    }

    @Override
    public void close()
    {
        selectionKeyByConnectionId.values().forEach(SelectionKey::cancel);
        selectionKeyByConnectionId.clear();

        CloseHelper.closeAll(listeningSockets);
        CloseHelper.close(acceptingSelector);
        CloseHelper.close(connectionsSelector);
        CloseHelper.close(connectionService);
    }

    private void handle(final Listen command) throws IOException
    {
        final ListeningSocket listeningSocket = new ListeningSocket(command.port(), command.commandId(), connectionIdSource, eventListener, commandFactory);
        try
        {
            listeningSocket.listen();
            final ServerSocketChannel serverSocketChannel = listeningSocket.serverSocketChannel();
            final SelectionKey selectionKey = serverSocketChannel.register(acceptingSelector, SelectionKey.OP_ACCEPT);
            selectionKey.attach(listeningSocket);
        }
        catch (IOException e)
        {
            CloseHelper.close(listeningSocket);
            eventListener.onEvent(new TransportCommandFailed(command, e.getMessage()));
            return;
        }
        listeningSockets.add(listeningSocket);
        eventListener.onEvent(new StartedListening(command.port(), command.commandId()));
    }

    private void handle(final StopListening command)
    {
        for (int k = 0; k < listeningSockets.size(); k++)
        {
            if (listeningSockets.get(k).port() == command.port())
            {
                CloseHelper.close(listeningSockets.get(k));
                eventListener.onEvent(new StoppedListening(command.port(), command.commandId()));
                return;
            }
        }
        eventListener.onEvent(new TransportCommandFailed(command, "No listening socket found on this port"));
    }

}
