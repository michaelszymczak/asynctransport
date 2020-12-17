package dev.squaremile.transport.usecases.market.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


import dev.squaremile.asynctcp.fixtures.ThingsOnDutyRunner;
import dev.squaremile.transport.usecases.market.domain.FirmPrice;

import static dev.squaremile.asynctcp.transport.testfixtures.FreePort.freePort;
import static dev.squaremile.asynctcp.transport.testfixtures.Worker.runUntil;
import static java.lang.System.currentTimeMillis;

class MarketApplicationTest
{
    private final Clock clock = new Clock();
    private final MarketApplicationFixtures fixtures = new MarketApplicationFixtures(freePort(), clock, MarketMakerApplication::new);
    private final ThingsOnDutyRunner onDutyRunner = fixtures.onDutyRunner();
    private final MarketMakerApplication marketMakerApplication = fixtures.marketMakerApplication();

    @Test
    void shouldConfirmReceiptOfThePriceUpdate()
    {
        // Given
        assertThat(marketMakerApplication.acknowledgedPriceUpdatesCount()).isEqualTo(0);

        // When
        final long updateTimeMs = currentTimeMillis();
        marketMakerApplication.updatePrice(new FirmPrice(5, updateTimeMs, 99, 40, 101, 50));
        runUntil(onDutyRunner.reached(() -> marketMakerApplication.acknowledgedPriceUpdatesCount() > 0));

        // Then
        assertThat(marketMakerApplication.acknowledgedPriceUpdatesCount()).isEqualTo(1);
        FirmPrice lastUpdatedFirmPrice = marketMakerApplication.lastUpdatedFirmPrice();
        assertThat(lastUpdatedFirmPrice).usingRecursiveComparison()
                .isEqualTo(new FirmPrice(5, lastUpdatedFirmPrice.updateTime(), 99, 40, 101, 50));
        assertThat(lastUpdatedFirmPrice.updateTime()).isGreaterThanOrEqualTo(updateTimeMs);
        assertThat(lastUpdatedFirmPrice.updateTime()).isLessThan(updateTimeMs + 100);
    }
}