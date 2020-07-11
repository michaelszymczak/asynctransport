package com.michaelszymczak.sample.sockets.support;

import java.util.List;

import com.michaelszymczak.sample.sockets.api.events.TransportEvent;
import com.michaelszymczak.sample.sockets.api.TransportEventsListener;

public class SynchronizedTransportEvents implements TransportEventsListener
{
    private final TransportEvents transportEvents = new TransportEvents();

    @Override
    public synchronized void onEvent(final TransportEvent event)
    {
        transportEvents.onEvent(event);
    }

    public synchronized List<TransportEvent> events()
    {
        return transportEvents.events();
    }

    public synchronized <T> T last(final Class<T> clazz)
    {
        return transportEvents.last(clazz);
    }

    public synchronized <T> List<T> all(final Class<T> clazz)
    {
        return transportEvents.all(clazz);
    }
}