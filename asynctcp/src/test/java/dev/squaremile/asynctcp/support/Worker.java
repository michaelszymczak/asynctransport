package dev.squaremile.asynctcp.support;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;


import static dev.squaremile.asynctcp.support.ThrowWhenTimedOutBeforeMeeting.timeoutOr;
import static java.util.concurrent.locks.LockSupport.parkNanos;

public class Worker
{
    public static void runUntil(final BooleanSupplier stopCondition)
    {
        final BooleanSupplier abort = timeoutOr(stopCondition);
        while (!abort.getAsBoolean())
        {
            parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }
}