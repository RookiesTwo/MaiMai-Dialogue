package top.rookiestwo.maimai_dialogue_editor.preview;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads Vorbis sample count from Ogg page granules without decoding the whole track to PCM. */
public final class OggPreviewInfo {
    private OggPreviewInfo() {}
    public static double duration(byte[] bytes, float sampleRate) throws IOException {
        if (!(sampleRate > 0)) throw new IOException("Invalid sample rate");
        var input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int offset = 0, serial = 0;
        long samples = -1;
        boolean first = true, ended = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 27 || input.getInt(offset) != 0x5367674f || input.get(offset + 4) != 0)
                throw new IOException("Invalid Ogg page");
            int stream = input.getInt(offset + 14);
            if (first) { serial = stream; first = false; }
            int segments = Byte.toUnsignedInt(input.get(offset + 26));
            if (bytes.length - offset < 27 + segments) throw new IOException("Truncated Ogg page");
            int size = 27 + segments;
            for (int i = 0; i < segments; i++) size += Byte.toUnsignedInt(input.get(offset + 27 + i));
            if (size > bytes.length - offset) throw new IOException("Truncated Ogg packet");
            if (stream == serial) {
                long granule = input.getLong(offset + 6);
                if (granule >= 0) samples = Math.max(samples, granule);
                ended |= (input.get(offset + 5) & 4) != 0;
            }
            offset += size;
        }
        if (!ended || samples < 0) throw new IOException("Missing Ogg end page");
        return samples / (double)sampleRate;
    }
}
