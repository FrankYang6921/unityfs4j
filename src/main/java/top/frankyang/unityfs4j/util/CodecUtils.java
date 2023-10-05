package top.frankyang.unityfs4j.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@UtilityClass
public class CodecUtils {
    private final int[][] LUMINANCE_TABLE = {
        {2, 8, -2, -8},
        {5, 17, -5, -17},
        {9, 29, -9, -29},
        {13, 42, -13, -42},
        {18, 60, -18, -60},
        {24, 80, -24, -80},
        {33, 106, -33, -106},
        {47, 183, -47, -183},
    };

    private final int[] SIGNED_INT3 = {0, 1, 2, 3, -4, -3, -2, -1};

    public BufferedImage decodeRgb24(byte[] data, int width, int height) {
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var raster = img.getRaster();
        for (int i = 0; i < data.length; i += 3) {
            int index = i / 3;
            int r = data[i] & 0xff,
                g = data[i + 1] & 0xff,
                b = data[i + 2] & 0xff;
            raster.setPixel(index % width, index / width, new int[]{r, g, b});
        }
        return img;
    }

    // line 751
    @SneakyThrows
    public BufferedImage decodeEtcRgb4(byte[] data, int width, int height) {
        if (true) {
            return EtcCodec.uncompress(new ByteArrayInputStream(data), width, height, EtcFormat.ETC1_RGB);
        }
        if (data.length % 8 != 0) {
            throw new IllegalArgumentException("data.length % 8 != 0");
        }
        if (width % 4 != 0 || height % 4 != 0) {
            throw new UnsupportedOperationException(width + "x" + height);
        }
        int xBlocks = width / 4;
        var img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var raster = img.getRaster();
        for (int i = 0; i < data.length; i += 8) {
            int blockIndex = i / 8;
            int xIndex = blockIndex % xBlocks;
            int yIndex = blockIndex / xBlocks;
            int xOffset = xIndex * 4;
            int yOffset = yIndex * 4;
            boolean flip = (data[i + 3] & 1) > 0;
            boolean diff = (data[i + 3] & 2) > 0;
            // Average RGB for 2 parts
            int r0, g0, b0, r1, g1, b1;
            if (diff) {
                r0 = (data[i] & 0xf8) >> 3;
                r1 = r0 + SIGNED_INT3[data[i] & 0x07];
                r0 = (r0 << 3) + (r0 >> 2);
                r1 = (r1 << 3) + (r1 >> 2);
                g0 = (data[i + 1] & 0xf8) >> 3;
                g1 = g0 + SIGNED_INT3[data[i + 1] & 0x07];
                g0 = (g0 << 3) + (g0 >> 2);
                g1 = (g1 << 3) + (g1 >> 2);
                b0 = (data[i + 2] & 0xf8) >> 3;
                b1 = b0 + SIGNED_INT3[data[i + 2] & 0x07];
                b0 = (b0 << 3) + (b0 >> 2);
                b1 = (b1 << 3) + (b1 >> 2);
            } else {
                r0 = data[i] & 0xf0;
                r0 += r0 >> 4;
                r1 = data[i] & 0x0f;
                r1 += r1 << 4;
                g0 = data[i + 1] & 0xf0;
                g0 += g0 >> 4;
                g1 = data[i + 1] & 0x0f;
                g1 += g1 << 4;
                b0 = data[i + 2] & 0xf0;
                b0 += b0 >> 4;
                b1 = data[i + 2] & 0x0f;
                b1 += b1 << 4;
            }
            int t0 = (data[i + 3] & 0xe0) >> 5;
            int t1 = (data[i + 3] & 0x1c) >> 2;
            int[] modifier0 = LUMINANCE_TABLE[t0];
            int[] modifier1 = LUMINANCE_TABLE[t1];
            int msbData = ((data[i + 4] & 0xff) << 8) + (data[i + 5] & 0xff);
            int lsbData = ((data[i + 6] & 0xff) << 8) + (data[i + 7] & 0xff);
            for (int j = 0; j < 16; j++) {
                boolean first;
                if (flip) {  // Horizontal
                    first = j % 4 < 2;
                } else {  // Vertical
                    first = j < 8;
                }
                int[] modifier = first ? modifier0 : modifier1;
                int msb = (msbData >> j) & 1;
                int lsb = (lsbData >> j) & 1;
                int delta = modifier[(msb << 1) + lsb];
                int r, g, b;
                if (first) {
                    r = clamp255(r0 + delta);
                    g = clamp255(g0 + delta);
                    b = clamp255(b0 + delta);
                } else {
                    r = clamp255(r1 + delta);
                    g = clamp255(g1 + delta);
                    b = clamp255(b1 + delta);
                }
                int pxXOffset = j / 4;
                int pxYOffset = j % 4;
                raster.setPixel(xOffset + pxXOffset, height - (yOffset + pxYOffset) - 1, new int[]{r, g, b});
            }
        }
        return img;
    }

    // line 1034
    @SneakyThrows
    public BufferedImage decodeEtc2Rgba8(byte[] data, int width, int height) {
        // 1481: ETC2_RGBA1
        return EtcCodec.uncompress(new ByteArrayInputStream(data), width, height, EtcFormat.ETC2_RGBA);
    }

    private int clamp255(int i) {
        return Math.min(Math.max(i, 0), 255);
    }
}
