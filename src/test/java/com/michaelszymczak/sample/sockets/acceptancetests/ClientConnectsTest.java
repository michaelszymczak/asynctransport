package com.michaelszymczak.sample.sockets.acceptancetests;

import com.michaelszymczak.sample.sockets.domain.api.commands.Connect;
import com.michaelszymczak.sample.sockets.domain.api.events.CommandFailed;
import com.michaelszymczak.sample.sockets.domain.api.events.Connected;
import com.michaelszymczak.sample.sockets.domain.api.events.ConnectionAccepted;
import com.michaelszymczak.sample.sockets.domain.api.events.NumberOfConnectionsChanged;
import com.michaelszymczak.sample.sockets.domain.api.events.StartedListening;
import com.michaelszymczak.sample.sockets.support.TransportDriver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


import static com.michaelszymczak.sample.sockets.support.Assertions.assertEqual;
import static com.michaelszymczak.sample.sockets.support.TearDown.closeCleanly;
import static java.util.Collections.singletonList;


class ClientConnectsTest extends TransportTestBase
{
    @Test
    void shouldConnect()
    {
        // Given
        TransportDriver serverDriver = new TransportDriver(serverTransport);
        StartedListening serverStartedListening = serverDriver.startListening();

        // When
        clientTransport.handle(new Connect().set(serverStartedListening.port(), 101));
        spinUntil(() -> !serverTransport.connectionEvents().all(ConnectionAccepted.class).isEmpty() &&
                        !clientTransport.connectionEvents().all(Connected.class).isEmpty());

        // Then
        ConnectionAccepted connectionAcceptedByServer = serverTransport.connectionEvents().last(ConnectionAccepted.class);
        assertThat(clientTransport.events().all(CommandFailed.class)).isEmpty();
        Connected connected = clientTransport.events().last(Connected.class);
        assertEqual(
                clientTransport.events().all(Connected.class),
                new Connected(
                        connectionAcceptedByServer.remotePort(),
                        101,
                        serverStartedListening.port(),
                        connected.connectionId(),
                        connected.inboundPduLimit(),
                        connected.outboundPduLimit()
                )
        );
        assertEqual(clientTransport.statusEvents().all(), singletonList(new NumberOfConnectionsChanged(1)));
        assertEqual(serverTransport.statusEvents().all(), singletonList(new NumberOfConnectionsChanged(1)));
        assertThat(clientTransport.events().all(CommandFailed.class)).isEmpty();
    }

    @AfterEach
    void tearDown()
    {
        closeCleanly(serverTransport);
        closeCleanly(clientTransport);
    }
}
