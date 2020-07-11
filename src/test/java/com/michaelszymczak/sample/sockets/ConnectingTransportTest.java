package com.michaelszymczak.sample.sockets;

import java.io.IOException;
import java.net.SocketException;
import java.util.List;

import com.michaelszymczak.sample.sockets.api.commands.CloseConnection;
import com.michaelszymczak.sample.sockets.api.commands.Listen;
import com.michaelszymczak.sample.sockets.api.events.ConnectionAccepted;
import com.michaelszymczak.sample.sockets.api.events.StartedListening;
import com.michaelszymczak.sample.sockets.nio.NIOBackedTransport;
import com.michaelszymczak.sample.sockets.nio.Workmen;
import com.michaelszymczak.sample.sockets.support.BackgroundRunner;
import com.michaelszymczak.sample.sockets.support.SampleClient;
import com.michaelszymczak.sample.sockets.support.TransportEvents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


import static com.michaelszymczak.sample.sockets.support.Assertions.assertEqual;
import static com.michaelszymczak.sample.sockets.support.Foreman.workUntil;
import static com.michaelszymczak.sample.sockets.support.FreePort.freePort;
import static com.michaelszymczak.sample.sockets.support.FreePort.freePortOtherThan;

class ConnectingTransportTest
{
    private final TransportEvents events = new TransportEvents();
    private final BackgroundRunner runner = new BackgroundRunner();

    @Test
    void shouldNotifyWhenConnected() throws IOException
    {
        final NIOBackedTransport transport = new NIOBackedTransport(events);

        // Given
        transport.handle(new Listen(1, freePort()));
        final int serverPort = events.last(StartedListening.class).port();

        // When
        final int clientPort = freePort();
        runner.keepRunning(transport::work).untilCompleted(() -> new SampleClient().connectedTo(serverPort, clientPort));
        workUntil(() -> !events.all(ConnectionAccepted.class).isEmpty(), transport);

        // Then
        assertEqual(events.all(ConnectionAccepted.class), new ConnectionAccepted(serverPort, 1, clientPort, 0));
    }

    @Test
    void shouldProvideConnectionDetailsForEachConnection() throws IOException
    {
        final NIOBackedTransport transport = new NIOBackedTransport(events);

        // Given
        transport.handle(new Listen(5, freePort()));
        final int serverPort = events.last(StartedListening.class).port();

        // When
        final Workmen.ThrowingBlockingWorkman clientConnectsTask = () -> new SampleClient().connectedTo(serverPort);
        runner.keepRunning(transport::work).untilCompleted(clientConnectsTask);
        runner.keepRunning(transport::work).untilCompleted(clientConnectsTask);
        workUntil(() -> events.all(ConnectionAccepted.class).size() >= 2, transport);

        // Then
        final List<ConnectionAccepted> events = this.events.all(ConnectionAccepted.class);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).commandId()).isEqualTo(events.get(1).commandId());
        assertThat(events.get(0).port()).isEqualTo(events.get(1).port());
        assertThat(events.get(0).remotePort()).isNotEqualTo(events.get(1).remotePort());
        assertThat(events.get(0).connectionId()).isNotEqualTo(events.get(1).connectionId());
    }

    @Test
    void shouldCloseConnection() throws IOException
    {
        final NIOBackedTransport transport = new NIOBackedTransport(events);

        // Given
        transport.handle(new Listen(9, freePort()));
        workUntil(() -> !events.all(StartedListening.class).isEmpty(), transport);
        final int serverPort = events.last(StartedListening.class).port();
        final SampleClient client = new SampleClient();
        assertThrows(SocketException.class, client::write); // throws if not connected when writing
        runner.keepRunning(transport::work).untilCompleted(() -> client.connectedTo(serverPort));
        workUntil(() -> !events.all(ConnectionAccepted.class).isEmpty(), transport);
        final ConnectionAccepted connectionAccepted = events.last(ConnectionAccepted.class);

        // When
        transport.handle(new CloseConnection(connectionAccepted.port(), connectionAccepted.connectionId()));

        // Then
        assertThat(client.hasServerClosedConnection()).isTrue();
    }

    @Test
    void shouldNotifyWhenConnectedWhileListeningOnMultiplePorts() throws IOException
    {
        final NIOBackedTransport transport = new NIOBackedTransport(events);

        // Given
        final int listeningPort1 = freePort();
        final int listeningPort2 = freePortOtherThan(listeningPort1);
        transport.handle(new Listen(5, listeningPort1));
        transport.handle(new Listen(6, listeningPort2));
        assertThat(events.last(StartedListening.class, event -> event.commandId() == 5).port()).isEqualTo(listeningPort1);
        assertThat(events.last(StartedListening.class, event -> event.commandId() == 6).port()).isEqualTo(listeningPort2);

//        // When
        runner.keepRunning(transport::work).untilCompleted(() -> new SampleClient().connectedTo(listeningPort1));
        runner.keepRunning(transport::work).untilCompleted(() -> new SampleClient().connectedTo(listeningPort2));

        // Then
        workUntil(() -> events.all(ConnectionAccepted.class).size() >= 2, transport);
        final ConnectionAccepted connectionAccepted1 = events.last(ConnectionAccepted.class, event -> event.port() == listeningPort1);
        final ConnectionAccepted connectionAccepted2 = events.last(ConnectionAccepted.class, event -> event.port() == listeningPort2);

        assertThat(connectionAccepted1.connectionId()).isNotEqualTo(connectionAccepted2.connectionId());
        assertThat(connectionAccepted1.commandId()).isEqualTo(5);
        assertThat(connectionAccepted2.commandId()).isEqualTo(6);
        assertThat(connectionAccepted1.port()).isEqualTo(listeningPort1);
        assertThat(connectionAccepted2.port()).isEqualTo(listeningPort2);
        assertThat(connectionAccepted1.remotePort()).isNotEqualTo(connectionAccepted2.remotePort());
    }

    @Test
    void shouldNotifyWhenStartedListeningAndConnectedTwice() throws IOException
    {
        final NIOBackedTransport transport = new NIOBackedTransport(events);

        // Given
        final int listeningPort1 = freePort();
        final int listeningPort2 = freePortOtherThan(listeningPort1);

        // When
        transport.handle(new Listen(5, listeningPort1));
        assertThat(events.last(StartedListening.class, event -> event.commandId() == 5).port()).isEqualTo(listeningPort1);
        runner.keepRunning(transport::work).untilCompleted(() -> new SampleClient().connectedTo(listeningPort1));
        transport.handle(new Listen(6, listeningPort2));
        assertThat(events.last(StartedListening.class, event -> event.commandId() == 6).port()).isEqualTo(listeningPort2);
        runner.keepRunning(transport::work).untilCompleted(() -> new SampleClient().connectedTo(listeningPort2));

        // Then
        workUntil(() -> events.all(ConnectionAccepted.class).size() >= 2, transport);
        final ConnectionAccepted connectionAccepted1 = events.last(ConnectionAccepted.class, event -> event.port() == listeningPort1);
        final ConnectionAccepted connectionAccepted2 = events.last(ConnectionAccepted.class, event -> event.port() == listeningPort2);

        assertThat(connectionAccepted1.connectionId()).isNotEqualTo(connectionAccepted2.connectionId());
        assertThat(connectionAccepted1.commandId()).isEqualTo(5);
        assertThat(connectionAccepted2.commandId()).isEqualTo(6);
        assertThat(connectionAccepted1.port()).isEqualTo(listeningPort1);
        assertThat(connectionAccepted2.port()).isEqualTo(listeningPort2);
        assertThat(connectionAccepted1.remotePort()).isNotEqualTo(connectionAccepted2.remotePort());
    }
}