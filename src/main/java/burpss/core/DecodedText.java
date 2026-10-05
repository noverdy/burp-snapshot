package burpss.core;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class DecodedText {

    private final byte[] bytes;
    private final String text;
    private final boolean utf8;

    private DecodedText(byte[] bytes, String text, boolean utf8) {
        this.bytes = bytes;
        this.text = text;
        this.utf8 = utf8;
    }

    public static DecodedText decode(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new DecodedText(bytes, s, true);
        } catch (CharacterCodingException e) {
            return new DecodedText(bytes, new String(bytes, StandardCharsets.ISO_8859_1), false);
        }
    }

    public String text() {
        return text;
    }

    public int charOffset(int byteOffset) {
        int clamped = Math.max(0, Math.min(byteOffset, bytes.length));
        if (!utf8) {
            return clamped;
        }
        return new String(bytes, 0, clamped, StandardCharsets.UTF_8).length();
    }
}
