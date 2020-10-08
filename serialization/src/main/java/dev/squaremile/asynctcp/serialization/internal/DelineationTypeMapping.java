package dev.squaremile.asynctcp.serialization.internal;

import dev.squaremile.asynctcp.sbe.DelineationType;
import dev.squaremile.asynctcp.transport.api.values.Delineation;

public class DelineationTypeMapping
{
    public static Delineation.Type toDomain(final DelineationType wire)
    {
        switch (wire)
        {
            case FIXED_LENGTH:
                return Delineation.Type.FIXED_LENGTH;
            case ASCII_PATTERN:
                return Delineation.Type.ASCII_PATTERN;
            case NULL_VAL:
            default:
                throw new IllegalArgumentException();
        }
    }

    public static DelineationType toWire(final Delineation.Type domain)
    {
        switch (domain)
        {
            case FIXED_LENGTH:
                return DelineationType.FIXED_LENGTH;
            case ASCII_PATTERN:
                return DelineationType.ASCII_PATTERN;
            default:
                throw new IllegalArgumentException();
        }
    }
}
