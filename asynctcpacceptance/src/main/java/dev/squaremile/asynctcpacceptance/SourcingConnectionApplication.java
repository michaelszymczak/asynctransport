package dev.squaremile.asynctcpacceptance;

import java.util.concurrent.TimeUnit;

import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;


import dev.squaremile.asynctcp.api.AsyncTcp;
import dev.squaremile.asynctcp.transport.api.app.ApplicationOnDuty;
import dev.squaremile.asynctcp.transport.api.app.CommandFailed;
import dev.squaremile.asynctcp.transport.api.app.ConnectionApplication;
import dev.squaremile.asynctcp.transport.api.app.ConnectionEvent;
import dev.squaremile.asynctcp.transport.api.app.ConnectionTransport;
import dev.squaremile.asynctcp.transport.api.commands.SendMessage;
import dev.squaremile.asynctcp.transport.api.events.MessageReceived;
import dev.squaremile.asynctcp.transport.api.values.ConnectionId;
import dev.squaremile.asynctcp.transport.api.values.ConnectionIdValue;

import static dev.squaremile.asynctcp.api.FactoryType.NON_PROD_GRADE;
import static dev.squaremile.asynctcp.transport.api.values.Delineation.fixedLengthDelineation;
import static dev.squaremile.asynctcpacceptance.AdHocProtocol.NO_OPTIONS;
import static dev.squaremile.asynctcpacceptance.AdHocProtocol.PLEASE_RESPOND_FLAG;
import static java.lang.Integer.parseInt;
import static java.lang.System.nanoTime;

public class SourcingConnectionApplication implements ConnectionApplication
{

    private final ConnectionId connectionId;
    private final ConnectionTransport connectionTransport;
    private final int totalMessagesToSend;
    private final int totalMessagesToReceive;
    private final MutableBoolean isDone;
    private final long messageDelayNs;
    private final OnMessageReceived onMessageReceived;
    private final SelectiveResponseRequest selectiveResponseRequest = new SelectiveResponseRequest(1);
    long messagesSentCount = 0;
    long awaitingResponsesInFlight = 0;
    long messagesReceivedCount = 0;
    private long startedSendingTimestampNanos = Long.MIN_VALUE;

    public SourcingConnectionApplication(
            final ConnectionId connectionId,
            final ConnectionTransport connectionTransport,
            final int totalMessagesToSend,
            final int totalMessagesToReceive,
            final MutableBoolean isDone,
            final int sendingRatePerSecond,
            final OnMessageReceived onMessageReceived
    )
    {
        this.connectionId = new ConnectionIdValue(connectionId);
        this.connectionTransport = connectionTransport;
        this.totalMessagesToSend = totalMessagesToSend;
        this.totalMessagesToReceive = totalMessagesToReceive;
        this.isDone = isDone;
        this.messageDelayNs = TimeUnit.SECONDS.toNanos(1) / sendingRatePerSecond;
        this.onMessageReceived = onMessageReceived;
    }

    public static void main(String[] args)
    {
        if (args.length != 5)
        {
            System.out.println("Usage: remoteHost remotePort sendingRatePerSecond warmUpMessages measuredMessages");
            System.out.println("e.g. localhost 8889 48000 400000 4000000");
            return;
        }
        String remoteHost = args[0];
        final int remotePort = parseInt(args[1]);
        final int sendingRatePerSecond = parseInt(args[2]);
        final int warmUpMessages = parseInt(args[3]);
        final int measuredMessages = parseInt(args[4]);
        final String description = String.format(
                "remoteHost %s, remotePort %d, sendingRatePerSecond %d, warmUpMessages %d , measuredMessages %d",
                remoteHost, remotePort, sendingRatePerSecond, warmUpMessages, measuredMessages
        );
        System.out.println("Starting with " + description);
        start(description, remoteHost, remotePort, sendingRatePerSecond, warmUpMessages, measuredMessages);
    }

    public static void start(final String description, final String remoteHost, final int remotePort, final int sendingRatePerSecond, final int warmUp, final int measuredMessages)
    {
        final Measurements measurements = new Measurements(description, warmUp + 1);
        final MutableBoolean isDone = new MutableBoolean(false);
        final int totalMessagesToSend = warmUp + measuredMessages;
        final int totalMessagesToReceive = totalMessagesToSend;
        final ApplicationOnDuty source = new AsyncTcp().transportAppFactory(NON_PROD_GRADE).create(
                "source",
                transport -> new ConnectingApplication(
                        transport,
                        remoteHost,
                        remotePort,
                        fixedLengthDelineation(16),
                        (connectionTransport, connectionId) -> new SourcingConnectionApplication(
                                connectionId,
                                connectionTransport,
                                totalMessagesToSend,
                                totalMessagesToReceive,
                                isDone,
                                sendingRatePerSecond,
                                measurements
                        )
                )
        );

        source.onStart();
        while (!isDone.get())
        {
            source.work();
        }
        source.onStop();

        measurements.printResults();
    }

    @Override
    public ConnectionId connectionId()
    {
        return connectionId;
    }

    @Override
    public void onStart()
    {
    }

    @Override
    public void work()
    {
        if (messagesSentCount < totalMessagesToSend)
        {
            final long nowNs = nanoTime();
            final long expectedTimestampNsToSendThisMessage;
            if (startedSendingTimestampNanos != Long.MIN_VALUE)
            {
                expectedTimestampNsToSendThisMessage = startedSendingTimestampNanos + messagesSentCount * messageDelayNs;
            }
            else
            {
                expectedTimestampNsToSendThisMessage = nowNs;
                startedSendingTimestampNanos = nowNs;
            }
            if (nowNs >= expectedTimestampNsToSendThisMessage)
            {
                boolean askToRespond = selectiveResponseRequest.shouldRespond(messagesSentCount);
                send(expectedTimestampNsToSendThisMessage, askToRespond);
                messagesSentCount++;
                awaitingResponsesInFlight += askToRespond ? 1 : 0;
            }
        }
    }

    @Override
    public void onEvent(final ConnectionEvent event)
    {
        if (event instanceof CommandFailed)
        {
            throw new IllegalStateException(((CommandFailed)event).details());
        }
        if (event instanceof MessageReceived)
        {
            long receivedTimeNs = nanoTime();
            messagesReceivedCount++;
            awaitingResponsesInFlight--;
            MessageReceived messageReceived = (MessageReceived)event;
            long sendTimeNs = messageReceived.buffer().getLong(messageReceived.offset() + 8);
            onMessageReceived.onMessageReceived(messagesSentCount, messagesReceivedCount, sendTimeNs, receivedTimeNs);
            if (messagesReceivedCount == totalMessagesToReceive)
            {
                isDone.set(true);
                if (awaitingResponsesInFlight != 0)
                {
                    throw new IllegalStateException("At this point we should have received all expected responses, " +
                                                    "but " + awaitingResponsesInFlight + " are still in flight");
                }
            }
        }
    }

    private void send(final long supposedSendingTimestampNs, final boolean expectResponse)
    {
        SendMessage message = connectionTransport.command(SendMessage.class);
        MutableDirectBuffer buffer = message.prepare();
        buffer.putLong(message.offset(), expectResponse ? PLEASE_RESPOND_FLAG : NO_OPTIONS);
        buffer.putLong(message.offset() + 8, supposedSendingTimestampNs);
        message.commit(16);
        connectionTransport.handle(message);
    }

    public interface OnMessageReceived
    {
        void onMessageReceived(long messagesSentCount, long messagesReceivedCount, long messageSentTimeNs, long messageReceivedTimeNs);
    }

}
