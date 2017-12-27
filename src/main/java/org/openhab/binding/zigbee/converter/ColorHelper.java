/**
 * Copyright (c) 2014-2017 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.zigbee.converter;

import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.PercentType;

/**
 * The methods provided by this class should be merged into HSBType directly
 * They are here only while these are merged into ESH HSBType class
 *
 * @author Pedro Garcia - CIE XY color conversions
 *
 */

public class ColorHelper {
    // 1931 CIE XYZ to sRGB (D65 reference white)
    private static float Xy2Rgb[][] = { { 3.2406f, -1.5372f, -0.4986f }, { -0.9689f, 1.8758f, 0.0415f },
            { 0.0557f, -0.2040f, 1.0570f } };

    // sRGB to 1931 CIE XYZ (D65 reference white)
    private static float Rgb2Xy[][] = { { 0.4124f, 0.3576f, 0.1805f }, { 0.2126f, 0.7152f, 0.0722f },
            { 0.0193f, 0.1192f, 0.9505f } };

    // Gamma compression (sRGB) for a single component, in the 0.0 - 1.0 range
    private static float gammaCompress(float c) {
        if (c < 0.0f) {
            c = 0.0f;
        } else if (c > 1.0f) {
            c = 1.0f;
        }

        return c <= 0.0031308f ? 19.92f * c : (1.0f + 0.055f) * (float) Math.pow(c, 1.0f / 2.4f) - 0.055f;
    }

    // Gamma decompression (sRGB) for a single component, in the 0.0 - 1.0 range
    private static float gammaDecompress(float c) {
        if (c < 0.0f) {
            c = 0.0f;
        } else if (c > 1.0f) {
            c = 1.0f;
        }

        return c <= 0.04045f ? c / 19.92f : (float) Math.pow((c + 0.055f) / (1.0f + 0.055f), 2.4f);
    }

    /**
     * Returns a HSBType object representing the provided xyY color values in CIE XY color model.
     * Conversion from CIE XY color model to sRGB using D65 reference white
     *
     * @param x, y color information 0.0 - 1.0
     * @param Y relative luminance 0.0 - 1.0
     *
     * @return new HSBType object representing the given CIE XY color
     */
    public static HSBType fromXY(float x, float y, float Y) {
        // This makes sure we keep color information even if relative luminance is zero
        float Yo = 1.0f;

        float X = (Yo / y) * x;
        float Z = (Yo / y) * (1.0f - x - y);

        float r = X * Xy2Rgb[0][0] + Yo * Xy2Rgb[0][1] + Z * Xy2Rgb[0][2];
        float g = X * Xy2Rgb[1][0] + Yo * Xy2Rgb[1][1] + Z * Xy2Rgb[1][2];
        float b = X * Xy2Rgb[2][0] + Yo * Xy2Rgb[2][1] + Z * Xy2Rgb[2][2];

        r = gammaCompress(r * Y);
        g = gammaCompress(g * Y);
        b = gammaCompress(b * Y);

        return HSBType.fromRGB((int) (r * 255.0f + 0.5f), (int) (g * 255.0f + 0.5f), (int) (b * 255.0f + 0.5f));
    }

    /**
     * Returns the xyY values representing this object's color in CIE XY color model.
     * Conversion from sRGB to CIE XY using D65 reference white
     * xy pair contains color information
     * Y represents relative luminance
     *
     * @param HSBType color object
     * @return PercentType[x, y, Y] values in the CIE XY color model
     */
    public static PercentType[] toXY(HSBType HSB) {
        // This makes sure we keep color information even if brightness is zero
        PercentType sRGB[] = new HSBType(HSB.getHue(), HSB.getSaturation(), PercentType.HUNDRED).toRGB();

        float r = gammaDecompress(sRGB[0].floatValue() / 100.0f);
        float g = gammaDecompress(sRGB[1].floatValue() / 100.0f);
        float b = gammaDecompress(sRGB[2].floatValue() / 100.0f);

        float X = r * Rgb2Xy[0][0] + g * Rgb2Xy[0][1] + b * Rgb2Xy[0][2];
        float Y = r * Rgb2Xy[1][0] + g * Rgb2Xy[1][1] + b * Rgb2Xy[1][2];
        float Z = r * Rgb2Xy[2][0] + g * Rgb2Xy[2][1] + b * Rgb2Xy[2][2];

        float x = X / (X + Y + Z);
        float y = Y / (X + Y + Z);

        return new PercentType[] { new PercentType(Float.valueOf(x * 100.0f).toString()),
                new PercentType(Float.valueOf(y * 100.0f).toString()),
                new PercentType(Float.valueOf(Y * HSB.getBrightness().floatValue()).toString()) };
    }
}
