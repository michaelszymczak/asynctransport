package dev.squaremile.trcheck.standalone;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TrCheckTest
{
    private static final int PORT = 8889;

    // run as first, when started run, measureRoundTripTime
    @Test
    @Disabled
    void runEchoApplication()
    {
        TrCheck.main(new String[]{"benchmark", "server", "-p", String.valueOf(PORT)});
    }

    @Test
    @Disabled
    void measureRoundTripTime()
    {
        final String remoteHost = "localhost";
        final int sendingRatePerSecond = 200000;
        final int respondToNth = 32;
        final int extraDataLength = 0;
        TrCheck.main(new String[]{
                "benchmark",
                "client",
                "-h",
                remoteHost,
                "-p",
                Integer.toString(PORT),
                "-w",
                Integer.toString(10),
                "-t",
                Integer.toString(5),
                "-s",
                Integer.toString(sendingRatePerSecond),
                "-r",
                Integer.toString(respondToNth),
                "-x",
                Integer.toString(extraDataLength),
                });
    }
}