package dev.starsector.preflight.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class SoundWrapperObservationChildTest {
    @Test
    void observesPayloadAndMetadataWithoutExportingDecodedBytes() {
        byte[] pcm = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        ObservedSound value = new ObservedSound(pcm, 2, 44_100, "repository-owned-secret");
        SoundWrapperObservationChild.Decoded direct =
                new SoundWrapperObservationChild.Decoded(pcm, 2, 44_100, 1, 0);

        SoundWrapperObservationChild.FieldSummary summary =
                SoundWrapperObservationChild.observeFields(value, direct);
        String rendered = summary.toMap().toString();

        assertTrue(summary.payloadMatched(), rendered);
        assertTrue(summary.metadataCandidatesPresent(), rendered);
        assertTrue(rendered.contains("string-redacted"), rendered);
        assertFalse(rendered.contains("repository-owned-secret"), rendered);
    }

    private static final class ObservedSound {
        private final ByteBuffer pcm;
        private final int channels;
        private final int sampleRate;
        private final String label;

        private ObservedSound(byte[] pcm, int channels, int sampleRate, String label) {
            this.pcm = ByteBuffer.wrap(pcm.clone());
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.label = label;
        }
    }
}
