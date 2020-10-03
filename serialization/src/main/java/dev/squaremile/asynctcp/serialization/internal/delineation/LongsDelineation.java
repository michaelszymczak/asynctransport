package dev.squaremile.asynctcp.serialization.internal.delineation;

import org.agrona.DirectBuffer;

class LongsDelineation implements DelineationHandler
{
    private final FixedLengthDelineation delineation;

    LongsDelineation(final DelineationHandler delineatedDataHandler)
    {
        this.delineation = new FixedLengthDelineation(delineatedDataHandler, 8);
    }

    @Override
    public void onData(final DirectBuffer buffer, final int offset, final int length)
    {
        delineation.onData(buffer, offset, length);
    }
}
