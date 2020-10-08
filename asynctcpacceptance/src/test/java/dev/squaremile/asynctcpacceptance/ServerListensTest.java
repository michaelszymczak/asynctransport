package dev.squaremile.asynctcpacceptance;

import java.net.ConnectException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


import dev.squaremile.asynctcp.transport.api.app.CommandFailed;
import dev.squaremile.asynctcp.transport.api.commands.Listen;
import dev.squaremile.asynctcp.transport.api.commands.StopListening;
import dev.squaremile.asynctcp.transport.api.events.ConnectionAccepted;
import dev.squaremile.asynctcp.transport.api.events.StartedListening;
import dev.squaremile.asynctcp.transport.api.events.StoppedListening;
import dev.squaremile.asynctcp.transport.api.events.TransportCommandFailed;
import dev.squaremile.asynctcp.transport.internal.domain.NumberOfConnectionsChanged;
import dev.squaremile.asynctcp.transport.testfixtures.network.SampleClient;

import static dev.squaremile.asynctcp.serialization.api.PredefinedTransportDelineation.INTEGERS;
import static dev.squaremile.asynctcp.serialization.api.PredefinedTransportDelineation.RAW_STREAMING;
import static dev.squaremile.asynctcp.transport.testfixtures.Assertions.assertEqual;
import static dev.squaremile.asynctcp.transport.testfixtures.BackgroundRunner.completed;
import static dev.squaremile.asynctcp.transport.testfixtures.FreePort.freePort;
import static dev.squaremile.asynctcp.transport.testfixtures.FreePort.freePortOtherThan;


class ServerListensTest extends TransportTestBase
{
    @Test
    void shouldAcceptConnections()
    {
        final int port = freePort();

        // Given
        serverTransport.handle(serverTransport.command(Listen.class).set((long)102, port, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().contains(StartedListening.class));
        assertEqual(serverTransport.events().all(StartedListening.class), new StartedListening(port, 102, RAW_STREAMING.type));

        // When
        serverTransport.workUntil(completed(() -> clients.client(1).connectedTo(port)));

        // Then
        serverTransport.workUntil(() -> !serverTransport.connectionEvents().all(ConnectionAccepted.class).isEmpty());
        assertThat(serverTransport.statusEvents().all(NumberOfConnectionsChanged.class)).isNotEmpty();
        ConnectionAccepted event = serverTransport.connectionEvents().last(ConnectionAccepted.class);
        assertThat(event.port()).isEqualTo(port);
        assertThat(event.commandId()).isEqualTo(102);
    }

    @Test
    @Tag("tcperror")
    void shouldNotAcceptIfNotAsked()
    {
        assertThrows(ConnectException.class, () -> clients.client(1).connectedTo(freePort()));
    }

    @Test
    @Tag("tcperror")
    void shouldStopListeningWhenAsked()
    {
        serverTransport.handle(serverTransport.command(Listen.class).set((long)0, freePort(), RAW_STREAMING.type));
        serverTransport.work();
        serverTransport.workUntil(() -> serverTransport.events().contains(StartedListening.class));
        final int port = serverTransport.events().last(StartedListening.class).port();

        // When
        serverTransport.handle(serverTransport.command(StopListening.class).set(9, port));
        serverTransport.work();
        serverTransport.workUntil(() -> serverTransport.events().contains(StoppedListening.class));

        // Then
        assertEqual(serverTransport.events().all(StoppedListening.class), new StoppedListening(port, 9));
        assertThrows(ConnectException.class, () -> clients.client(1).connectedTo(port));
    }

    @Test
    void shouldIgnoreStopListeningCommandForNonExistingRequest()
    {
        // Given
        final int port = freePort();
        final int anotherPort = freePortOtherThan(port);
        serverTransport.handle(serverTransport.command(Listen.class).set((long)2, port, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().contains(StartedListening.class));

        // When
        serverTransport.handle(serverTransport.command(StopListening.class).set(4, anotherPort));
        serverTransport.workUntil(() -> serverTransport.events().contains(TransportCommandFailed.class));

        // Then
        final TransportCommandFailed event = serverTransport.events().last(TransportCommandFailed.class);
        assertThat(event.commandId()).isEqualTo(4);
        assertThat(event.port()).isEqualTo(anotherPort);
        clients.client(1).connectedTo(port);
    }

    @Test
    void shouldBeAbleToListenOnMoreThanOnePort()
    {
        // When
        final int serverPort1 = freePort();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)0, serverPort1, RAW_STREAMING.type));
        final int serverPort2 = freePortOtherThan(serverPort1);
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 1);

        // When
        serverTransport.handle(serverTransport.command(Listen.class).set((long)1, serverPort2, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 2);

        // Then
        assertThat(serverTransport.statusEvents().all(NumberOfConnectionsChanged.class)).isEmpty();
        clients.client(1).connectedTo(serverPort1);
        clients.client(2).connectedTo(serverPort2);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 2);
    }

    @Test
    void shouldBeAbleToListenOnTheSamePortAgain()
    {
        // When
        final int serverPort1 = freePort();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)101, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 1);
        serverTransport.handle(serverTransport.command(StopListening.class).set(102, serverPort1));
        serverTransport.workUntil(() -> serverTransport.events().all(StoppedListening.class).size() == 1);

        // When
        serverTransport.handle(serverTransport.command(Listen.class).set((long)103, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 2);

        // Then
        assertThat(serverTransport.statusEvents().all(NumberOfConnectionsChanged.class)).isEmpty();
        clients.client(1).connectedTo(serverPort1);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 1);
    }

    @Test
    void shouldBeAbleToListenOnTheSameAfterStoppedListeningWhileConnectionIsUp()
    {
        // When
        final int serverPort1 = freePort();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)101, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 1);
        clients.client(1).connectedTo(serverPort1);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 1);
        serverTransport.handle(serverTransport.command(StopListening.class).set(102, serverPort1));
        serverTransport.workUntil(() -> serverTransport.events().all(StoppedListening.class).size() == 1);

        // When
        serverTransport.handle(serverTransport.command(Listen.class).set((long)103, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 2);

        // Then
        assertThat(serverTransport.statusEvents().all(NumberOfConnectionsChanged.class)).hasSize(1);
        clients.client(2).connectedTo(serverPort1);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 2);
    }

    @Test
    void shouldBeAbleToListenOnTheSameAfterStoppedListeningAndClosedConnection()
    {
        // When
        final int serverPort1 = freePort();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)101, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 1);
        clients.client(1).connectedTo(serverPort1);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 1);
        serverTransport.handle(serverTransport.command(StopListening.class).set(102, serverPort1));
        serverTransport.workUntil(() -> serverTransport.events().all(StoppedListening.class).size() == 1);
        clients.client(1).close();
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 2);
        assertThat(serverTransport.statusEvents().last(NumberOfConnectionsChanged.class).newNumberOfConnections()).isEqualTo(0);

        // When
        serverTransport.handle(serverTransport.command(Listen.class).set((long)103, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 2);

        // Then
        assertThat(serverTransport.statusEvents().all(NumberOfConnectionsChanged.class)).hasSize(2);
        clients.client(2).connectedTo(serverPort1);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 3);
    }


    @Test
    void shouldRejectWhenAskedToListenOnTheSamePortAtTheSameTime()
    {
        // When
        final int serverPort1 = freePort();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)101, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 1);
        assertThat(serverTransport.statusEvents().all(NumberOfConnectionsChanged.class)).hasSize(0);

        // When
        serverTransport.handle(serverTransport.command(Listen.class).set((long)102, serverPort1, RAW_STREAMING.type));

        // Then
        serverTransport.workUntil(() -> serverTransport.events().all(CommandFailed.class).size() == 1);
        assertThat(serverTransport.events().all(StartedListening.class)).hasSize(1);
        assertEqual(serverTransport.events().all(CommandFailed.class), new TransportCommandFailed(serverPort1, 102, "Address already in use", Listen.class));
        clients.client(1).connectedTo(serverPort1);
        serverTransport.workUntil(() -> serverTransport.statusEvents().all(NumberOfConnectionsChanged.class).size() == 1);
    }

    @Test
    void shouldRejectWhenAskedImmediatelyToListenOnTheSamePort()
    {
        // When
        final int serverPort1 = freePort();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)101, serverPort1, RAW_STREAMING.type));
        serverTransport.handle(serverTransport.command(Listen.class).set((long)102, serverPort1, RAW_STREAMING.type));
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 1);

        // Then
        serverTransport.workUntil(() -> serverTransport.events().all(CommandFailed.class).size() == 1);
        assertThat(serverTransport.events().all(StartedListening.class)).hasSize(1);
        assertEqual(serverTransport.events().all(CommandFailed.class), new TransportCommandFailed(serverPort1, 102, "Address already in use", Listen.class));
    }


    @Test
    @Tag("tcperror")
    void shouldUseRequestIdToFindThePortItShouldStopListeningOn()
    {
        // Given
        final int port1 = freePort();
        final int port2 = freePortOtherThan(port1);
        final int port3 = freePortOtherThan(port1, port2);
        assertThrows(ConnectException.class, () -> new SampleClient().connectedTo(port1));
        assertThrows(ConnectException.class, () -> new SampleClient().connectedTo(port2));
        assertThrows(ConnectException.class, () -> new SampleClient().connectedTo(port3));

        serverTransport.handle(serverTransport.command(Listen.class).set((long)5, port1, RAW_STREAMING.type));
        serverTransport.work();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)6, port2, INTEGERS.type));
        serverTransport.work();
        serverTransport.handle(serverTransport.command(Listen.class).set((long)7, port3, RAW_STREAMING.type));
        serverTransport.work();
        serverTransport.workUntil(() -> serverTransport.events().all(StartedListening.class).size() == 3);
        assertEqual(
                serverTransport.events().all(StartedListening.class),
                new StartedListening(port1, 5, RAW_STREAMING.type),
                new StartedListening(port2, 6, INTEGERS.type),
                new StartedListening(port3, 7, RAW_STREAMING.type)
        );

        // When
        serverTransport.handle(serverTransport.command(StopListening.class).set(9, port2));
        serverTransport.work();
        serverTransport.workUntil(() -> serverTransport.events().contains(StoppedListening.class));

        // Then
        assertThat(serverTransport.events().last(StoppedListening.class).commandId()).isEqualTo(9);
        assertThat(serverTransport.events().last(StoppedListening.class).port()).isEqualTo(port2);
        new SampleClient().connectedTo(port1);
        assertThrows(ConnectException.class, () -> new SampleClient().connectedTo(port2));
        new SampleClient().connectedTo(port3);
    }
}
