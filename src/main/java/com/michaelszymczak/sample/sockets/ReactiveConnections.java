package com.michaelszymczak.sample.sockets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.michaelszymczak.sample.sockets.commands.Command;
import com.michaelszymczak.sample.sockets.commands.Listen;
import com.michaelszymczak.sample.sockets.events.EventsListener;
import com.michaelszymczak.sample.sockets.events.StartedListening;

public class ReactiveConnections implements AutoCloseable
{
    private final List<Acceptor> acceptors = new ArrayList<>(10);
    private final SessionIdSource sessionIdSource = new SessionIdSource();
    private final EventsListener eventsListener;

    public ReactiveConnections(final EventsListener eventsListener)
    {

        this.eventsListener = eventsListener;
    }

    public void handle(final Command command)
    {
        if (command instanceof Listen)
        {
            final Listen cmd = (Listen)command;
            listen(cmd.commandId(), cmd.port());
        }
    }

    private long listen(final long currentRequestId, final int serverPort)
    {
        final long sessionId = sessionIdSource.newId();
        final Acceptor acceptor = new Acceptor(sessionId);
        try
        {
            acceptor.listen(serverPort);
        }
        catch (IOException e)
        {
            Resources.close(acceptor);
            throw new RuntimeException(e); // TODO: error as an event
        }
        acceptors.add(acceptor);
        eventsListener.onEvent(new StartedListening(currentRequestId, sessionId));
        return sessionId;

    }

    public void doWork()
    {
//        final int availableCount;
//        try
//        {
//            availableCount = selector.selectNow();
//
//            if (availableCount > 0)
//            {
//                final Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
//                while (keyIterator.hasNext())
//                {
//                    final SelectionKey key = keyIterator.next();
//                    keyIterator.remove();
//                    if (!key.isValid())
//                    {
//                        continue;
//                    }
//                    if (key.isAcceptable())
//                    {
//                    }
//                }
//            }
//        }
//        catch (IOException e)
//        {
//            throw new RuntimeException(e);
//        }
    }

    @Override
    public void close()
    {
        for (int k = 0; k < acceptors.size(); k++)
        {
            final Acceptor acceptor = acceptors.get(k);
            Resources.close(acceptor);
        }
    }

    public long stopListening(final long listenRequestId)
    {
        for (int k = 0; k < acceptors.size(); k++)
        {
            if (acceptors.get(k).id() == listenRequestId)
            {
                Resources.close(acceptors.get(k));
                return 0L;
            }
        }
        return -1L;
    }
}
