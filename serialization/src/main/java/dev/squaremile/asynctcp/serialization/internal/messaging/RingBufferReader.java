package dev.squaremile.asynctcp.serialization.internal.messaging;

import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.ringbuffer.RingBuffer;


import dev.squaremile.asynctcp.serialization.internal.SerializedMessageListener;

public class RingBufferReader
{
    private final String role;
    private final RingBuffer ringBuffer;
    private MessageHandler messageHandler;

    public RingBufferReader(final String role, final RingBuffer ringBuffer, final SerializedMessageListener serializedMessageListener)
    {
        this.role = role;
        this.ringBuffer = ringBuffer;
        this.messageHandler = (msgTypeId, buffer, index, length) ->
                // see the corresponding writer for -4 explanation
                serializedMessageListener.onSerialized(buffer, index, length - 4);
    }

    public int read()
    {
        return ringBuffer.read(messageHandler);
    }

    @Override
    public String toString()
    {
        return "RingBufferReader{" +
               "role='" + role + '\'' +
               '}';
    }
}
