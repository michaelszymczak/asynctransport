package com.michaelszymczak.sample.sockets;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.michaelszymczak.sample.sockets.api.events.ConnectionAccepted;
import com.michaelszymczak.sample.sockets.api.events.DataReceived;
import com.michaelszymczak.sample.sockets.api.events.NumberOfConnectionsChanged;
import com.michaelszymczak.sample.sockets.api.events.StartedListening;
import com.michaelszymczak.sample.sockets.support.ConnectionEventsSpy;
import com.michaelszymczak.sample.sockets.support.SampleClient;
import com.michaelszymczak.sample.sockets.support.TransportDriver;
import com.michaelszymczak.sample.sockets.support.TransportUnderTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


import static com.michaelszymczak.sample.sockets.support.BackgroundRunner.completed;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.copyOf;

class DataReceivingTest
{
    private final TransportUnderTest transport = new TransportUnderTest();

    @SafeVarargs
    private static <T> Set<?> distinct(final Function<T, Object> property, final T... items)
    {
        final List<T> allItems = Arrays.asList(items);
        return allItems.stream().map(property).collect(Collectors.toSet());
    }

    @Test
    void shouldReceiveData() throws IOException
    {
        final SampleClient client = new SampleClient();
        final TransportDriver driver = new TransportDriver(transport);

        // Given
        final ConnectionAccepted conn = driver.listenAndConnect(client);

        // When
        transport.workUntil(completed(() -> client.write("foo".getBytes(US_ASCII))));
        transport.workUntil(bytesReceived(transport.connectionEvents(), conn.connectionId(), 3));

        // Then
        assertThat(transport.events().all(DataReceived.class)).isNotEmpty();
        final DataReceived dataReceivedEvent = transport.events().last(DataReceived.class);
        assertThat(dataReceivedEvent).usingRecursiveComparison()
                .isEqualTo(new DataReceived(conn.port(), conn.connectionId(), 3, dataReceivedEvent.data(), dataReceivedEvent.length()));
        assertThat(dataAsString(transport.events().all(DataReceived.class), US_ASCII)).isEqualTo("foo");
    }

    @Test
    void shouldReceivedDataFromMultipleConnections() throws IOException
    {
        final SampleClient client1 = new SampleClient();
        final SampleClient client2 = new SampleClient();
        final SampleClient client3 = new SampleClient();
        final SampleClient client4 = new SampleClient();
        final TransportDriver driver = new TransportDriver(transport);

        // Given
        final StartedListening startedListeningEvent1 = driver.startListening();
        final ConnectionAccepted connS1C1 = driver.connectClient(startedListeningEvent1, client1);
        final StartedListening startedListeningEvent2 = driver.startListening();
        final ConnectionAccepted connS2C2 = driver.connectClient(startedListeningEvent2, client2);
        final ConnectionAccepted connS1C3 = driver.connectClient(startedListeningEvent1, client3);
        final ConnectionAccepted connS2C4 = driver.connectClient(startedListeningEvent2, client4);
        assertThat(distinct(ConnectionAccepted::commandId, connS1C1, connS2C2, connS1C3, connS2C4)).hasSize(2);
        assertThat(distinct(ConnectionAccepted::connectionId, connS1C1, connS2C2, connS1C3, connS2C4)).hasSize(4);

        // When
        transport.workUntil(completed(() -> client1.write(fixedLengthStringStartingWith("S1 -> C1 ", 10).getBytes(US_ASCII))));
        transport.workUntil(completed(() -> client2.write(fixedLengthStringStartingWith("S2 -> C2 ", 20).getBytes(US_ASCII))));
        transport.workUntil(completed(() -> client3.write(fixedLengthStringStartingWith("S1 -> C3 ", 30).getBytes(US_ASCII))));
        transport.workUntil(completed(() -> client4.write(fixedLengthStringStartingWith("S2 -> C4 ", 40).getBytes(US_ASCII))));
        transport.workUntil(bytesReceived(transport.connectionEvents(), connS1C1.connectionId(), 10));
        transport.workUntil(bytesReceived(transport.connectionEvents(), connS2C2.connectionId(), 20));
        transport.workUntil(bytesReceived(transport.connectionEvents(), connS1C3.connectionId(), 30));
        transport.workUntil(bytesReceived(transport.connectionEvents(), connS2C4.connectionId(), 40));

        // Then
        assertThat(dataAsString(transport.connectionEvents().all(DataReceived.class, connS1C1.connectionId()), US_ASCII))
                .isEqualTo(fixedLengthStringStartingWith("S1 -> C1 ", 10));
        assertThat(dataAsString(transport.connectionEvents().all(DataReceived.class, connS2C2.connectionId()), US_ASCII))
                .isEqualTo(fixedLengthStringStartingWith("S2 -> C2 ", 20));
        assertThat(dataAsString(transport.connectionEvents().all(DataReceived.class, connS1C3.connectionId()), US_ASCII))
                .isEqualTo(fixedLengthStringStartingWith("S1 -> C3 ", 30));
        assertThat(dataAsString(transport.connectionEvents().all(DataReceived.class, connS2C4.connectionId()), US_ASCII))
                .isEqualTo(fixedLengthStringStartingWith("S2 -> C4 ", 40));
    }

    @AfterEach
    void tearDown()
    {
        transport.close();
        if (transport.statusEvents().contains(NumberOfConnectionsChanged.class))
        {
            assertThat(transport.statusEvents().last(NumberOfConnectionsChanged.class).newNumberOfConnections()).isEqualTo(0);
        }
    }

    private BooleanSupplier bytesReceived(final ConnectionEventsSpy events, final long connectionId, final int size)
    {
        return () -> !events.all(DataReceived.class, connectionId, event -> event.totalBytesReceived() >= size).isEmpty();
    }

    private String dataAsString(final List<DataReceived> all, final Charset charset)
    {
        return all.stream()
                .map(dataReceived -> copyOf(dataReceived.data(), dataReceived.length()))
                .map(data -> new String(data, charset))
                .collect(Collectors.joining(""));
    }

    private String fixedLengthStringStartingWith(final String content, final int minLength)
    {
        final StringBuilder sb = new StringBuilder(10);
        sb.append(content);
        for (int i = 0; i < minLength - content.length(); i++)
        {
            sb.append(i % 10);
        }
        return sb.toString();
    }
}
