package dev.squaremile.asynctcp.playground;

import org.agrona.DirectBuffer;


import dev.squaremile.asynctcp.api.app.Application;
import dev.squaremile.asynctcp.api.app.Event;
import dev.squaremile.asynctcp.serialization.MessageDrivenApplication;
import dev.squaremile.asynctcp.serialization.TransportEventsDeserialization;

public class MessageOnlyDrivenApplication implements MessageDrivenApplication
{
    private final Application application;
    private final TransportEventsDeserialization deserialization;

    public MessageOnlyDrivenApplication(final Application application)
    {

        this.application = application;
        this.deserialization = new TransportEventsDeserialization(application::onEvent);
    }

    @Override
    public void onSerializedEvent(final DirectBuffer buffer, final int offset)
    {
        deserialization.onSerializedEvent(buffer, offset);
    }

    @Override
    public void onEvent(final Event event)
    {
        throw new UnsupportedOperationException("There should be no need to send events to the app directly");
    }

    @Override
    public void onStart()
    {
        application.onStart();
    }

    @Override
    public void onStop()
    {
        application.onStop();
    }

    @Override
    public void work()
    {
        application.work();
    }
}
