package dev.squaremile.asynctcp.support.transport.latencies;

class Measurement
{
    int point;
    long nanoTime;

    public void set(final int point, final long nanoTime)
    {
        this.point = point;
        this.nanoTime = nanoTime;
    }
}
