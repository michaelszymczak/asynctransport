package dev.squaremile.asynctcp.serialization;

import java.util.function.Function;
import java.util.stream.Stream;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


import dev.squaremile.asynctcp.api.app.Transport;
import dev.squaremile.asynctcp.api.app.TransportCommand;
import dev.squaremile.asynctcp.api.app.TransportUserCommand;
import dev.squaremile.asynctcp.testfixtures.TransportCommandSpy;

import static dev.squaremile.asynctcp.testfixtures.Assertions.assertEqual;

class TransportCommandDeserializationTest
{
    private static final int OFFSET = 6;
    private final TransportCommandSpy commandsSpy = new TransportCommandSpy();

    static Stream<Function<Transport, TransportUserCommand>> commands()
    {
        return Fixtures.commands();
    }

    @ParameterizedTest
    @MethodSource("commands")
    void shouldSerializeCommands(final Function<Transport, TransportCommand> commandProvider)
    {
        // Given
        SerializingTransport transport = notifyingAboutSerializedCommand(new TransportCommandDeserialization(commandsSpy));
        TransportCommand command = commandProvider.apply(transport);

        // When
        transport.handle(command);

        // Then
        assertEqual(commandsSpy.all(), command);
    }

    private SerializingTransport notifyingAboutSerializedCommand(final TransportCommandDeserialization serializedCommandListener)
    {
        SerializingTransport transport = new SerializingTransport(
                new UnsafeBuffer(new byte[150]),
                OFFSET,
                serializedCommandListener
        );
        transport.onEvent(Fixtures.connectedEvent());
        return transport;
    }
}