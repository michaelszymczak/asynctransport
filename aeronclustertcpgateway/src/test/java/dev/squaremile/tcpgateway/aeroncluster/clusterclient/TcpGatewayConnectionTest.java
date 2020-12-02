package dev.squaremile.tcpgateway.aeroncluster.clusterclient;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import dev.squaremile.asynctcp.transport.api.commands.Listen;
import dev.squaremile.asynctcp.transport.testfixtures.network.SampleClient;
import dev.squaremile.tcpgateway.aeroncluster.clusterservice.TcpHandlingClusteredService;
import dev.squaremile.transport.aeroncluster.api.IngressDefinition;
import dev.squaremile.transport.aeroncluster.fixtures.ClusterDefinition;

import static dev.squaremile.asynctcp.serialization.api.PredefinedTransportDelineation.fixedLengthDelineation;
import static dev.squaremile.asynctcp.transport.testfixtures.FreePort.freePortPools;
import static dev.squaremile.asynctcp.transport.testfixtures.Worker.noExceptionAnd;
import static dev.squaremile.asynctcp.transport.testfixtures.Worker.runUntil;
import static dev.squaremile.transport.aeroncluster.fixtures.ClusterDefinition.endpoints;
import static dev.squaremile.transport.aeroncluster.fixtures.ClusterDefinition.node;
import static dev.squaremile.transport.aeroncluster.fixtures.ClusterNode.clusterNode;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

class TcpGatewayConnectionTest
{
    private final Map<String, List<Integer>> freePortPools = freePortPools("tcp:1", "ingress:1", "clusterNode:6");
    private final ClusterDefinition cluster = new ClusterDefinition(
            node(0, new IngressDefinition.Endpoint(0, "localhost", freePortPools.get("ingress").get(0)), endpoints("localhost", freePortPools.get("clusterNode")))
    );

    @Test
    void shouldUseTcpGatewayToInteractWithTcpLayerFromWithinTheCluster(@TempDir Path tempDir)
    {
        newSingleThreadExecutor().execute(clusterNode(
                cluster.node(0),
                cluster.memberURIs(),
                tempDir.resolve("cluster"),
                tempDir.resolve("aeron"),
                new TcpHandlingClusteredService(
                        transport -> transport.handle(transport.command(Listen.class).set(1, freePortPools.get("tcp").get(0), fixedLengthDelineation(1))),
                        System.out::println
                )
        )::start);
        newSingleThreadExecutor().execute(() -> new TcpGatewayConnection(cluster.ingress()).connect());

        // When
        runUntil(noExceptionAnd(() -> new SampleClient().connectedTo(freePortPools.get("tcp").get(0)) != null));
        runUntil(noExceptionAnd(() -> new SampleClient().connectedTo(freePortPools.get("tcp").get(0)) != null));
        runUntil(noExceptionAnd(() -> new SampleClient().connectedTo(freePortPools.get("tcp").get(0)) != null));
    }
}