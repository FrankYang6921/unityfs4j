package top.frankyang.unityfs4j.util;

import lombok.experimental.UtilityClass;
import top.frankyang.unityfs4j.io.EndianDataInputStream;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static top.frankyang.unityfs4j.util.EtcFormat.*;

@SuppressWarnings("DuplicatedCode")
@UtilityClass
class EtcCodec {
    private final byte R = 0, G = 1, B = 2, A = 3;

    private final int[][] ALPHA_TABLE = new int[256][8];

    private final int[][] ALPHA_BASE = {
        {-15, -9, -6, -3},
        {-13, -10, -7, -3},
        {-13, -8, -5, -2},
        {-13, -6, -4, -2},
        {-12, -8, -6, -3},
        {-11, -9, -7, -3},
        {-11, -8, -7, -4},
        {-11, -8, -5, -3},
        {-10, -8, -6, -2},
        {-10, -8, -5, -2},
        {-10, -8, -4, -2},
        {-10, -7, -5, -2},
        {-10, -7, -4, -3},
        {-10, -3, -2, -1},
        {-9, -8, -6, -4},
        {-9, -7, -5, -3}
    };

    private final short[] TABLE_59T = {3, 6, 11, 16, 23, 32, 41, 64};

    private final short[] TABLE_58H = {3, 6, 11, 16, 23, 32, 41, 64};

    private final int[][] COMPRESS_PARAMS = {
        {-8, -2, 2, 8},
        {-8, -2, 2, 8},
        {-17, -5, 5, 17},
        {-17, -5, 5, 17},
        {-29, -9, 9, 29},
        {-29, -9, 9, 29},
        {-42, -13, 13, 42},
        {-42, -13, 13, 42},
        {-60, -18, 18, 60},
        {-60, -18, 18, 60},
        {-80, -24, 24, 80},
        {-80, -24, 24, 80},
        {-106, -33, 33, 106},
        {-106, -33, 33, 106},
        {-183, -47, 47, 183},
        {-183, -47, 47, 183}
    };

    private final int[] UNSCRAMBLE = {2, 3, 1, 0};

    private boolean alphaTableSetup = false;

    BufferedImage uncompress(InputStream input, int width, int height, EtcFormat format) throws IOException {
        byte[][] raw = uncompress0(input, width, height, format);
        byte[] rawImg = raw[0];
        byte[] rawAlphaImg = raw[1];
        switch (format) {
            case ETC1_RGB, ETC2_RGB -> {
                var img = new BufferedImage(width, height, TYPE_INT_RGB);
                assert rawImg.length == 3 * width * height;
                writeRGB888(img, rawImg);
                return img;
            }
            case ETC2_RGBA, ETC2_RGBA1 -> {
                var img = new BufferedImage(width, height, TYPE_INT_ARGB);
                assert rawImg.length == 3 * width * height;
                assert rawAlphaImg.length == 2 * width * height;
                writeRGBA8888(img, rawImg, rawAlphaImg);
                return img;
            }
            default -> throw new UnsupportedOperationException(format.toString());
        }
    }

    void writeRGB888(BufferedImage img, byte[] rawImg) {
        var raster = img.getRaster();
        int width = img.getWidth();
        int height = img.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int idx = 3 * (y * width + x);
                raster.setPixel(x, height - y - 1, new int[]{
                    Byte.toUnsignedInt(rawImg[idx + R]),
                    Byte.toUnsignedInt(rawImg[idx + G]),
                    Byte.toUnsignedInt(rawImg[idx + B])
                });
            }
        }
    }

    void writeRGBA8888(BufferedImage img, byte[] rawImg, byte[] rawAlphaImg) {
        var raster = img.getRaster();
        int width = img.getWidth();
        int height = img.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int idx = 3 * (y * width + x);
                raster.setPixel(x, height - y - 1, new int[]{
                    Byte.toUnsignedInt(rawAlphaImg[y * width + x + A]),
                    Byte.toUnsignedInt(rawImg[idx + R]),
                    Byte.toUnsignedInt(rawImg[idx + G]),
                    Byte.toUnsignedInt(rawImg[idx + B])
                });
            }
        }
    }

    byte[][] uncompress0(InputStream input, int width, int height, EtcFormat format) throws IOException {
        var endianInput = new EndianDataInputStream(input);
        boolean signed = false;
        format = switch (format) {
            case ETC2_SIGNED_RG -> {
                signed = true;
                yield ETC2_RG;
            }
            case ETC2_SIGNED_R -> {
                signed = true;
                yield ETC2_R;
            }
            default -> format;
        };
        byte[] rawImg, rawAlphaImg = null, rawAlphaImg2 = null;
        if (format == ETC2_RG) {
            rawImg = new byte[3 * width * height * 2];
        } else {
            rawImg = new byte[3 * width * height];
        }
        if (format == ETC2_RGBA || format == ETC2_R || format == ETC2_RG || format == ETC2_RGBA1) {
            rawAlphaImg = new byte[width * height * 2];
            setupAlphaTable();
        }
        if (format == ETC2_RG) {
            rawAlphaImg2 = new byte[width * height * 2];
        }
        for (int y = 0; y < height / 4; y++) {
            for (int x = 0; x < width / 4; x++) {
                // decode alpha channel for RGBA
                if (format == ETC2_RGBA) {
                    var alphaBlock = endianInput.readNBytes(8);
                    decompressBlockAlpha(alphaBlock, rawAlphaImg, width, 4 * x, 4 * y);
                }
                // color channels for most normal modes
                if (format != ETC2_R && format != ETC2_RG) {
                    // we have normal ETC2 color channels, decompress these
                    long blockPart1 = endianInput.readUnsignedInt();
                    long blockPart2 = endianInput.readUnsignedInt();
                    if (format == ETC2_RGBA1) {
                        decompressBlockETC2A1(blockPart1, blockPart2, rawImg, rawAlphaImg, width, 4 * x, 4 * y);
                    } else {
                        decompressBlockETC2(blockPart1, blockPart2, rawImg, width, 4 * x, 4 * y);
                    }
                } else {  // one or two 11-bit alpha channels for R or RG
                    var alphaBlock = endianInput.readNBytes(8);
                    decompressBlockAlpha16(alphaBlock, rawAlphaImg, width, 4 * x, 4 * y, signed);
                }
                if (format == ETC2_RG) {
                    var alphaBlock = endianInput.readNBytes(8);
                    decompressBlockAlpha16(alphaBlock, rawAlphaImg2, width, 4 * x, 4 * y, signed);
                }
            }
        }
        if (format == ETC2_RG) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = 6 * (y * width + x);
                    rawImg[idx] = rawAlphaImg[2 * (y * width + x)];
                    rawImg[idx + 1] = rawAlphaImg[2 * (y * width + x) + 1];
                    rawImg[idx + 2] = rawAlphaImg2[2 * (y * width + x)];
                    rawImg[idx + 3] = rawAlphaImg2[2 * (y * width + x) + 1];
                    rawImg[idx + 4] = 0;
                    rawImg[idx + 5] = 0;
                }
            }
        }
        return new byte[][]{rawImg, rawAlphaImg};
    }

    void decompressBlockAlpha(byte[] data, byte[] img, int width, int ix, int iy) {
        // decompressBlockAlphaC
        int alpha = Byte.toUnsignedInt(data[0]);
        int table = Byte.toUnsignedInt(data[1]);
        int bit = 0;
        int bite = 2;
        // extract an alpha value for each pixel
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                // extract table index
                int index = 0;
                for (int bitPos = 0; bitPos < 3; bitPos++) {
                    index |= getBit(data[bite], 7 - bit, 2 - bitPos);
                    bit++;
                    if (bit > 7) {
                        bit = 0;
                        bite++;
                    }
                }
                img[ix + x + (iy + y) * width] = (byte) clamp(alpha + ALPHA_TABLE[table][index]);
            }
        }
    }

    void decompressBlockAlpha16(byte[] data, byte[] img, int width, int ix, int iy, boolean signed) {
        int alpha = Byte.toUnsignedInt(data[0]);
        int table = Byte.toUnsignedInt(data[1]);

        if (signed) {
            // if we have a signed format, the base value is given as a signed byte. We convert it to (0-255) here,
            // so more code can be shared with the unsigned mode.
            alpha = data[0];
            alpha += 128;
        }

        int bit = 0;
        int bite = 2;
        // extract an alpha value for each pixel.
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                // Extract table index
                int index = 0;
                for (int bitPos = 0; bitPos < 3; bitPos++) {
                    index |= getBit(data[bite], 7 - bit, 2 - bitPos);
                    bit++;
                    if (bit > 7) {
                        bit = 0;
                        bite++;
                    }
                }
                int idx = 3 * 2 * (ix + x + (iy + y) * width);

                // make data compatible with the .pgm format. See the comment in compressBlockAlpha16() for details.
                int u16;
                if (signed) {
                    // the pgm-format only allows unsigned images,
                    // so we add 2^15 to get a 16-bit value.
                    u16 = get16Bits11Signed(alpha, table % 16, table / 16, index) + 256 * 128;
                } else {
                    u16 = get16Bits11Bits(alpha, table % 16, table / 16, index);
                }
                // byte swap for pgm
                img[idx] = (byte) (u16 / 256);
                img[idx + 1] = (byte) (u16 % 256);
            }
        }
    }

    void decompressBlockETC2A1(long blockPart1, long blockPart2, byte[] img, byte[] alphaImg, int width, int ix, int iy) {
        // decompressBlockETC21BitAlphaC
        boolean diffBit;
        byte[] color1 = new byte[3];
        byte[] diff = new byte[3];
        byte red, green, blue;
        int channelsA = 1;

        diffBit = getBitsHigh(blockPart1, 1, 33) != 0;

        // Base color
        color1[0] = (byte) getBitsHigh(blockPart1, 5, 63);
        color1[1] = (byte) getBitsHigh(blockPart1, 5, 55);
        color1[2] = (byte) getBitsHigh(blockPart1, 5, 47);
        // Diff color
        diff[0] = (byte) getBitsHigh(blockPart1, 3, 58);
        diff[1] = (byte) getBitsHigh(blockPart1, 3, 50);
        diff[2] = (byte) getBitsHigh(blockPart1, 3, 42);
        // Extend sign bit to entire byte.
        diff[0] = (byte) (diff[0] << 5);
        diff[1] = (byte) (diff[1] << 5);
        diff[2] = (byte) (diff[2] << 5);
        diff[0] = (byte) (diff[0] >> 5);
        diff[1] = (byte) (diff[1] >> 5);
        diff[2] = (byte) (diff[2] >> 5);

        red = (byte) (color1[0] + diff[0]);
        green = (byte) (color1[1] + diff[1]);
        blue = (byte) (color1[2] + diff[2]);

        if (diffBit) {
            // We have diffBit = 1, meaning no transparent pixels. regular decompression.
            if (red < 0 || red > 31) {
                var block59 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockThumb59T(block59.part1, block59.part2, img, width, ix, iy);
            } else if (green < 0 || green > 31) {
                var block58 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockThumb58H(block58.part1, block58.part2, img, width, ix, iy);
            } else if (blue < 0 || blue > 31) {
                var block57 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockPlanar57(block57.part1, block57.part2, img, width, ix, iy);
            } else {
                decompressBlockDiffAlpha(blockPart1, blockPart2, img, alphaImg, width, ix, iy);
            }
            for (int x = ix; x < ix + 4; x++) {
                for (int y = iy; y < iy + 4; y++) {
                    alphaImg[channelsA * (x + y * width)] = (byte) 255;
                }
            }
        } else {
            // We have diffBit = 0, transparent pixels. Only T-, H- or regular diff-mode possible.
            if (red < 0 || red > 31) {
                var block59 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockThumb59TAlpha(block59.part1, block59.part2, img, alphaImg, width, ix, iy);
            } else if (green < 0 || green > 31) {
                var block58 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockThumb58HAlpha(block58.part1, block58.part2, img, alphaImg, width, ix, iy);
            } else if (blue < 0 || blue > 31) {
                var block57 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockPlanar57(block57.part1, block57.part2, img, width, ix, iy);
                for (int x = ix; x < ix + 4; x++) {
                    for (int y = iy; y < iy + 4; y++) {
                        alphaImg[channelsA * (x + y * width)] = (byte) 255;
                    }
                }
            } else {
                decompressBlockDiffAlpha(blockPart1, blockPart2, img, alphaImg, width, ix, iy);
            }
        }
    }

    void decompressBlockThumb59TAlpha(long blockPart1, long blockPart2, byte[] img, byte[] alphaImg, int width, int ix, int iy) {
        short[][] colorsRGB444 = new short[2][3];
        short[][] colors = new short[2][3];
        short[][] paintColors = new short[4][3];
        short distance;
        short[][] blockMask = new short[4][4];
        int channelsA = 1;

        // First decode left part of block.
        colorsRGB444[0][R] = (short) getBitsHigh(blockPart1, 4, 58);
        colorsRGB444[0][G] = (short) getBitsHigh(blockPart1, 4, 54);
        colorsRGB444[0][B] = (short) getBitsHigh(blockPart1, 4, 50);

        colorsRGB444[1][R] = (short) getBitsHigh(blockPart1, 4, 46);
        colorsRGB444[1][G] = (short) getBitsHigh(blockPart1, 4, 42);
        colorsRGB444[1][B] = (short) getBitsHigh(blockPart1, 4, 38);

        distance = (short) getBitsHigh(blockPart1, 3, 34);

        // Extend the two colors to RGB888
        decompressColor(4, 4, 4, colorsRGB444, colors);
        calcPaintColors59T(distance, colors, paintColors);

        // Choose one of the four paint colors for each texel
        for (int x = 0; x < 4; ++x) {
            for (int y = 0; y < 4; ++y) {
                blockMask[x][y] = (short) (getBitsLow(blockPart2, 1, (y + x * 4) + 16) << 1);
                blockMask[x][y] |= getBitsLow(blockPart2, 1, (y + x * 4));
                int idx = 3 * ((iy + y) * width + ix + x);
                img[idx + R] = (byte) clamp(paintColors[blockMask[x][y]][R]); // RED
                img[idx + G] = (byte) clamp(paintColors[blockMask[x][y]][G]); // GREEN
                img[idx + B] = (byte) clamp(paintColors[blockMask[x][y]][B]); // BLUE
                int alphaIdx = channelsA * (x + ix + (y + iy) * width);
                if (blockMask[x][y] == 2) {
                    alphaImg[alphaIdx] = 0;
                    img[idx + R] = 0;
                    img[idx + G] = 0;
                    img[idx + B] = 0;
                } else {
                    alphaImg[alphaIdx] = (byte) 255;
                }
            }
        }
    }

    void decompressBlockThumb58HAlpha(long blockPart1, long blockPart2, byte[] img, byte[] alphaImg, int width, int ix, int iy) {
        long col0, col1;
        short[][] colors = new short[2][3];
        short[][] colorsRGB444 = new short[2][3];
        short[][] paintColors = new short[4][3];
        short distance;
        short[][] blockMask = new short[4][4];
        int channelsA = 1;

        // First decode left part of block.
        colorsRGB444[0][R] = (short) getBitsHigh(blockPart1, 4, 57);
        colorsRGB444[0][G] = (short) getBitsHigh(blockPart1, 4, 53);
        colorsRGB444[0][B] = (short) getBitsHigh(blockPart1, 4, 49);

        colorsRGB444[1][R] = (short) getBitsHigh(blockPart1, 4, 45);
        colorsRGB444[1][G] = (short) getBitsHigh(blockPart1, 4, 41);
        colorsRGB444[1][B] = (short) getBitsHigh(blockPart1, 4, 37);

        distance = (short) (getBitsHigh(blockPart1, 2, 33) << 1);

        col0 = getBitsHigh(blockPart1, 12, 57);
        col1 = getBitsHigh(blockPart1, 12, 45);

        if (col0 >= col1) {
            distance |= 1;
        }

        // Extend the two colors to RGB888
        decompressColor(4, 4, 4, colorsRGB444, colors);
        calcPaintColors58H(distance, colors, paintColors);

        // Choose one of the four paint colors for each texel
        for (int x = 0; x < 4; ++x) {
            for (int y = 0; y < 4; ++y) {
                blockMask[x][y] = (short) (getBitsLow(blockPart2, 1, (y + x * 4) + 16) << 1);
                blockMask[x][y] |= getBitsLow(blockPart2, 1, (y + x * 4));
                int idx = 3 * ((iy + y) * width + ix + x);
                img[idx + R] = (byte) clamp(paintColors[blockMask[x][y]][R]); // RED
                img[idx + G] = (byte) clamp(paintColors[blockMask[x][y]][G]); // GREEN
                img[idx + B] = (byte) clamp(paintColors[blockMask[x][y]][B]); // BLUE

                int alphaIdx = channelsA * (x + ix + (y + iy) * width);
                if (blockMask[x][y] == 2) {
                    alphaImg[alphaIdx] = 0;
                    img[idx + R] = 0;
                    img[idx + G] = 0;
                    img[idx + B] = 0;
                } else {
                    alphaImg[alphaIdx] = (byte) 255;
                }
            }
        }
    }

    void decompressBlockDiffAlpha(long blockPart1, long blockPart2, byte[] img, byte[] alphaImg, int width, int ix, int iy) {
        short[] avgColor = new short[3],
            encColor1 = new short[3],
            encColor2 = new short[3];
        byte[] diff = new byte[3];
        int table;
        int index, shift;
        boolean diffBit;
        boolean flipBit;
        int channelsA = 1;

        //the diffBit now encodes whether the entire alpha channel is 255.
        diffBit = getBitsHigh(blockPart1, 1, 33) != 0;
        flipBit = getBitsHigh(blockPart1, 1, 32) != 0;

        // First decode left part of block.
        encColor1[0] = (short) getBitsHigh(blockPart1, 5, 63);
        encColor1[1] = (short) getBitsHigh(blockPart1, 5, 55);
        encColor1[2] = (short) getBitsHigh(blockPart1, 5, 47);

        // Expand from 5 to 8 bits
        avgColor[0] = (short) (encColor1[0] << 3 | encColor1[0] >> 2);
        avgColor[1] = (short) (encColor1[1] << 3 | encColor1[1] >> 2);
        avgColor[2] = (short) (encColor1[2] << 3 | encColor1[2] >> 2);

        table = (int) (getBitsHigh(blockPart1, 3, 39) << 1);

        long pixelIndicesMSB, pixelIndicesLSB;

        pixelIndicesMSB = getBitsLow(blockPart2, 16, 31);
        pixelIndicesLSB = getBitsLow(blockPart2, 16, 15);

        shift = 0;
        if (!flipBit) {
            // We should not flip
            for (int x = ix; x < ix + 2; x++) {
                for (int y = iy; y < iy + 4; y++) {
                    index = (int) ((pixelIndicesMSB >> shift & 1) << 1);
                    index |= ((pixelIndicesLSB >> shift) & 1);
                    shift++;
                    index = UNSCRAMBLE[index];

                    int mod = COMPRESS_PARAMS[table][index];
                    if (!diffBit && (index == 1 || index == 2)) {
                        mod = 0;
                    }
                    int idx = 3 * (y * width + x);
                    img[idx + R] = (byte) clamp(avgColor[0] + mod);
                    img[idx + G] = (byte) clamp(avgColor[1] + mod);
                    img[idx + B] = (byte) clamp(avgColor[2] + mod);
                    if (!diffBit && index == 1) {
                        alphaImg[(y * width + x) * channelsA] = 0;
                        img[idx + R] = 0;
                        img[idx + G] = 0;
                        img[idx + B] = 0;
                    } else {
                        alphaImg[(y * width + x) * channelsA] = (byte) 255;
                    }
                }
            }
        } else {
            // We should flip
            for (int x = ix; x < ix + 4; x++) {
                for (int y = iy; y < iy + 2; y++) {
                    index = (int) ((pixelIndicesMSB >> shift & 1) << 1);
                    index |= ((pixelIndicesLSB >> shift) & 1);
                    shift++;
                    index = UNSCRAMBLE[index];
                    int mod = COMPRESS_PARAMS[table][index];
                    if (!diffBit && (index == 1 || index == 2)) {
                        mod = 0;
                    }
                    int idx = 3 * (y * width + x);
                    img[idx + R] = (byte) clamp(avgColor[0] + mod);
                    img[idx + G] = (byte) clamp(avgColor[1] + mod);
                    img[idx + B] = (byte) clamp(avgColor[2] + mod);
                    if (!diffBit && index == 1) {
                        alphaImg[(y * width + x) * channelsA] = 0;
                        img[idx + R] = 0;
                        img[idx + G] = 0;
                        img[idx + B] = 0;
                    } else {
                        alphaImg[(y * width + x) * channelsA] = (byte) 255;
                    }
                }
                shift += 2;
            }
        }
        // Now decode right part of block.
        diff[0] = (byte) getBitsHigh(blockPart1, 3, 58);
        diff[1] = (byte) getBitsHigh(blockPart1, 3, 50);
        diff[2] = (byte) getBitsHigh(blockPart1, 3, 42);

        // Extend sign bit to entire byte.
        diff[0] = (byte) (diff[0] << 5);
        diff[1] = (byte) (diff[1] << 5);
        diff[2] = (byte) (diff[2] << 5);
        diff[0] = (byte) (diff[0] >> 5);
        diff[1] = (byte) (diff[1] >> 5);
        diff[2] = (byte) (diff[2] >> 5);

        //  Calculate second color
        encColor2[0] = (short) (encColor1[0] + diff[0]);
        encColor2[1] = (short) (encColor1[1] + diff[1]);
        encColor2[2] = (short) (encColor1[2] + diff[2]);

        // Expand from 5 to 8 bits
        avgColor[0] = (short) (encColor2[0] << 3 | encColor2[0] >> 2);
        avgColor[1] = (short) (encColor2[1] << 3 | encColor2[1] >> 2);
        avgColor[2] = (short) (encColor2[2] << 3 | encColor2[2] >> 2);

        table = (int) (getBitsHigh(blockPart1, 3, 36) << 1);
        pixelIndicesMSB = getBitsLow(blockPart2, 16, 31);
        pixelIndicesLSB = getBitsLow(blockPart2, 16, 15);

        if (!flipBit) {
            // We should not flip
            shift = 8;
            for (int x = ix + 2; x < ix + 4; x++) {
                for (int y = iy; y < iy + 4; y++) {
                    index = (int) ((pixelIndicesMSB >> shift & 1) << 1);
                    index |= ((pixelIndicesLSB >> shift) & 1);
                    shift++;
                    index = UNSCRAMBLE[index];
                    int mod = COMPRESS_PARAMS[table][index];
                    if (!diffBit && (index == 1 || index == 2)) {
                        mod = 0;
                    }

                    int idx = 3 * (y * width + x);
                    img[idx + R] = (byte) clamp(avgColor[0] + mod);
                    img[idx + G] = (byte) clamp(avgColor[1] + mod);
                    img[idx + B] = (byte) clamp(avgColor[2] + mod);
                    if (!diffBit && index == 1) {
                        alphaImg[(y * width + x) * channelsA] = 0;
                        img[idx + R] = 0;
                        img[idx + G] = 0;
                        img[idx + B] = 0;
                    } else {
                        alphaImg[(y * width + x) * channelsA] = (byte) 255;
                    }
                }
            }
        } else {
            // We should flip
            shift = 2;
            for (int x = ix; x < ix + 4; x++) {
                for (int y = iy + 2; y < iy + 4; y++) {
                    index = (int) ((pixelIndicesMSB >> shift & 1) << 1);
                    index |= ((pixelIndicesLSB >> shift) & 1);
                    shift++;
                    index = UNSCRAMBLE[index];
                    int mod = COMPRESS_PARAMS[table][index];
                    if (!diffBit && (index == 1 || index == 2)) {
                        mod = 0;
                    }

                    int idx = 3 * (y * width + x);
                    img[idx + R] = (byte) clamp(avgColor[0] + mod);
                    img[idx + G] = (byte) clamp(avgColor[1] + mod);
                    img[idx + B] = (byte) clamp(avgColor[2] + mod);
                    if (!diffBit && index == 1) {
                        alphaImg[(y * width + x) * channelsA] = 0;
                        img[idx + R] = 0;
                        img[idx + G] = 0;
                        img[idx + B] = 0;
                    } else {
                        alphaImg[(y * width + x) * channelsA] = (byte) 255;
                    }
                }
                shift += 2;
            }
        }
    }

    void decompressBlockETC2(long blockPart1, long blockPart2, byte[] img, int width, int ix, int iy) {
        // decompressBlockETC2c
        int diffBit;
        byte[] color1 = new byte[3];
        byte[] diff = new byte[3];
        byte red, green, blue;

        diffBit = (int) getBitsHigh(blockPart1, 1, 33);
        if (diffBit == 1) {
            // Base color
            color1[0] = (byte) getBitsHigh(blockPart1, 5, 63);
            color1[1] = (byte) getBitsHigh(blockPart1, 5, 55);
            color1[2] = (byte) getBitsHigh(blockPart1, 5, 47);
            // Diff color
            diff[0] = (byte) getBitsHigh(blockPart1, 3, 58);
            diff[1] = (byte) getBitsHigh(blockPart1, 3, 50);
            diff[2] = (byte) getBitsHigh(blockPart1, 3, 42);

            diff[0] = (byte) (diff[0] << 5);
            diff[1] = (byte) (diff[1] << 5);
            diff[2] = (byte) (diff[2] << 5);
            diff[0] = (byte) (diff[0] >> 5);
            diff[1] = (byte) (diff[1] >> 5);
            diff[2] = (byte) (diff[2] >> 5);

            red = (byte) (color1[0] + diff[0]);
            green = (byte) (color1[1] + diff[1]);
            blue = (byte) (color1[2] + diff[2]);

            if (red < 0 || red > 31) {
                var block59 = unStuff59Bits(blockPart1, blockPart2);
                decompressBlockThumb59T(block59.part1, block59.part2, img, width, ix, iy);
            } else if (green < 0 || green > 31) {
                var block58 = unStuff58Bits(blockPart1, blockPart2);
                decompressBlockThumb58H(block58.part1, block58.part2, img, width, ix, iy);
            } else if (blue < 0 || blue > 31) {
                var block57 = unStuff57Bits(blockPart1, blockPart2);
                decompressBlockPlanar57(block57.part1, block57.part2, img, width, ix, iy);
            } else {
                decompressBlockDiffFlip(blockPart1, blockPart2, img, width, ix, iy);
            }
        } else {
            decompressBlockDiffFlip(blockPart1, blockPart2, img, width, ix, iy);
        }
    }

    UIntPair unStuff59Bits(long blockPart1, long blockPart2) {
        short R0a;
        long word1 = blockPart1 >> 1;
        word1 = putBitsHigh(word1, blockPart1, 1, 32);
        R0a = (short) getBitsHigh(blockPart1, 2, 60);
        word1 = putBitsHigh(word1, R0a, 2, 58);
        word1 = putBitsHigh(word1, 0, 5, 63);

        return new UIntPair(word1, blockPart2);
    }

    UIntPair unStuff58Bits(long blockPart1, long blockPart2) {
        long part0, part1, part2, part3;
        part0 = getBitsHigh(blockPart1, 7, 62);
        part1 = getBitsHigh(blockPart1, 2, 52);
        part2 = getBitsHigh(blockPart1, 16, 49);
        part3 = getBitsHigh(blockPart1, 1, 32);
        long word1 = 0;
        word1 = putBitsHigh(word1, part0, 7, 57);
        word1 = putBitsHigh(word1, part1, 2, 50);
        word1 = putBitsHigh(word1, part2, 16, 48);
        word1 = putBitsHigh(word1, part3, 1, 32);

        return new UIntPair(word1, blockPart2);
    }

    UIntPair unStuff57Bits(long blockPart1, long blockPart2) {
        short RO, GO1, GO2, BO1, BO2, BO3, RH1, RH2, GH, BH, RV, GV, BV;
        RO = (short) getBitsHigh(blockPart1, 6, 62);
        GO1 = (short) getBitsHigh(blockPart1, 1, 56);
        GO2 = (short) getBitsHigh(blockPart1, 6, 54);
        BO1 = (short) getBitsHigh(blockPart1, 1, 48);
        BO2 = (short) getBitsHigh(blockPart1, 2, 44);
        BO3 = (short) getBitsHigh(blockPart1, 3, 41);
        RH1 = (short) getBitsHigh(blockPart1, 5, 38);
        RH2 = (short) getBitsHigh(blockPart1, 1, 32);
        GH = (short) getBitsLow(blockPart2, 7, 31);
        BH = (short) getBitsLow(blockPart2, 6, 24);
        RV = (short) getBitsLow(blockPart2, 6, 18);
        GV = (short) getBitsLow(blockPart2, 7, 12);
        BV = (short) getBitsLow(blockPart2, 6, 5);

        long word1 = 0, word2 = 0;
        word1 = putBitsHigh(word1, RO, 6, 63);
        word1 = putBitsHigh(word1, GO1, 1, 57);
        word1 = putBitsHigh(word1, GO2, 6, 56);
        word1 = putBitsHigh(word1, BO1, 1, 50);
        word1 = putBitsHigh(word1, BO2, 2, 49);
        word1 = putBitsHigh(word1, BO3, 3, 47);
        word1 = putBitsHigh(word1, RH1, 5, 44);
        word1 = putBitsHigh(word1, RH2, 1, 39);
        word1 = putBitsHigh(word1, GH, 7, 38);
        word2 = putBitsLow(word2, BH, 6, 31);
        word2 = putBitsLow(word2, RV, 6, 25);
        word2 = putBitsLow(word2, GV, 7, 19);
        word2 = putBitsLow(word2, BV, 6, 12);

        return new UIntPair(word1, word2);
    }

    void writeBlock(long data, byte[] img, int width, int ix, int iy, short[][] paintColors, short[][] blockMask) {
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                blockMask[x][y] = (short) (getBitsLow(data, 1, (y + x * 4) + 16) << 1);
                blockMask[x][y] |= getBitsLow(data, 1, (y + x * 4));
                int idx = 3 * ((iy + y) * width + ix + x);
                img[idx + R] = (byte) clamp(paintColors[blockMask[x][y]][R]); // RED`
                img[idx + G] = (byte) clamp(paintColors[blockMask[x][y]][G]); // GREEN
                img[idx + B] = (byte) clamp(paintColors[blockMask[x][y]][B]); // BLUE
            }
        }
    }

    void decompressBlockThumb59T(long blockPart1, long blockPart2, byte[] img, int width, int ix, int iy) {
        short[][] colorsRGB444 = new short[2][3];
        short[][] colors = new short[2][3];
        short[][] paintColors = new short[4][3];
        short distance;
        short[][] blockMask = new short[4][4];
        colorsRGB444[0][R] = (short) getBitsHigh(blockPart1, 4, 58);
        colorsRGB444[0][G] = (short) getBitsHigh(blockPart1, 4, 54);
        colorsRGB444[0][B] = (short) getBitsHigh(blockPart1, 4, 50);
        colorsRGB444[1][R] = (short) getBitsHigh(blockPart1, 4, 46);
        colorsRGB444[1][G] = (short) getBitsHigh(blockPart1, 4, 42);
        colorsRGB444[1][B] = (short) getBitsHigh(blockPart1, 4, 38);
        distance = (short) getBitsHigh(blockPart1, 3, 34);

        decompressColor(4, 4, 4, colorsRGB444, colors);
        calcPaintColors59T(distance, colors, paintColors);
        writeBlock(blockPart2, img, width, ix, iy, paintColors, blockMask);
    }

    void calcPaintColors59T(short d, short[][] colors, short[][] possibleColors) {
        possibleColors[3][R] = (short) clamp(colors[1][R] - TABLE_59T[d]);
        possibleColors[3][G] = (short) clamp(colors[1][G] - TABLE_59T[d]);
        possibleColors[3][B] = (short) clamp(colors[1][B] - TABLE_59T[d]);
        // C3
        possibleColors[0][R] = colors[0][R];
        possibleColors[0][G] = colors[0][G];
        possibleColors[0][B] = colors[0][B];
        // C2
        possibleColors[1][R] = (short) clamp(colors[1][R] + TABLE_59T[d]);
        possibleColors[1][G] = (short) clamp(colors[1][G] + TABLE_59T[d]);
        possibleColors[1][B] = (short) clamp(colors[1][B] + TABLE_59T[d]);
        // C1
        possibleColors[2][R] = colors[1][R];
        possibleColors[2][G] = colors[1][G];
        possibleColors[2][B] = colors[1][B];
    }

    void decompressBlockThumb58H(long blockPart1, long blockPart2, byte[] img, int width, int ix, int iy) {
        short[][] colorsRGB444 = new short[2][3];
        short[][] colors = new short[2][3];
        short[][] paintColors = new short[4][3];
        short distance;
        short[][] blockMask = new short[4][4];
        long col0, col1;

        // First decode left part of block.
        colorsRGB444[0][R] = (short) getBitsHigh(blockPart1, 4, 57);
        colorsRGB444[0][G] = (short) getBitsHigh(blockPart1, 4, 53);
        colorsRGB444[0][B] = (short) getBitsHigh(blockPart1, 4, 49);

        colorsRGB444[1][R] = (short) getBitsHigh(blockPart1, 4, 45);
        colorsRGB444[1][G] = (short) getBitsHigh(blockPart1, 4, 41);
        colorsRGB444[1][B] = (short) getBitsHigh(blockPart1, 4, 37);

        distance = (short) (getBitsHigh(blockPart1, 2, 33) << 1);

        col0 = getBitsHigh(blockPart1, 12, 57);
        col1 = getBitsHigh(blockPart1, 12, 45);

        if (col0 >= col1) {
            distance |= 1;
        }

        decompressColor(4, 4, 4, colorsRGB444, colors);
        calcPaintColors58H(distance, colors, paintColors);
        writeBlock(blockPart2, img, width, ix, iy, paintColors, blockMask);
    }

    void calcPaintColors58H(short d, short[][] colors, short[][] possibleColors) {
        possibleColors[3][R] = (short) clamp(colors[1][R] - TABLE_58H[d]);
        possibleColors[3][G] = (short) clamp(colors[1][G] - TABLE_58H[d]);
        possibleColors[3][B] = (short) clamp(colors[1][B] - TABLE_58H[d]);
        // C1
        possibleColors[0][R] = (short) clamp(colors[0][R] + TABLE_58H[d]);
        possibleColors[0][G] = (short) clamp(colors[0][G] + TABLE_58H[d]);
        possibleColors[0][B] = (short) clamp(colors[0][B] + TABLE_58H[d]);
        // C2
        possibleColors[1][R] = (short) clamp(colors[0][R] - TABLE_58H[d]);
        possibleColors[1][G] = (short) clamp(colors[0][G] - TABLE_58H[d]);
        possibleColors[1][B] = (short) clamp(colors[0][B] - TABLE_58H[d]);
        // C3
        possibleColors[2][R] = (short) clamp(colors[1][R] + TABLE_58H[d]);
        possibleColors[2][G] = (short) clamp(colors[1][G] + TABLE_58H[d]);
        possibleColors[2][B] = (short) clamp(colors[1][B] + TABLE_58H[d]);
    }

    void decompressBlockPlanar57(long blockPart1, long blockPart2, byte[] img, int width, int ix, int iy) {
        short[] colorO = new short[3],
            colorH = new short[3],
            colorV = new short[3];

        colorO[0] = (short) getBitsHigh(blockPart1, 6, 63);
        colorO[1] = (short) getBitsHigh(blockPart1, 7, 57);
        colorO[2] = (short) getBitsHigh(blockPart1, 6, 50);
        colorH[0] = (short) getBitsHigh(blockPart1, 6, 44);
        colorH[1] = (short) getBitsHigh(blockPart1, 7, 38);
        colorH[2] = (short) getBitsLow(blockPart2, 6, 31);
        colorV[0] = (short) getBitsLow(blockPart2, 6, 25);
        colorV[1] = (short) getBitsLow(blockPart2, 7, 19);
        colorV[2] = (short) getBitsLow(blockPart2, 6, 12);

        fillPlanar57(colorO);
        fillPlanar57(colorH);
        fillPlanar57(colorV);

        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                int idx = 3 * width * (iy + y) + 3 * (ix + x);
                img[idx + R] =
                    (byte) clamp((x * (colorH[0] - colorO[0]) + y * (colorV[0] - colorO[0]) + 4 * colorO[0] + 2 >> 2));
                img[idx + G] =
                    (byte) clamp((x * (colorH[1] - colorO[1]) + y * (colorV[1] - colorO[1]) + 4 * colorO[1] + 2 >> 2));
                img[idx + B] =
                    (byte) clamp((x * (colorH[2] - colorO[2]) + y * (colorV[2] - colorO[2]) + 4 * colorO[2] + 2 >> 2));
            }
        }
    }

    void fillPlanar57(short[] color) {
        color[0] = (short) ((color[0] << 2) | (color[0] >> 4));
        color[1] = (short) ((color[1] << 1) | (color[1] >> 6));
        color[2] = (short) ((color[2] << 2) | (color[2] >> 4));
    }

    void decompressColor(int rBits, int gBits, int bBits, short[][] colorsRGB444, short[][] colors) {
        colors[0][R] = (short) (colorsRGB444[0][R] << 8 - rBits | colorsRGB444[0][R] >> rBits - (8 - rBits));
        colors[0][G] = (short) (colorsRGB444[0][G] << 8 - gBits | colorsRGB444[0][G] >> gBits - (8 - gBits));
        colors[0][B] = (short) (colorsRGB444[0][B] << 8 - bBits | colorsRGB444[0][B] >> bBits - (8 - bBits));
        colors[1][R] = (short) (colorsRGB444[1][R] << 8 - rBits | colorsRGB444[1][R] >> rBits - (8 - rBits));
        colors[1][G] = (short) (colorsRGB444[1][G] << 8 - gBits | colorsRGB444[1][G] >> gBits - (8 - gBits));
        colors[1][B] = (short) (colorsRGB444[1][B] << 8 - bBits | colorsRGB444[1][B] >> bBits - (8 - bBits));
    }

    void decompressBlockDiffFlip(long blockPart1, long blockPart2, byte[] img, int width, int ix, int iy) {
        short[] avgColor = new short[3],
            encColor1 = new short[3],
            encColor2 = new short[3];
        short[] diff = new short[3];
        int table;
        int index, shift;
        boolean diffBit;
        boolean flipBit;

        diffBit = getBitsHigh(blockPart1, 1, 33) != 0;
        flipBit = getBitsHigh(blockPart1, 1, 32) != 0;

        if (!diffBit) {
            // We have diffBit = 0.

            // First decode left part of block.
            avgColor[0] = (short) getBitsHigh(blockPart1, 4, 63);
            avgColor[1] = (short) getBitsHigh(blockPart1, 4, 55);
            avgColor[2] = (short) getBitsHigh(blockPart1, 4, 47);

            // Here, we should really multiply by 17 instead of 16. This can
            // be done by just copying the four lower bits to the upper ones
            // while keeping the lower bits.
            avgColor[0] |= (avgColor[0] << 4);
            avgColor[1] |= (avgColor[1] << 4);
            avgColor[2] |= (avgColor[2] << 4);

            table = (int) (getBitsHigh(blockPart1, 3, 39) << 1);

            long pixelIndicesMSB, pixelIndicesLSB;

            pixelIndicesMSB = getBitsLow(blockPart2, 16, 31);
            pixelIndicesLSB = getBitsLow(blockPart2, 16, 15);

            shift = 0;
            if (!flipBit) {
                // We should not flip
                for (int x = ix; x < ix + 2; x++) {
                    for (int y = iy; y < iy + 4; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                }
            } else {
                // We should flip
                for (int x = ix; x < ix + 4; x++) {
                    for (int y = iy; y < iy + 2; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                    shift += 2;
                }
            }

            // Now decode other part of block.
            avgColor[0] = (short) getBitsHigh(blockPart1, 4, 59);
            avgColor[1] = (short) getBitsHigh(blockPart1, 4, 51);
            avgColor[2] = (short) getBitsHigh(blockPart1, 4, 43);

            // Here, we should really multiply by 17 instead of 16. This can
            // be done by just copying the four lower bits to the upper ones
            // while keeping the lower bits.
            avgColor[0] |= (avgColor[0] << 4);
            avgColor[1] |= (avgColor[1] << 4);
            avgColor[2] |= (avgColor[2] << 4);

            table = (int) (getBitsHigh(blockPart1, 3, 36) << 1);
            pixelIndicesMSB = getBitsLow(blockPart2, 16, 31);
            pixelIndicesLSB = getBitsLow(blockPart2, 16, 15);

            if (!flipBit) {
                // We should not flip
                shift = 8;
                for (int x = ix + 2; x < ix + 4; x++) {
                    for (int y = iy; y < iy + 4; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                }
            } else {
                // We should flip
                shift = 2;
                for (int x = ix; x < ix + 4; x++) {
                    for (int y = iy + 2; y < iy + 4; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                    shift += 2;
                }
            }
        } else {
            // We have diffBit = 1.

            //      63 62 61 60 59 58 57 56 55 54 53 52 51 50 49 48 47 46 45 44 43 42 41 40 39 38 37 36 35 34  33  32
            //      ---------------------------------------------------------------------------------------------------
            //     | base col1    | dcol 2 | base col1    | dcol 2 | base col 1   | dcol 2 | table  | table  |diff|flip|
            //     | R1' (5 bits) | dR2    | G1' (5 bits) | dG2    | B1' (5 bits) | dB2    | cw 1   | cw 2   |bit |bit |
            //      ---------------------------------------------------------------------------------------------------
            //
            //
            //     c) bit layout in bits 31 through 0 (in both cases)
            //
            //      31 30 29 28 27 26 25 24 23 22 21 20 19 18 17 16 15 14 13 12 11 10  9  8  7  6  5  4  3   2   1  0
            //      --------------------------------------------------------------------------------------------------
            //     |       most significant pixel index bits       |         least significant pixel index bits       |
            //     | p| o| n| m| l| k| j| i| h| g| f| e| d| c| b| a| p| o| n| m| l| k| j| i| h| g| f| e| d| c | b | a |
            //      --------------------------------------------------------------------------------------------------

            // First decode left part of block.
            encColor1[0] = (short) getBitsHigh(blockPart1, 5, 63);
            encColor1[1] = (short) getBitsHigh(blockPart1, 5, 55);
            encColor1[2] = (short) getBitsHigh(blockPart1, 5, 47);

            // Expand from 5 to 8 bits
            avgColor[0] = (short) (encColor1[0] << 3 | encColor1[0] >> 2);
            avgColor[1] = (short) (encColor1[1] << 3 | encColor1[1] >> 2);
            avgColor[2] = (short) (encColor1[2] << 3 | encColor1[2] >> 2);

            table = (int) (getBitsHigh(blockPart1, 3, 39) << 1);

            long pixelIndicesMSB, pixelIndicesLSB;

            pixelIndicesMSB = getBitsLow(blockPart2, 16, 31);
            pixelIndicesLSB = getBitsLow(blockPart2, 16, 15);

            shift = 0;
            if (!flipBit) {
                // We should not flip
                for (int x = ix; x < ix + 2; x++) {
                    for (int y = iy; y < iy + 4; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                }
            } else {
                // We should flip
                for (int x = ix; x < ix + 4; x++) {
                    for (int y = iy; y < iy + 2; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                    shift += 2;
                }
            }

            // Now decode right part of block.
            diff[0] = (short) getBitsHigh(blockPart1, 3, 58);
            diff[1] = (short) getBitsHigh(blockPart1, 3, 50);
            diff[2] = (short) getBitsHigh(blockPart1, 3, 42);

            // Extend sign bit to entire byte.
            diff[0] = (short) (diff[0] << 5);
            diff[1] = (short) (diff[1] << 5);
            diff[2] = (short) (diff[2] << 5);
            diff[0] = (short) (diff[0] >> 5);
            diff[1] = (short) (diff[1] >> 5);
            diff[2] = (short) (diff[2] >> 5);

            //  Calculate second color
            encColor2[0] = (short) (encColor1[0] + diff[0]);
            encColor2[1] = (short) (encColor1[1] + diff[1]);
            encColor2[2] = (short) (encColor1[2] + diff[2]);

            // Expand from 5 to 8 bits
            avgColor[0] = (short) (encColor2[0] << 3 | encColor2[0] >> 2);
            avgColor[1] = (short) (encColor2[1] << 3 | encColor2[1] >> 2);
            avgColor[2] = (short) (encColor2[2] << 3 | encColor2[2] >> 2);

            table = (int) (getBitsHigh(blockPart1, 3, 36) << 1);
            pixelIndicesMSB = getBitsLow(blockPart2, 16, 31);
            pixelIndicesLSB = getBitsLow(blockPart2, 16, 15);

            if (!flipBit) {
                // We should not flip
                shift = 8;
                for (int x = ix + 2; x < ix + 4; x++) {
                    for (int y = iy; y < iy + 4; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                }
            } else {
                // We should flip
                shift = 2;
                for (int x = ix; x < ix + 4; x++) {
                    for (int y = iy + 2; y < iy + 4; y++) {
                        index = (int) (((pixelIndicesMSB >> shift) & 1) << 1);
                        index |= ((pixelIndicesLSB >> shift) & 1);
                        shift++;
                        index = UNSCRAMBLE[index];

                        img[3 * (y * width + x) + R] = (byte) clamp(avgColor[0] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + G] = (byte) clamp(avgColor[1] + COMPRESS_PARAMS[table][index]);
                        img[3 * (y * width + x) + B] = (byte) clamp(avgColor[2] + COMPRESS_PARAMS[table][index]);
                    }
                    shift += 2;
                }
            }
        }
    }

    short get16Bits11Signed(int base, int table, int mul, int index) {
        int base11 = base - 128;
        if (base11 == -128) {
            base11 = -127;
        }
        base11 *= 8;
        // I want the positive value here
        int tableVal = -ALPHA_BASE[table][3 - index % 4] - 1;
        // and the sign, please
        boolean sign = 1 - (index / 4) != 0;

        if (sign) {
            tableVal = tableVal + 1;
        }
        int value11 = tableVal * 8;

        if (mul != 0) {
            value11 *= mul;
        } else {
            value11 /= 8;
        }

        if (sign) {
            value11 = -value11;
        }

        // calculate sum
        int bits11 = base11 + value11;

        // clamp..
        if (bits11 >= 1024) {
            bits11 = 1023;
        } else if (bits11 < -1023) {
            bits11 = -1023;
        }
        // this is the value we would actually output...
        // but there aren't any good 11-bit file or uncompressed GL formats,
        // so we extend to 15 bits signed.
        sign = bits11 < 0;
        bits11 = Math.abs(bits11);
        int bits16 = (bits11 << 5) + (bits11 >> 5);

        if (sign) {
            bits16 *= -1;
        }

        return (short) bits16;
    }

    int get16Bits11Bits(int base, int table, int mul, int index) {
        int base11 = base * 8 + 4;

        // I want the positive value here
        int tableVal = -ALPHA_BASE[table][3 - index % 4] - 1;
        // and the sign, please
        boolean sign = 1 - (index / 4) != 0;

        if (sign) {
            tableVal = tableVal + 1;
        }
        int value11 = tableVal * 8;

        if (mul != 0) {
            value11 *= mul;
        } else {
            value11 /= 8;
        }

        if (sign) {
            value11 = -value11;
        }

        // calculate sum
        int bits11 = base11 + value11;

        // clamp..
        if (bits11 >= 256 * 8) {
            bits11 = 256 * 8 - 1;
        } else if (bits11 < 0) {
            bits11 = 0;
        }
        // bits11 now contains the 11 bit alpha value as defined in the spec.

        // extend to 16 bits before returning, since we don't have any good 11-bit file formats.
        int result = (bits11 << 5) + (bits11 >> 6);
        return result & 0xffff;
    }

    void setupAlphaTable() {
        // no synchronization here, extra calculation does no harm
        if (alphaTableSetup) {
            return;
        }
        alphaTableSetup = true;

        // read table used for alpha compression
        int buf;
        for (int i = 16; i < 32; i++) {
            for (int j = 0; j < 8; j++) {
                buf = ALPHA_BASE[i - 16][3 - j % 4];
                if (j < 4)
                    ALPHA_TABLE[i][j] = buf;
                else
                    ALPHA_TABLE[i][j] = (-buf - 1);
            }
        }

        // beyond the first 16 values, the rest of the table is implicit. so calculate that!
        for (int i = 0; i < 256; i++) {
            // fill remaining slots in table with multiples of the first ones.
            int mul = i / 16;
            int old = 16 + i % 16;
            for (int j = 0; j < 8; j++) {
                ALPHA_TABLE[i][j] = ALPHA_TABLE[old][j] * mul;
                // note: we don't do clamping here, though we could, because we'll be clamped afterward anyway.
            }
        }
    }

    int getBit(int input, int from, int to) {
        if (from > to) {
            return (1 << from & input) >> from - to;
        }
        return (1 << from & input) << to - from;
    }

    int shiftHigh(int size, int startPos) {
        return startPos - 32 - size + 1;
    }

    int maskHigh(int size, int startPos) {
        return (1 << size) - 1 << shiftHigh(size, startPos);
    }

    int shiftLow(int size, int startPos) {
        return startPos - size + 1;
    }

    int maskLow(int size, int startPos) {
        return (2 << size - 1) - 1 << shiftLow(size, startPos);
    }

    long putBitsHigh(long dest, long data, int size, int startPos) {
        return dest & ~maskHigh(size, startPos) | data << shiftHigh(size, startPos) & maskHigh(size, startPos);
    }

    long getBitsHigh(long source, int size, int startPos) {
        return source >>> startPos - 32 - size + 1 & (1L << size) - 1;
    }

    long putBitsLow(long dest, long data, int size, int startPos) {
        return dest & ~maskLow(size, startPos) | data << shiftLow(size, startPos) & maskLow(size, startPos);
    }

    long getBitsLow(long source, int size, int startPos) {
        return source >>> startPos - size + 1 & (1L << size) - 1;
    }

    int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }

    record UIntPair(long part1, long part2) {
    }
}
