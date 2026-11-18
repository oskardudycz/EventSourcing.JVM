package io.eventdriven.eventstores.mongodb.codecs;

import java.time.OffsetDateTime;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class OffsetDateTimeCodec implements Codec<OffsetDateTime> {
    @Override
    public OffsetDateTime decode(BsonReader reader, DecoderContext decoderContext) {
        return OffsetDateTime.parse(reader.readString());
    }

    @Override
    public void encode(BsonWriter writer, OffsetDateTime value, EncoderContext encoderContext) {
        writer.writeString(value.toString());
    }

    @Override
    public Class<OffsetDateTime> getEncoderClass() {
        return OffsetDateTime.class;
    }
}
