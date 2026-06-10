package com.satya.urlshortener.Service;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.BooleanOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class BloomFilterService {

    private static final String BLOOM_KEY = "bloom:links";
    private static final Logger log = LoggerFactory.getLogger(BloomFilterService.class);

    private final StatefulRedisConnection<String, String> connection;

    public BloomFilterService(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    private enum BloomCommand implements ProtocolKeyword {
        BF_ADD("BF.ADD"),
        BF_EXISTS("BF.EXISTS");

        private final byte[] nameBytes;

        BloomCommand(String name) {
            this.nameBytes = name.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getBytes() {
            return nameBytes;
        }
    }

    public void add(String shortCode) {
        CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                .add(BLOOM_KEY)
                .add(shortCode);
        connection.sync().dispatch(BloomCommand.BF_ADD, new BooleanOutput<>(StringCodec.UTF8), args);
        log.debug("Bloom filter: added '{}'", shortCode);
    }

    public boolean mightExist(String shortCode) {
        CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                .add(BLOOM_KEY)
                .add(shortCode);
        Boolean result = connection.sync().dispatch(
                BloomCommand.BF_EXISTS,
                new BooleanOutput<>(StringCodec.UTF8),
                args
        );
        boolean exists = Boolean.TRUE.equals(result);
        log.debug("Bloom filter: mightExist('{}') = {}", shortCode, exists);
        return exists;
    }

    public void seedFromDatabase(List<String> shortCodes) {
        if (shortCodes.isEmpty()) {
            log.info("Bloom filter: no short codes to seed");
            return;
        }
        shortCodes.forEach(this::add);
        log.info("Bloom filter: seeded with {} short codes", shortCodes.size());
    }
}