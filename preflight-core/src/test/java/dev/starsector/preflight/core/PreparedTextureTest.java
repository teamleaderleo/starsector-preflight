package dev.starsector.preflight.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PreparedTextureTest {
    private static final String HASH = "00".repeat(32);

    @Test
    void defensivelyCopiesPixelsAndPackedColors() {
        byte[] source = {1, 2, 3, 4};
        int color = PreparedTexture.rgba(10, 20, 30, 255);
        PreparedTexture texture = new PreparedTexture(
                HASH,
                PreparedTexture.Transformation.IDENTITY,
                1,
                1,
                1,
                1,
                4,
                color,
                color,
                color,
                source);

        source[0] = 99;
        byte[] returned = texture.pixels();
        returned[1] = 99;

        assertArrayEquals(new byte[] {1, 2, 3, 4}, texture.pixels());
        assertEquals(10, PreparedTexture.red(color));
        assertEquals(20, PreparedTexture.green(color));
        assertEquals(30, PreparedTexture.blue(color));
        assertEquals(255, PreparedTexture.alpha(color));
    }

    @Test
    void rejectsPayloadsThatDoNotMatchDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreparedTexture(
                        HASH,
                        PreparedTexture.Transformation.IDENTITY,
                        2,
                        2,
                        2,
                        2,
                        4,
                        0,
                        0,
                        0,
                        new byte[15]));
    }

    @Test
    void rejectsInvalidHashesChannelsAndDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreparedTexture(
                        "bad",
                        PreparedTexture.Transformation.IDENTITY,
                        1,
                        1,
                        1,
                        1,
                        4,
                        0,
                        0,
                        0,
                        new byte[4]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreparedTexture(
                        HASH,
                        PreparedTexture.Transformation.IDENTITY,
                        1,
                        1,
                        1,
                        1,
                        2,
                        0,
                        0,
                        0,
                        new byte[2]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreparedTexture(
                        HASH,
                        PreparedTexture.Transformation.IDENTITY,
                        0,
                        1,
                        1,
                        1,
                        4,
                        0,
                        0,
                        0,
                        new byte[4]));
    }
}
