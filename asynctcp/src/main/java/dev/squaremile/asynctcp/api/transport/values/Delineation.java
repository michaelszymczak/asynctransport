package dev.squaremile.asynctcp.api.transport.values;

import java.nio.ByteOrder;
import java.util.Objects;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;


import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class Delineation
{
    private final Type type;
    private final int padding;
    private final int extraLength;
    private final String pattern;

    /**
     * Prescribes a delineation of data (how to turn stream into messages).
     *
     * <pre>
     *     // The pattern extracts the length of the data in bytes, when evaluated as follows
     *
     *     int lengthInBytes(final String data, final String pattern)
     *     {
     *         Matcher matcher = Pattern.compile(pattern).matcher(data);
     *         if (matcher.find()) {
     *             return Integer.parseInt(matcher.group(1));
     *         }
     *         else
     *         {
     *             throw new IllegalArgumentException();
     *         }
     *     }
     *
     *     // the total length is then calculated using the following formula
     *     int length = lengthInBytes(data,pattern) + offset of the extracted pattern + extraLength
     * </pre>
     *
     * @param type        Delineation type (e.g. fixed length or length extracted from an ascii pattern)
     * @param padding     How many initial bytes to ignore
     * @param extraLength Length on top of the encoded length, can be treated as a fixed length when no length encoded
     * @param pattern     Describes how to extract the length in bytes, e.g. 8=[^\u0001]+\u00019=([0-9]+)\u0001
     */
    public Delineation(final Type type, final int padding, final int extraLength, final String pattern)
    {
        this.type = type;
        this.padding = padding;
        this.extraLength = extraLength;
        this.pattern = pattern;
    }

    public Type type()
    {
        return type;
    }

    public int padding()
    {
        return padding;
    }

    public int extraLength()
    {
        return extraLength;
    }

    /**
     * @return pattern that points to the ASCII-represented length
     */
    public String pattern()
    {
        return pattern;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(type, padding, extraLength, pattern);
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        final Delineation that = (Delineation)o;
        return padding == that.padding &&
               extraLength == that.extraLength &&
               type == that.type &&
               Objects.equals(pattern, that.pattern);
    }

    @Override
    public String toString()
    {
        return "Delineation{" +
               "type=" + type +
               ", padding=" + padding +
               ", extraLength=" + extraLength +
               ", pattern='" + pattern + '\'' +
               '}';
    }

    public enum Type
    {
        ASCII_PATTERN(0, (buffer, currentOffset) -> 0, (buffer, currentOffset, value) ->
        {
        }),
        FIXED_LENGTH(0, (buffer, currentOffset) -> 0, (buffer, currentOffset, value) ->
        {
        }),
        SHORT_BIG_ENDIAN_FIELD(Short.BYTES, (buffer, currentOffset) -> buffer.getShort(currentOffset, BIG_ENDIAN), (buffer, currentOffset, value) ->
        {
            buffer.putShort(currentOffset, (short)value, ByteOrder.BIG_ENDIAN);
        }),
        SHORT_LITTLE_ENDIAN_FIELD(Short.BYTES, (buffer, currentOffset) -> buffer.getShort(currentOffset, LITTLE_ENDIAN), (buffer, currentOffset, value) ->
        {
            buffer.putShort(currentOffset, (short)value, LITTLE_ENDIAN);
        }),
        INT_BIG_ENDIAN_FIELD(Integer.BYTES, (buffer, currentOffset) -> buffer.getInt(currentOffset, BIG_ENDIAN), (buffer, currentOffset, value) ->
        {
            buffer.putInt(currentOffset, value, ByteOrder.BIG_ENDIAN);
        }),
        INT_LITTLE_ENDIAN_FIELD(Integer.BYTES, (buffer, currentOffset) -> buffer.getInt(currentOffset, LITTLE_ENDIAN), (buffer, currentOffset, value) ->
        {
            buffer.putInt(currentOffset, value, LITTLE_ENDIAN);
        });

        public final int lengthFieldLength;
        private final LengthDecoder lengthDecoder;
        private final LengthEncoder lengthEncoder;

        Type(final int lengthFieldLength, final LengthDecoder lengthDecoder, final LengthEncoder lengthEncoder)
        {
            this.lengthFieldLength = lengthFieldLength;
            this.lengthDecoder = lengthDecoder;
            this.lengthEncoder = lengthEncoder;
        }

        public int readLength(final DirectBuffer buffer, final int currentOffset)
        {
            return lengthDecoder.readLength(buffer, currentOffset);
        }

        public void writeLength(final MutableDirectBuffer buffer, final int currentOffset, final int value)
        {
            lengthEncoder.writeLength(buffer, currentOffset, value);
        }

        interface LengthDecoder
        {
            int readLength(final DirectBuffer buffer, final int currentOffset);
        }

        interface LengthEncoder
        {
            void writeLength(final MutableDirectBuffer buffer, final int currentOffset, final int value);
        }
    }
}
