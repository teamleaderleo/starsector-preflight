package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.PreparedAudio;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Deterministic WAV-to-PCM adapter for the synthetic prepared-audio workload. */
final class SyntheticAudioDecoder {
    private static final int MAX_SOURCE_BYTES = 32 * 1024 * 1024;

    private SyntheticAudioDecoder() {
    }

    static PreparedAudio decodeWave(
            String sourceSha256,
            String decoderPolicyIdentitySha256,
            byte[] source) throws IOException {
        Objects.requireNonNull(source, "source");
        if (source.length < 1 || source.length > MAX_SOURCE_BYTES) {
            throw new IOException("Synthetic audio source size is invalid");
        }
        try (AudioInputStream original = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(source))) {
            AudioFormat input = original.getFormat();
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    input.getSampleRate(),
                    16,
                    input.getChannels(),
                    input.getChannels() * 2,
                    input.getSampleRate(),
                    false);
            if (!target.matches(input)
                    && !AudioSystem.isConversionSupported(target, input)) {
                throw new IOException(
                        "Synthetic audio conversion is unsupported: " + input);
            }
            try (AudioInputStream pcm = target.matches(input)
                    ? original
                    : AudioSystem.getAudioInputStream(target, original)) {
                byte[] payload = pcm.readNBytes(PreparedAudio.MAX_PCM_BYTES + 1);
                if (payload.length > PreparedAudio.MAX_PCM_BYTES) {
                    throw new IOException("Synthetic decoded PCM exceeds limit");
                }
                int frameSize = target.getFrameSize();
                if (payload.length < 1
                        || frameSize < 1
                        || payload.length % frameSize != 0) {
                    throw new IOException(
                            "Synthetic decoded PCM has an incomplete frame");
                }
                return new PreparedAudio(
                        sourceSha256,
                        decoderPolicyIdentitySha256,
                        PreparedAudio.Policy.FULLY_DECODED_EFFECT,
                        PreparedAudio.PcmEncoding.PCM_SIGNED,
                        16,
                        PreparedAudio.ByteOrder.LITTLE_ENDIAN,
                        Math.round(target.getSampleRate()),
                        target.getChannels(),
                        payload.length / frameSize,
                        payload);
            }
        } catch (UnsupportedAudioFileException error) {
            throw new IOException("Synthetic encoded audio is unsupported", error);
        }
    }
}
