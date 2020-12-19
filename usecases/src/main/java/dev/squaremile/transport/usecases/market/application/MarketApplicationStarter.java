package dev.squaremile.transport.usecases.market.application;

import org.agrona.collections.MutableBoolean;


import dev.squaremile.asynctcp.api.AsyncTcp;
import dev.squaremile.asynctcp.api.wiring.ListeningApplication;
import dev.squaremile.asynctcp.serialization.api.SerializedMessageListener;
import dev.squaremile.asynctcp.transport.api.app.TransportApplicationOnDuty;
import dev.squaremile.asynctcp.transport.api.events.StartedListening;
import dev.squaremile.transport.usecases.market.domain.FakeMarket;
import dev.squaremile.transport.usecases.market.domain.MarketListener;
import dev.squaremile.transport.usecases.market.domain.TrackedSecurity;
import dev.squaremile.transport.usecases.market.domain.Volatility;

import static dev.squaremile.asynctcp.api.wiring.ConnectionApplicationFactory.onStart;
import static dev.squaremile.asynctcp.serialization.api.PredefinedTransportDelineation.lengthBasedDelineation;
import static dev.squaremile.asynctcp.transport.api.values.Delineation.Type.SHORT_LITTLE_ENDIAN_FIELD;
import static dev.squaremile.transport.usecases.market.domain.CurrentTime.currentTime;
import static dev.squaremile.transport.usecases.market.domain.CurrentTime.timeFromMs;
import static dev.squaremile.transport.usecases.market.domain.MarketListener.marketListeners;

public class MarketApplicationStarter
{
    private final int port;
    private final Clock clock;
    private final MarketMakerChart chart = new MarketMakerChart(time -> time / 10);
    private TransportApplicationOnDuty transportApplication;
    private MarketConnectionApplication<MarketApplication> marketConnectionApplication;

    public MarketApplicationStarter(final int port, final Clock clock)
    {
        this.port = port;
        this.clock = clock;
    }

    public TransportApplicationOnDuty startTransport(final int timeoutMs)
    {
        if (transportApplication != null)
        {
            throw new IllegalStateException("Application already started");
        }
        final MutableBoolean startedListening = new MutableBoolean(false);
        final TransportApplicationOnDuty application = new AsyncTcp().create("marketApp", 16 * 1024, SerializedMessageListener.NO_OP, transport ->
        {
            final MarketParticipants marketParticipants = new MarketParticipants();
            MarketListener marketListener = marketListeners(
                    new MarketEventsPublisher(transport, marketParticipants),
                    chart
            );
            final FakeMarket fakeMarket = new FakeMarket(new TrackedSecurity().midPrice(0, 100), new Volatility(1, 1), 0, marketListener);
            return new ListeningApplication(
                    transport,
                    lengthBasedDelineation(SHORT_LITTLE_ENDIAN_FIELD, 0, 0),
                    port,
                    event ->
                    {
                        if (event instanceof StartedListening)
                        {
                            startedListening.set(true);
                        }
                    },
                    onStart((connectionTransport, connectionId) ->
                            {
                                marketParticipants.onConnected(connectionId);
                                marketConnectionApplication = new MarketConnectionApplication<>(clock, new MarketApplication(connectionId, clock, fakeMarket, marketParticipants));
                                return marketConnectionApplication;
                            })
            );
        });
        application.onStart();
        long startTime = currentTime();
        while (!startedListening.get() && currentTime() < startTime + timeFromMs(timeoutMs))
        {
            application.work();
        }
        if (!startedListening.get())
        {
            throw new IllegalStateException("Unable to satisfy condition within " + timeoutMs + "ms.");
        }
        this.transportApplication = application;
        return application;
    }

    public MarketConnectionApplication<MarketApplication> application()
    {
        return marketConnectionApplication;
    }

    public MarketMakerChart chart()
    {
        return chart;
    }
}
