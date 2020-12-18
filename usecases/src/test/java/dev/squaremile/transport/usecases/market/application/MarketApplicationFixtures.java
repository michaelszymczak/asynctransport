package dev.squaremile.transport.usecases.market.application;

import dev.squaremile.asynctcp.fixtures.ThingsOnDutyRunner;
import dev.squaremile.asynctcp.transport.api.app.TransportApplicationOnDuty;

public class MarketApplicationFixtures
{
    private final ThingsOnDutyRunner onDutyRunner;
    private final MarketMakerApplication marketMakerApplication;
    private final BuySideApplication buySideApplication;

    public MarketApplicationFixtures(final int port, final Clock clock)
    {
        final MarketApplicationStarter marketApplicationStarter = new MarketApplicationStarter(port, clock);
        final MarketMakerApplicationStarter marketMakerApplicationStarter = new MarketMakerApplicationStarter("localhost", port, clock);
        final BuySideApplicationStarter buySideApplicationStarter = new BuySideApplicationStarter("localhost", port);
        final TransportApplicationOnDuty marketTransportOnDuty = marketApplicationStarter.startTransport(1000);
        final TransportApplicationOnDuty marketMakerTransportOnDuty = marketMakerApplicationStarter.startTransport(marketTransportOnDuty::work, 1000);
        final TransportApplicationOnDuty buySideTransportOnDuty = buySideApplicationStarter.startTransport(marketTransportOnDuty::work, 1000);
        onDutyRunner = new ThingsOnDutyRunner(marketTransportOnDuty, marketMakerTransportOnDuty, buySideTransportOnDuty);
        marketMakerApplication = marketMakerApplicationStarter.application();
        buySideApplication = buySideApplicationStarter.application();
    }

    public ThingsOnDutyRunner onDutyRunner()
    {
        return onDutyRunner;
    }

    public MarketMakerApplication marketMakerApplication()
    {
        return marketMakerApplication;
    }

    public BuySideApplication buySideApplication()
    {
        return buySideApplication;
    }
}