package com.satya.urlshortener.service;

import com.satya.urlshortener.Service.BloomFilterService;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.protocol.CommandArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterServiceTest {

    @Mock
    private StatefulRedisConnection<String, String> connection;

    @Mock
    private RedisCommands<String, String> syncCommands;

    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        when(connection.sync()).thenReturn(syncCommands);
        bloomFilterService = new BloomFilterService(connection);
    }

    @Test
    void add_dispatches_BF_ADD_command() {
        // Act
        bloomFilterService.add("shorty");

        // Assert
        verify(syncCommands).dispatch(
                argThat(cmd -> "BF.ADD".equals(new String(cmd.getBytes(), java.nio.charset.StandardCharsets.UTF_8))),
                any(CommandOutput.class),
                any(CommandArgs.class)
        );
    }

    @Test
    void mightExist_returns_true_when_command_returns_true() {
        // Arrange
        when(syncCommands.dispatch(
                any(ProtocolKeyword.class),
                any(CommandOutput.class),
                any(CommandArgs.class)
        )).thenReturn(true);

        // Act
        boolean result = bloomFilterService.mightExist("shorty");

        // Assert
        assertThat(result).isTrue();
        verify(syncCommands).dispatch(
                argThat(cmd -> "BF.EXISTS".equals(new String(cmd.getBytes(), java.nio.charset.StandardCharsets.UTF_8))),
                any(CommandOutput.class),
                any(CommandArgs.class)
        );
    }

    @Test
    void mightExist_returns_false_when_command_returns_false() {
        // Arrange
        when(syncCommands.dispatch(
                any(ProtocolKeyword.class),
                any(CommandOutput.class),
                any(CommandArgs.class)
        )).thenReturn(false);

        // Act
        boolean result = bloomFilterService.mightExist("nonexistent");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void seedFromDatabase_with_empty_list_does_nothing() {
        // Act
        bloomFilterService.seedFromDatabase(Collections.emptyList());

        // Assert
        verify(syncCommands, never()).dispatch(any(), any(), any());
    }

    @Test
    void seedFromDatabase_with_elements_adds_each_element() {
        // Act
        bloomFilterService.seedFromDatabase(List.of("code1", "code2"));

        // Assert
        verify(syncCommands, times(2)).dispatch(
                argThat(cmd -> "BF.ADD".equals(new String(cmd.getBytes(), java.nio.charset.StandardCharsets.UTF_8))),
                any(CommandOutput.class),
                any(CommandArgs.class)
        );
    }
}
