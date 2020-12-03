package dev.squaremile.tcpgateway.aeroncluster.clusterservice;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.IdleStrategy;


import dev.squaremile.asynctcp.serialization.api.SerializedEventListener;
import dev.squaremile.asynctcp.serialization.internal.SerializingTransport;
import dev.squaremile.asynctcp.serialization.internal.TransportEventsDeserialization;
import dev.squaremile.asynctcp.transport.api.app.TransportEvent;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;

import static java.util.Collections.singletonList;

public class TcpHandlingClusteredService implements ClusteredService
{
    private final Long2ObjectHashMap<SerializedEventListener> tcpEventsForTcpGatewaySession = new Long2ObjectHashMap<>();
    private final Consumer<TransportEvent> onTcpEvent;
    private final BiConsumer<SerializingTransport, Integer> onStart;
    private final IntHashSet tcpGatewayStreamIds = new IntHashSet();
    private IdleStrategy idleStrategy;

    public TcpHandlingClusteredService(final int tcpGatewayStreamId, final BiConsumer<SerializingTransport, Integer> onStart, final Consumer<TransportEvent> onTcpEvent)
    {
        this(singletonList(tcpGatewayStreamId), onStart, onTcpEvent);
    }

    public TcpHandlingClusteredService(final Collection<Integer> tcpGatewayStreamIds, final BiConsumer<SerializingTransport, Integer> onStart, final Consumer<TransportEvent> onTcpEvent)
    {
        this.onStart = onStart;
        this.onTcpEvent = onTcpEvent;
        this.tcpGatewayStreamIds.addAll(tcpGatewayStreamIds);
    }

    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        this.idleStrategy = cluster.idleStrategy();
    }

    public void onSessionOpen(final ClientSession session, final long timestamp)
    {
        if (tcpGatewayStreamIds.contains(session.responseStreamId()))
        {
            final TcpGatewaySession tcpGatewaySession = new TcpGatewaySession(
                    session,
                    idleStrategy,
                    transport -> onStart.accept(transport, session.responseStreamId()),
                    onTcpEvent
            );
            tcpEventsForTcpGatewaySession.put(session.id(), new TransportEventsDeserialization(tcpGatewaySession::onEvent));
            tcpGatewaySession.onStart();
        }
    }

    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
    {
        tcpEventsForTcpGatewaySession.remove(session.id());
    }

    public void onSessionMessage(
            final ClientSession session,
            final long timestamp,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Header header
    )
    {
        tcpEventsForTcpGatewaySession.get(session.id()).onSerialized(buffer, offset, length);
    }

    public void onTimerEvent(final long correlationId, final long timestamp)
    {
    }

    public void onTakeSnapshot(final ExclusivePublication snapshotPublication)
    {
    }

    public void onRoleChange(final Cluster.Role newRole)
    {
    }

    public void onTerminate(final Cluster cluster)
    {
    }
}
