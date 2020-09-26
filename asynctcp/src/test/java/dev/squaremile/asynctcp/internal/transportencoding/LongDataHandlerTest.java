package dev.squaremile.asynctcp.internal.transportencoding;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


import dev.squaremile.asynctcp.api.events.DataReceived;
import dev.squaremile.asynctcp.api.values.ConnectionIdValue;

import static java.nio.ByteBuffer.wrap;

class LongDataHandlerTest
{
    private MessageReceivedSpy messageReceivedSpy = new MessageReceivedSpy();
    private DataReceived dataReceived = new DataReceived(8888, 1, 0, 0, 100, wrap(new byte[100]));
    private LongDataHandler handler = new LongDataHandler(new ConnectionIdValue(8888, 1), messageReceivedSpy);

    @Test
    void shouldNotNotifyOnNoData()
    {
        dataReceived.prepare();
        dataReceived.commit(0, 0);

        // When
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).isEmpty();
    }

    @Test
    void shouldNotNotifyOnInsufficientData()
    {
        ByteBuffer buffer = dataReceived.prepare();
        buffer.put((byte)1).put((byte)2).put((byte)3);
        dataReceived.commit(3, 3);

        // When
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).isEmpty();
    }

    @Test
    void shouldNotifyAboutReceivedLong()
    {
        dataReceived.prepare().putLong(Long.MAX_VALUE);
        dataReceived.commit(8, 8);

        // When
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).hasSize(1);
        assertThat(messageReceivedSpy.asPdus().get(0)).hasSize(8);
        assertThat(wrap(messageReceivedSpy.asPdus().get(0)).getLong()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void shouldNotifyAboutReceivedFullLongsOnly()
    {
        ByteBuffer buffer = dataReceived.prepare();
        buffer.putLong(1295619689L);
        buffer.put((byte)1).put((byte)2);
        dataReceived.commit(10, 10);

        // When
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).hasSize(1);
        assertThat(messageReceivedSpy.asPdus().get(0)).hasSize(8);
        assertThat(wrap(messageReceivedSpy.asPdus().get(0)).getLong()).isEqualTo(1295619689L);
    }

    @Test
    void shouldHandleMultipleLongsSent()
    {
        // When
        dataReceived.prepare().putLong(1);
        dataReceived.commit(8, 8);
        handler.onDataReceived(dataReceived);
        dataReceived.prepare().putLong(3);
        dataReceived.commit(8, 8);
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).hasSize(2);
        assertThat(wrap(messageReceivedSpy.asPdus().get(0)).getLong()).isEqualTo(1);
        assertThat(wrap(messageReceivedSpy.asPdus().get(1)).getLong()).isEqualTo(3);
    }

    @Test
    void shouldHandleMultipleLongsEncodedBackToBack()
    {
        dataReceived.prepare().putLong(1).putLong(2).putLong(3);
        dataReceived.commit(8 * 3, 8 * 3);

        // When
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).hasSize(3);
        assertThat(wrap(messageReceivedSpy.asPdus().get(0)).getLong()).isEqualTo(1);
        assertThat(wrap(messageReceivedSpy.asPdus().get(1)).getLong()).isEqualTo(2);
        assertThat(wrap(messageReceivedSpy.asPdus().get(2)).getLong()).isEqualTo(3);
    }

    @Test
    void shouldReassembleLongs()
    {
        // When
        byte[] array = new byte[24];
        ByteBuffer source = wrap(array);
        source.putLong(Long.MIN_VALUE).putLong(200).putLong(Long.MAX_VALUE);

        // When
        ByteBuffer buffer1 = dataReceived.prepare();
        buffer1.put(array[0]);
        dataReceived.commit(1, 1);
        handler.onDataReceived(dataReceived);

        ByteBuffer buffer2 = dataReceived.prepare();
        buffer2.put(array[1]);
        buffer2.put(array[2]);
        buffer2.put(array[3]);
        buffer2.put(array[4]);
        buffer2.put(array[5]);
        buffer2.put(array[6]);
        buffer2.put(array[7]);
        buffer2.put(array[8]);
        buffer2.put(array[9]);
        buffer2.put(array[10]);
        dataReceived.commit(10, 10);
        handler.onDataReceived(dataReceived);

        ByteBuffer buffer3 = dataReceived.prepare();
        buffer3.put(array[11]);
        buffer3.put(array[12]);
        buffer3.put(array[13]);
        buffer3.put(array[14]);
        buffer3.put(array[15]);
        buffer3.put(array[16]);
        buffer3.put(array[17]);
        buffer3.put(array[18]);
        buffer3.put(array[19]);
        buffer3.put(array[20]);
        buffer3.put(array[21]);
        buffer3.put(array[22]);
        buffer3.put(array[23]);
        dataReceived.commit(13, 13);
        handler.onDataReceived(dataReceived);

        // Then
        assertThat(messageReceivedSpy.all()).hasSize(3);
        assertThat(wrap(messageReceivedSpy.asPdus().get(0)).getLong()).isEqualTo(Long.MIN_VALUE);
        assertThat(wrap(messageReceivedSpy.asPdus().get(1)).getLong()).isEqualTo(200);
        assertThat(wrap(messageReceivedSpy.asPdus().get(2)).getLong()).isEqualTo(Long.MAX_VALUE);
    }
}