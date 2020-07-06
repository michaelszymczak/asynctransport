package com.michaelszymczak.sample.sockets;

import java.io.IOException;
import java.net.ConnectException;

import com.michaelszymczak.sample.sockets.support.DelegatingServer;
import com.michaelszymczak.sample.sockets.support.FreePort;
import com.michaelszymczak.sample.sockets.support.ReadingClient;
import com.michaelszymczak.sample.sockets.support.ServerRun;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import static com.michaelszymczak.sample.sockets.support.ServerRun.startServer;

class ServerTest
{
    @Test
    void shouldAcceptConnections()
    {
        // Given
        runTest((reactiveConnections, client) ->
                {
                    // When
                    final int port = FreePort.freePort(0);
                    reactiveConnections.listen(port);

                    // Then
                    client.connectedTo(port);
                }
        );
    }

    @Test
    void shouldNotAcceptIfNotAsked()
    {
        runTest((reactiveConnections, client) -> assertThrows(ConnectException.class, () -> client.connectedTo(FreePort.freePort(0))));
    }

    // timeout required
    @Test
    void shouldNotAcceptIfListeningOnAnotherPort()
    {
        runTest((reactiveConnections, client) ->
                {
                    // When
                    final int port = FreePort.freePort(0);
                    reactiveConnections.listen(port);

                    // Then
                    assertThrows(ConnectException.class, () -> client.connectedTo(FreePort.freePortOtherThan(port)));
                }
        );
    }

    @Test
    void shouldStopListeningWhenAsked()
    {
        runTest((reactiveConnections, client) ->
                {
                    // Given
                    final int port = FreePort.freePort(0);
                    final long requestId = reactiveConnections.listen(port);

                    // When
                    reactiveConnections.stopListening(requestId);

                    // Then
                    assertThrows(ConnectException.class, () -> client.connectedTo(port));
                }
        );
    }

    @Test
    void shouldIgnoreStopListeningCommandForNonExistingRequest()
    {
        runTest((reactiveConnections, client) ->
                {
                    // Given
                    final int port = FreePort.freePort(0);
                    final long requestId = reactiveConnections.listen(port);

                    // When
                    final long result = reactiveConnections.stopListening(requestId + 1);
                    assertEquals(-1, result);

                    // Then
                    client.connectedTo(port);
                }
        );
    }

    @Test
    void shouldBeAbleToListenOnMoreThanOnePort()
    {
        try
        {
            final ReactiveConnections reactiveConnections = new ReactiveConnections();
            try (
                    ServerRun ignored = startServer(new DelegatingServer(reactiveConnections));
                    ReadingClient client1 = new ReadingClient();
                    ReadingClient client2 = new ReadingClient()
            )
            {
                // When
                final int port1 = FreePort.freePort(0);
                reactiveConnections.listen(port1);
                final int port2 = FreePort.freePortOtherThan(port1);
                reactiveConnections.listen(port2);

                // Then
                client1.connectedTo(port1);
                client2.connectedTo(port2);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Disabled
    void shouldUseRequestIdToFindThePortItShouldStopListeningOn()
    {
        try
        {
            final ReactiveConnections reactiveConnections = new ReactiveConnections();
            try (
                    ServerRun ignored = startServer(new DelegatingServer(reactiveConnections));
                    ReadingClient client = new ReadingClient()
            )
            {
                // Given
                final int port1 = FreePort.freePort(0);
                reactiveConnections.listen(port1);
                final int port2 = FreePort.freePort(0);
                final long requestId = reactiveConnections.listen(port2);
                final int port3 = FreePort.freePort(0);
                reactiveConnections.listen(port3);

                // When
                final long result = reactiveConnections.stopListening(requestId);
                assertNotEquals(-1, result);

                // Then
                client.connectedTo(port1);
                assertThrows(ConnectException.class, () -> client.connectedTo(port2));
                client.connectedTo(port3);

            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void runTest(final TestScenario testScenario)
    {
        try
        {
            final ReactiveConnections reactiveConnections = new ReactiveConnections();
            try (
                    ServerRun ignored = startServer(new DelegatingServer(reactiveConnections));
                    ReadingClient client = new ReadingClient()
            )
            {
                testScenario.test(reactiveConnections, client);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    interface TestScenario
    {
        void test(ReactiveConnections reactiveConnections, ReadingClient client) throws IOException;
    }

}
