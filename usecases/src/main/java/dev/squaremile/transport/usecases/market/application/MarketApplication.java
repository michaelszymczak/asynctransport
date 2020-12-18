package dev.squaremile.transport.usecases.market.application;

import dev.squaremile.asynctcp.transport.api.app.ConnectionTransport;
import dev.squaremile.transport.usecases.market.domain.FakeMarket;
import dev.squaremile.transport.usecases.market.domain.FirmPrice;
import dev.squaremile.transport.usecases.market.domain.MarketMessage;
import dev.squaremile.transport.usecases.market.domain.Order;
import dev.squaremile.transport.usecases.market.domain.OrderResult;

class MarketApplication implements BusinessApplication
{
    private final FirmPrice firmPriceResponse = FirmPrice.createNoPrice();
    private final Clock clock;
    private final FakeMarket fakeMarket;
    private final MarketPublisher marketPublisher;

    public MarketApplication(final ConnectionTransport connectionTransport, final Clock clock, final FakeMarket fakeMarket)
    {
        this.clock = clock;
        this.fakeMarket = fakeMarket;
        this.marketPublisher = new MarketPublisher(connectionTransport);
    }

    @Override
    public void onMessage(final MarketMessage marketMessage)
    {
        if (marketMessage instanceof FirmPrice)
        {
            FirmPrice firmPrice = (FirmPrice)marketMessage;
            fakeMarket.onFirmPriceUpdate(clock.currentTimeMs(), 0, firmPrice);// TODO: read the market participant id
            firmPriceResponse.update(clock.currentTimeMs(), firmPrice);
            marketPublisher.publish(firmPriceResponse);
        }
        if (marketMessage instanceof Order)
        {
            boolean executed = fakeMarket.execute(clock.currentTimeMs(), 1, (Order)marketMessage);// TODO: read the market participant id
            OrderResult orderResult = executed ? OrderResult.EXECUTED : OrderResult.NOT_EXECUTED;
            marketPublisher.publish(orderResult);
        }
    }

    public void onPeriodicWakeUp()
    {
        fakeMarket.tick(clock.currentTimeMs());
    }
}
