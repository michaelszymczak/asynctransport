package dev.squaremile.asynctcpacceptance;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.agrona.collections.MutableInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


import dev.squaremile.asynctcp.api.transport.commands.SendData;
import dev.squaremile.asynctcp.api.transport.events.Connected;
import dev.squaremile.asynctcp.api.transport.events.ConnectionAccepted;
import dev.squaremile.asynctcp.api.transport.events.DataReceived;
import dev.squaremile.asynctcp.api.transport.events.DataSent;
import dev.squaremile.asynctcp.fixtures.transport.ConnectionEventsSpy;

import static dev.squaremile.asynctcp.fixtures.transport.DataFixtures.concatenatedData;
import static dev.squaremile.asynctcp.fixtures.transport.StringFixtures.byteArrayWith;
import static dev.squaremile.asynctcp.fixtures.transport.StringFixtures.fixedLengthStringStartingWith;
import static dev.squaremile.asynctcp.fixtures.transport.StringFixtures.stringWith;


class ClientReceivesDataTest extends TransportTestBase
{
    private static final int _10_MB_IN_BYTES = 10 * 1024 * 1024;

    @Test
    void shouldReceiveData()
    {
        final TransportDriver driver = new TransportDriver(serverTransport);

        // Given
        final ConnectionAccepted conn = driver.listenAndConnect(clientTransport);

        // When
        serverTransport.handle(new SendData(conn.port(), conn.connectionId(), 3).set(byteArrayWith("foo"), 101));
        spinUntil(() -> !clientTransport.connectionEvents().all(DataReceived.class).isEmpty());

        // Then
        assertThat(stringWith(extractedContent(clientTransport.connectionEvents().all(DataReceived.class)))).isEqualTo("foo");
    }

    @Test
    void shouldEventuallyReceiveAllData()
    {
        final TransportDriver driver = new TransportDriver(serverTransport);

        // Given
        final ConnectionAccepted conn = driver.listenAndConnect(clientTransport);
        final Connected connected = clientTransport.connectionEvents().last(Connected.class);
        final List<byte[]> dataChunksToSend = Arrays.asList(
                byteArrayWith(fixedLengthStringStartingWith("\nfoo", 1_000)),
                byteArrayWith(fixedLengthStringStartingWith("\nbar", 2_000)),
                byteArrayWith(fixedLengthStringStartingWith("\nbazqux", 3_000))
        );
        byte[] wholeDataToSend = concatenatedData(dataChunksToSend);

        // When
        dataChunksToSend.forEach(dataChunkToSend -> serverTransport.handle(new SendData(conn, dataChunkToSend.length).set(dataChunkToSend)));
        spinUntil(bytesReceived(clientTransport.connectionEvents(), connected.connectionId(), wholeDataToSend.length));

        // Then
        assertThat(clientTransport.connectionEvents().all(DataReceived.class, connected.connectionId())).isNotEmpty();
        assertThat(clientTransport.connectionEvents().last(DataReceived.class, connected.connectionId()).totalBytesReceived()).isEqualTo(wholeDataToSend.length);
        assertThat(clientTransport.connectionEvents().all(DataReceived.class, connected.connectionId()).stream().mapToLong(DataReceived::length).sum()).isEqualTo(wholeDataToSend.length);
        assertThat(extractedContent(clientTransport.connectionEvents().all(DataReceived.class, connected.connectionId()))).isEqualTo(wholeDataToSend);
    }

    @Test
    @Tag("tcperror")
    void shouldEventuallyReceiveAllTheDataSentAsLargeChunks()
    {
        final TransportDriver driver = new TransportDriver(serverTransport);

        // Given
        final ConnectionAccepted conn = driver.listenAndConnect(clientTransport);
        final Connected connected = clientTransport.connectionEvents().last(Connected.class);
        final List<byte[]> dataChunksToSend = dataOfAtLeasSize(_10_MB_IN_BYTES, connected.outboundPduLimit());
        byte[] wholeDataToSend = concatenatedData(dataChunksToSend);
        assertThat(wholeDataToSend.length).isGreaterThan(_10_MB_IN_BYTES);

        // When
        dataChunksToSend.forEach(dataChunkToSend ->
                                 {
                                     serverTransport.handle(serverTransport.command(conn.connectionId(), SendData.class).set(dataChunkToSend));
                                     // Make sure all buffered data has been sent before sending more to avoid buffer overflow
                                     while (true)
                                     {
                                         serverTransport.work();
                                         clientTransport.work();
                                         List<DataSent> dataSentEvents = serverTransport.events().all(DataSent.class);
                                         if (dataSentEvents.isEmpty())
                                         {
                                             break;
                                         }
                                         DataSent lastDataSentEvent = serverTransport.events().last(DataSent.class);
                                         if (lastDataSentEvent.totalBytesSent() == lastDataSentEvent.totalBytesBuffered())
                                         {
                                             break;
                                         }
                                     }
                                 });
        spinUntil(bytesReceived(clientTransport.connectionEvents(), connected.connectionId(), wholeDataToSend.length));

        // Then
        assertThat(clientTransport.connectionEvents().all(DataReceived.class, connected.connectionId())).isNotEmpty();
        assertThat(clientTransport.connectionEvents().last(DataReceived.class, connected.connectionId()).totalBytesReceived()).isEqualTo(wholeDataToSend.length);
        assertThat(clientTransport.connectionEvents().all(DataReceived.class, connected.connectionId()).stream().mapToLong(DataReceived::length).sum()).isEqualTo(wholeDataToSend.length);
        byte[] actualReceivedData = extractedContent(clientTransport.connectionEvents().all(DataReceived.class, connected.connectionId()));
        assertThat(actualReceivedData).isEqualTo(wholeDataToSend);
        assertThat(actualReceivedData.length).isGreaterThan(_10_MB_IN_BYTES);
    }

    private List<byte[]> dataOfAtLeasSize(final int minTotalSize, final int maxPduSize)
    {
        ArrayList<byte[]> result = new ArrayList<>();
        MutableInteger currentTotalSize = new MutableInteger(0);
        do
        {
            List<byte[]> next = Arrays.asList(
                    byteArrayWith(fixedLengthStringStartingWith("\nfoo", maxPduSize)),
                    byteArrayWith(fixedLengthStringStartingWith("\nbar", maxPduSize / 2)),
                    byteArrayWith(fixedLengthStringStartingWith("\nbazqux", maxPduSize))
            );
            result.addAll(next);
            next.forEach(bytes -> currentTotalSize.addAndGet(bytes.length));
        }
        while (currentTotalSize.get() < minTotalSize);
        return result;
    }

    private byte[] extractedContent(final List<DataReceived> receivedEvents)
    {
        ByteBuffer actualContent = ByteBuffer.allocate((int)receivedEvents.get(receivedEvents.size() - 1).totalBytesReceived());
        receivedEvents.forEach(event -> event.copyDataTo(actualContent));
        return actualContent.array();
    }

    private BooleanSupplier bytesReceived(final ConnectionEventsSpy events, final long connectionId, final int size)
    {
        return () -> !events.all(DataReceived.class, connectionId, event -> event.totalBytesReceived() >= size).isEmpty();
    }

}
