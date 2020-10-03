package dev.squaremile.asynctcpacceptance.sampleapps;


import dev.squaremile.asynctcp.transport.api.app.Application;
import dev.squaremile.asynctcp.transport.api.app.Event;
import dev.squaremile.asynctcp.transport.api.app.EventListener;
import dev.squaremile.asynctcp.transport.api.app.Transport;
import dev.squaremile.asynctcp.transport.api.commands.CloseConnection;
import dev.squaremile.asynctcp.transport.api.commands.Connect;
import dev.squaremile.asynctcp.transport.api.commands.SendData;
import dev.squaremile.asynctcp.transport.api.events.Connected;
import dev.squaremile.asynctcp.transport.api.events.ConnectionClosed;
import dev.squaremile.asynctcp.transport.api.events.ConnectionResetByPeer;
import dev.squaremile.asynctcp.transport.api.values.ConnectionIdValue;

import static dev.squaremile.asynctcp.transport.api.values.PredefinedTransportDelineation.SINGLE_BYTE;
import static java.util.Objects.requireNonNull;

public class ByteMessageSendingApplication implements Application
{
    private final Transport transport;
    private final int remotePort;
    private final byte[] dataToSend;
    private final EventListener eventListener;
    private final byte[] messageContent = new byte[1];
    private String remoteHost;
    private ConnectionIdValue connectionId;
    private long nextCommandId = 1;

    public ByteMessageSendingApplication(
            final Transport transport,
            final String remoteHost,
            final int remotePort,
            final byte[] dataToSend,
            final EventListener eventListener
    )
    {
        this.transport = requireNonNull(transport);
        this.remoteHost = requireNonNull(remoteHost);
        this.remotePort = remotePort;
        this.dataToSend = dataToSend;
        this.eventListener = eventListener;
    }

    @Override
    public void onStart()
    {
        transport.handle(transport.command(Connect.class).set(remoteHost, remotePort, nextCommandId++, 50, SINGLE_BYTE));
    }

    @Override
    public void onStop()
    {
        if (connectionId != null)
        {
            transport.handle(transport.command(connectionId, CloseConnection.class).set(nextCommandId++));
        }
    }

    @Override
    public void onEvent(final Event event)
    {
        eventListener.onEvent(event);
        if (event instanceof Connected)
        {
            Connected connectedEvent = (Connected)event;
            SendData sendDataCommand = transport.command(connectedEvent, SendData.class);
            for (int i = 0; i < dataToSend.length; i++)
            {
                messageContent[0] = dataToSend[i];
                transport.handle(sendDataCommand.set(messageContent));
            }

            connectionId = new ConnectionIdValue(connectedEvent);

        }
        else if (event instanceof ConnectionClosed || event instanceof ConnectionResetByPeer)
        {
            connectionId = null;
        }
    }

    @Override
    public void work()
    {

    }
}
