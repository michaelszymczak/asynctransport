package dev.squaremile.asynctcp.serialization;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;


import dev.squaremile.asynctcp.api.app.ConnectionUserCommand;
import dev.squaremile.asynctcp.api.app.Transport;
import dev.squaremile.asynctcp.api.app.TransportCommand;
import dev.squaremile.asynctcp.api.app.TransportEvent;
import dev.squaremile.asynctcp.api.app.TransportEventsListener;
import dev.squaremile.asynctcp.api.app.TransportUserCommand;
import dev.squaremile.asynctcp.api.commands.CloseConnection;
import dev.squaremile.asynctcp.api.commands.Connect;
import dev.squaremile.asynctcp.api.commands.Listen;
import dev.squaremile.asynctcp.api.commands.SendData;
import dev.squaremile.asynctcp.api.commands.StopListening;
import dev.squaremile.asynctcp.api.events.Connected;
import dev.squaremile.asynctcp.api.events.ConnectionAccepted;
import dev.squaremile.asynctcp.api.events.ConnectionClosed;
import dev.squaremile.asynctcp.api.events.ConnectionResetByPeer;
import dev.squaremile.asynctcp.api.values.ConnectionId;
import dev.squaremile.asynctcp.api.values.ConnectionIdValue;
import dev.squaremile.asynctcp.internal.domain.CommandFactory;
import dev.squaremile.asynctcp.internal.domain.connection.ConnectionCommands;
import dev.squaremile.asynctcp.sbe.CloseConnectionEncoder;
import dev.squaremile.asynctcp.sbe.ConnectEncoder;
import dev.squaremile.asynctcp.sbe.ListenEncoder;
import dev.squaremile.asynctcp.sbe.MessageHeaderEncoder;
import dev.squaremile.asynctcp.sbe.SendDataEncoder;
import dev.squaremile.asynctcp.sbe.StopListeningEncoder;
import dev.squaremile.asynctcp.sbe.VarDataEncodingEncoder;

public class SerializingTransport implements Transport, TransportEventsListener
{
    private final MutableDirectBuffer buffer;
    private final int offset;
    private final SerializedCommandListener serializedCommandListener;
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final CloseConnectionEncoder closeConnectionEncoder = new CloseConnectionEncoder();
    private final ListenEncoder listenEncoder = new ListenEncoder();
    private final StopListeningEncoder stopListeningEncoder = new StopListeningEncoder();
    private final SendDataEncoder sendDataEncoder = new SendDataEncoder();
    private final ConnectEncoder connectEncoder = new ConnectEncoder();
    private final Long2ObjectHashMap<ConnectionCommands> connectionCommandsByConnectionId = new Long2ObjectHashMap<>();
    private final CommandFactory commandFactory = new CommandFactory();
    private final Listen listenCommand;
    private final StopListening stopListeningCommand;
    private final Connect connectCommand;

    public SerializingTransport(final MutableDirectBuffer buffer, final int offset, final SerializedCommandListener serializedCommandListener)
    {
        this.buffer = buffer;
        this.offset = offset;
        this.serializedCommandListener = serializedCommandListener;
        this.listenCommand = commandFactory.create(Listen.class);
        this.stopListeningCommand = commandFactory.create(StopListening.class);
        this.connectCommand = commandFactory.create(Connect.class);
    }

    @Override
    public void work()
    {

    }

    @Override
    public void close()
    {

    }

    @Override
    public <C extends TransportUserCommand> C command(final Class<C> commandType)
    {
        if (commandType.equals(Listen.class))
        {
            return commandType.cast(listenCommand);
        }
        if (commandType.equals(Connect.class))
        {
            return commandType.cast(connectCommand);
        }
        if (commandType.equals(StopListening.class))
        {
            return commandType.cast(stopListeningCommand);
        }
        return commandFactory.create(commandType);
    }

    @Override
    public <C extends ConnectionUserCommand> C command(final ConnectionId connectionId, final Class<C> commandType)
    {
        if (!connectionCommandsByConnectionId.containsKey(connectionId.connectionId()))
        {
            throw new IllegalArgumentException("Connection id " + connectionId + " does not exist");
        }
        return connectionCommandsByConnectionId.get(connectionId.connectionId()).command(commandType);
    }

    @Override
    public void handle(final TransportCommand unknownCommand)
    {
        if (unknownCommand instanceof CloseConnection)
        {
            CloseConnection command = (CloseConnection)unknownCommand;
            closeConnectionEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
                    .port(command.port())
                    .connectionId(command.connectionId())
                    .commandId(command.commandId());
            serializedCommandListener.onSerializedCommand(buffer, offset, headerEncoder.encodedLength() + closeConnectionEncoder.encodedLength());
        }
        if (unknownCommand instanceof Connect)
        {
            Connect command = (Connect)unknownCommand;
            connectEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
                    .remotePort(command.remotePort())
                    .commandId(command.commandId())
                    .timeoutMs(command.timeoutMs())
                    .encoding(command.encodingName())
                    .remoteHost(command.remoteHost());
            serializedCommandListener.onSerializedCommand(buffer, offset, headerEncoder.encodedLength() + connectEncoder.encodedLength());
        }
        if (unknownCommand instanceof Listen)
        {
            Listen command = (Listen)unknownCommand;
            listenEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
                    .port(command.port())
                    .commandId(command.commandId())
                    .encoding(command.encodingName());
            serializedCommandListener.onSerializedCommand(buffer, offset, headerEncoder.encodedLength() + listenEncoder.encodedLength());
        }
        if (unknownCommand instanceof StopListening)
        {
            StopListening command = (StopListening)unknownCommand;
            stopListeningEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
                    .port(command.port())
                    .commandId(command.commandId());
            serializedCommandListener.onSerializedCommand(buffer, offset, headerEncoder.encodedLength() + stopListeningEncoder.encodedLength());
        }
        if (unknownCommand instanceof SendData)
        {
            SendData command = (SendData)unknownCommand;
            sendDataEncoder.wrapAndApplyHeader(buffer, offset, headerEncoder)
                    .port(command.port())
                    .connectionId(command.connectionId())
                    .commandId(command.commandId())
                    .capacity(command.capacity());

            VarDataEncodingEncoder dstData = sendDataEncoder.data();
            ByteBuffer srcBuffer = command.data();
            int srcLength = command.length();
            dstData.length(srcLength);
            int offset = dstData.offset();
            dstData.buffer().putBytes(offset + dstData.encodedLength(), srcBuffer, srcLength);

            serializedCommandListener.onSerializedCommand(this.buffer, this.offset, headerEncoder.encodedLength() + sendDataEncoder.encodedLength());
        }

    }

    @Override
    public void onEvent(final TransportEvent unknownEvent)
    {
        if (unknownEvent instanceof Connected)
        {
            Connected event = (Connected)unknownEvent;
            connectionCommandsByConnectionId.put(
                    event.connectionId(),
                    new ConnectionCommands(new ConnectionIdValue(event.port(), event.connectionId()), event.outboundPduLimit())
            );
        }
        if (unknownEvent instanceof ConnectionAccepted)
        {
            ConnectionAccepted event = (ConnectionAccepted)unknownEvent;
            connectionCommandsByConnectionId.put(
                    event.connectionId(),
                    new ConnectionCommands(new ConnectionIdValue(event.port(), event.connectionId()), event.outboundPduLimit())
            );
        }
        if (unknownEvent instanceof ConnectionClosed)
        {
            ConnectionClosed event = (ConnectionClosed)unknownEvent;
            connectionCommandsByConnectionId.remove(event.connectionId());
        }
        if (unknownEvent instanceof ConnectionResetByPeer)
        {
            ConnectionResetByPeer event = (ConnectionResetByPeer)unknownEvent;
            connectionCommandsByConnectionId.remove(event.connectionId());
        }
    }
}
