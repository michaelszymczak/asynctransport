package dev.squaremile.asynctcp.internal.serialization;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;


import dev.squaremile.asynctcp.api.transport.app.Transport;
import dev.squaremile.asynctcp.api.transport.app.TransportCommand;
import dev.squaremile.asynctcp.api.transport.commands.CloseConnection;
import dev.squaremile.asynctcp.api.transport.commands.Connect;
import dev.squaremile.asynctcp.api.transport.commands.Listen;
import dev.squaremile.asynctcp.api.transport.commands.SendData;
import dev.squaremile.asynctcp.api.transport.commands.SendMessage;
import dev.squaremile.asynctcp.api.transport.commands.StopListening;
import dev.squaremile.asynctcp.api.transport.values.ConnectionIdValue;
import dev.squaremile.asynctcp.api.transport.values.Delineation;
import dev.squaremile.asynctcp.internal.serialization.sbe.CloseConnectionDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.ConnectDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.ListenDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.MessageHeaderDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.SendDataDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.SendMessageDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.StopListeningDecoder;
import dev.squaremile.asynctcp.internal.serialization.sbe.VarDataEncodingDecoder;

public class TransportCommandDecoders
{
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final Int2ObjectHashMap<TransportCommandDecoder> commandDecoders = new Int2ObjectHashMap<>();
    private final Transport transport;
    private int decodedLength;

    public TransportCommandDecoders(final Transport transport)
    {
        this.transport = transport;
        registerCloseConnection(commandDecoders, headerDecoder);
        registerConnect(commandDecoders, headerDecoder);
        registerListen(commandDecoders, headerDecoder);
        registerStopListening(commandDecoders, headerDecoder);
        registerSendData(commandDecoders, headerDecoder);
        registerSendMessage(commandDecoders, headerDecoder);
    }

    public TransportCommand decode(DirectBuffer buffer, int offset, final int length)
    {
        decodedLength = 0;
        headerDecoder.wrap(buffer, offset);
        TransportCommand result = commandDecoderForTemplateId(headerDecoder.templateId()).decode(buffer, offset);
        if (decodedLength != length)
        {
            throw new IllegalArgumentException("Decoded length of " + decodedLength + " does not match declared length of " + length);
        }
        return result;
    }

    private void registerCloseConnection(final Int2ObjectHashMap<TransportCommandDecoder> eventDecoders, final MessageHeaderDecoder headerDecoder)
    {
        final CloseConnectionDecoder decoder = new CloseConnectionDecoder();
        eventDecoders.put(
                decoder.sbeTemplateId(), (buffer, offset) ->
                {
                    headerDecoder.wrap(buffer, offset);
                    decoder.wrap(
                            buffer,
                            headerDecoder.encodedLength() + headerDecoder.offset(),
                            headerDecoder.blockLength(),
                            headerDecoder.version()
                    );
                    CloseConnection result = new CloseConnection(new ConnectionIdValue(decoder.port(), decoder.connectionId())).set(decoder.commandId());
                    this.decodedLength = headerDecoder.encodedLength() + decoder.encodedLength();
                    return result;
                }
        );
    }

    private void registerConnect(final Int2ObjectHashMap<TransportCommandDecoder> eventDecoders, final MessageHeaderDecoder headerDecoder)
    {
        final ConnectDecoder decoder = new ConnectDecoder();
        eventDecoders.put(
                decoder.sbeTemplateId(), (buffer, offset) ->
                {
                    headerDecoder.wrap(buffer, offset);
                    decoder.wrap(
                            buffer,
                            headerDecoder.encodedLength() + headerDecoder.offset(),
                            headerDecoder.blockLength(),
                            headerDecoder.version()
                    );
                    Delineation delineation = new Delineation(
                            DelineationTypeMapping.toDomain(decoder.delineationType()),
                            decoder.delineationPadding(),
                            decoder.delineationKnownLength(),
                            decoder.delineationPattern()
                    );
                    String remoteHost = decoder.remoteHost();
                    Connect result = new Connect().set(remoteHost, decoder.remotePort(), decoder.commandId(), decoder.timeoutMs(), delineation);
                    this.decodedLength = headerDecoder.encodedLength() + decoder.encodedLength();
                    return result;
                }
        );
    }

    private void registerListen(final Int2ObjectHashMap<TransportCommandDecoder> eventDecoders, final MessageHeaderDecoder headerDecoder)
    {
        final ListenDecoder decoder = new ListenDecoder();
        eventDecoders.put(
                decoder.sbeTemplateId(), (buffer, offset) ->
                {
                    headerDecoder.wrap(buffer, offset);
                    decoder.wrap(
                            buffer,
                            headerDecoder.encodedLength() + headerDecoder.offset(),
                            headerDecoder.blockLength(),
                            headerDecoder.version()
                    );
                    Listen result = new Listen().set(
                            decoder.commandId(),
                            decoder.port(),
                            new Delineation(DelineationTypeMapping.toDomain(decoder.delineationType()), decoder.delineationPadding(), decoder.delineationKnownLength(), decoder.delineationPattern())
                    );
                    this.decodedLength = headerDecoder.encodedLength() + decoder.encodedLength();
                    return result;
                }
        );
    }

    private void registerStopListening(final Int2ObjectHashMap<TransportCommandDecoder> eventDecoders, final MessageHeaderDecoder headerDecoder)
    {
        final StopListeningDecoder decoder = new StopListeningDecoder();
        eventDecoders.put(
                decoder.sbeTemplateId(), (buffer, offset) ->
                {
                    headerDecoder.wrap(buffer, offset);
                    decoder.wrap(
                            buffer,
                            headerDecoder.encodedLength() + headerDecoder.offset(),
                            headerDecoder.blockLength(),
                            headerDecoder.version()
                    );
                    StopListening result = new StopListening().set(decoder.commandId(), decoder.port());
                    this.decodedLength = headerDecoder.encodedLength() + decoder.encodedLength();
                    return result;
                }
        );
    }

    private void registerSendData(final Int2ObjectHashMap<TransportCommandDecoder> eventDecoders, final MessageHeaderDecoder headerDecoder)
    {
        final SendDataDecoder decoder = new SendDataDecoder();
        eventDecoders.put(
                decoder.sbeTemplateId(), (buffer, offset) ->
                {
                    headerDecoder.wrap(buffer, offset);
                    decoder.wrap(
                            buffer,
                            headerDecoder.encodedLength() + headerDecoder.offset(),
                            headerDecoder.blockLength(),
                            headerDecoder.version()
                    );
                    VarDataEncodingDecoder srcData = decoder.data();
                    byte[] dstArray = new byte[(int)srcData.length()];
                    srcData.buffer().getBytes(srcData.offset() + srcData.encodedLength(), dstArray);
                    SendData result = new SendData(decoder.port(), decoder.connectionId(), decoder.capacity()).set(dstArray, decoder.commandId());
                    this.decodedLength = headerDecoder.encodedLength() + decoder.encodedLength() + (int)srcData.length();
                    return result;
                }
        );
    }

    private void registerSendMessage(final Int2ObjectHashMap<TransportCommandDecoder> eventDecoders, final MessageHeaderDecoder headerDecoder)
    {
        final SendMessageDecoder decoder = new SendMessageDecoder();
        eventDecoders.put(
                decoder.sbeTemplateId(), (buffer, offset) ->
                {
                    headerDecoder.wrap(buffer, offset);
                    decoder.wrap(
                            buffer,
                            headerDecoder.encodedLength() + headerDecoder.offset(),
                            headerDecoder.blockLength(),
                            headerDecoder.version()
                    );
                    final int srcDataOffset = decoder.data().offset() + decoder.data().encodedLength();
                    final int srcDataLength = (int)decoder.data().length();
                    final DirectBuffer srcBuffer = decoder.data().buffer();
                    SendMessage result = transport.command(decoder.connectionId(), SendMessage.class);
                    result.buffer().putBytes(result.offset(), srcBuffer, srcDataOffset, srcDataLength);
                    result.setLength(srcDataLength);
                    this.decodedLength = headerDecoder.encodedLength() + decoder.encodedLength() + srcDataLength;
                    return result;
                }
        );
    }

    private TransportCommandDecoder commandDecoderForTemplateId(int templateId)
    {
        if (!commandDecoders.containsKey(templateId))
        {
            throw new IllegalArgumentException("Unregistered templateId " + templateId);
        }
        return commandDecoders.get(templateId);
    }

    public boolean supports(final MessageHeaderDecoder header)
    {
        return commandDecoders.containsKey(header.templateId());
    }
}
