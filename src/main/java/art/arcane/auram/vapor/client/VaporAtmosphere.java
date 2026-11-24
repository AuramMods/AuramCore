package art.arcane.auram.vapor.client;

import com.mojang.logging.LogUtils;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;

/** Evaluates Bliss's ambient fog in world space for the Vapor simulation. */
public final class VaporAtmosphere {
    private static final String[] TIMES = {"Morning", "Noon", "Evening", "Night"};
    private static volatile VaporAtmosphere current;

    private final float fogStart;
    private final float rainFog;
    private final float netherHaze;
    private final float netherCeiling;
    private final float endHaze;
    private final float midnightDensity;
    private final float[] uniform = new float[TIMES.length];
    private final float[] cloudy = new float[TIMES.length];
    private final float[] dailyUniform = new float[10];
    private final float[] dailyCloudy = new float[10];
    private final boolean randomWeather;
    private final FogNoise noise;

    private VaporAtmosphere(Function<String, String> values, Predicate<String> enabled, FogNoise noise) {
        fogStart = option(values, "FOG_START_HEIGHT", 60.0f) + 3.0f;
        rainFog = option(values, "RainFog_amount", 0.1f);
        netherHaze = option(values, "NETHER_HAZE_DENSITY", 1.0f);
        netherCeiling = option(values, "NETHER_CEILING_SMOKE_DENSITY", 1.0f);
        endHaze = option(values, "END_HAZE_DENSTIY", 1.0f);
        midnightDensity = option(values, "MIDNIGHT_FOG_DENSITY", 2.0f);
        float fogMultiplier = option(values, "TOD_Fog_mult", 1.0f);
        float[] uniformDefaults = {0.01f, 0.0f, 0.05f, 0.2f};
        float[] cloudyDefaults = {0.05f, 0.0f, 0.0f, 0.05f};
        for (int i = 0; i < TIMES.length; i++) {
            uniform[i] = fogMultiplier * option(values, TIMES[i] + "_Uniform_Fog", uniformDefaults[i]);
            cloudy[i] = fogMultiplier * option(values, TIMES[i] + "_Cloudy_Fog", cloudyDefaults[i]);
        }
        if (enabled.test("Daily_Weather")) {
            for (int i = 0; i < dailyUniform.length; i++) {
                dailyUniform[i] = option(values, "DAY" + i + "_ufog_density", i == 1 ? 0.01f : 0.0f);
                dailyCloudy[i] = option(values, "DAY" + i + "_cfog_density",
                        i == 1 ? 0.1f : i == 9 ? 1.0f : 0.0f);
            }
        }
        randomWeather = enabled.test("CHOOSE_RANDOM_WEATHER_PROFILE");
        this.noise = noise;
    }

    public static void updateBliss(Function<String, String> values, Predicate<String> enabled, byte[] noisePng) {
        current = new VaporAtmosphere(values, enabled, FogNoise.decode(noisePng));
    }

    public static void clearBliss() {
        current = null;
    }

    static VaporAtmosphere current() {
        return current;
    }

    Sample sample(String dimension, long dayTime, float rainStrength) {
        // The ambient Nether and End haze is independent of time and precipitation.
        if (dimension.equals("minecraft:the_nether")) {
            return new Sample(0.0f, 0.0f, 0.0f, 0.4f * netherHaze, 0.4f * netherCeiling);
        }
        if (dimension.equals("minecraft:the_end")) {
            return new Sample(0.0f, 0.0f, 0.0f, 0.4f * endHaze, 0.0f);
        }
        float dimensionScale = switch (dimension) {
            case "aether:the_aether" -> 0.25f;
            case "midnight:the_midnight" -> midnightDensity;
            default -> 1.0f;
        };
        int time = Math.floorMod(dayTime, 24000);
        long day = Math.floorDiv(dayTime, 24000);
        int profile = randomWeather ? dailyProfile(day) : Math.floorMod(day, dailyUniform.length);
        // Same time windows and daily density floor as Bliss's FogDensities.
        float[] weights = {
                clamp((time - 22000.0f) / 2000.0f) + clamp((2000.0f - time) / 2000.0f),
                clamp(time / 2000.0f) * clamp((12000.0f - time) / 2000.0f),
                clamp((time - 10000.0f) / 2000.0f) * clamp((14000.0f - time) / 2000.0f),
                clamp((time - 13000.0f) / 2000.0f) * clamp((23000.0f - time) / 2000.0f)
        };
        float uniformFog = 0.0f;
        float cloudyFog = 0.0f;
        for (int i = 0; i < weights.length; i++) {
            uniformFog += weights[i] * Math.max(uniform[i], dailyUniform[profile]);
            cloudyFog += weights[i] * Math.max(cloudy[i], dailyCloudy[profile]);
        }
        // Full rain with Auram's RainFog_amount=0.07 gives 0.5, including under cover.
        float rain = 0.5f * rainStrength * rainFog / 0.07f;
        return new Sample(4.0f * dimensionScale * uniformFog, 4.0f * dimensionScale * cloudyFog,
                dimensionScale * rain, 0.0f, 0.0f);
    }

    float lingering(Sample sample, int x, int y, int z, boolean precipitation) {
        float uniformFog = sample.uniform * (float) Math.pow(2.0, -0.15 * Math.max(y - fogStart, 0.0f));
        float cloudyFog = 0.0f;
        if (sample.cloudy > 0.0f && noise != null) {
            // Match cloudVol's large-scale shape and erosion before its lighting or view-distance terms.
            float shape = clamp((0.4f - noise.fog(x * 24.0f, y, z * 24.0f)) * 2.0f);
            float shift = shape * 200.0f;
            float erosion = 1.0f - noise.fog(x * 200.0f - shift, y * (200.0f / 48.0f) - shift, z * 200.0f - shift);
            float heightFade = (float) Math.exp(-0.05 * Math.max(y - fogStart - 20.0f, 0.0f));
            cloudyFog = sample.cloudy * heightFade * Math.max(shape - erosion * 0.4f, 0.0f);
        }
        float ceiling = clamp((y - 40.0f) / 50.0f);
        return clamp(uniformFog + cloudyFog + (precipitation ? sample.rain : 0.0f)
                + sample.haze + sample.ceiling * ceiling * ceiling * ceiling);
    }

    record Sample(float uniform, float cloudy, float rain, float haze, float ceiling) {
        boolean clear() {
            return uniform == 0.0f && cloudy == 0.0f && rain == 0.0f && haze == 0.0f && ceiling == 0.0f;
        }
    }

    private static int dailyProfile(long day) {
        float p = fract(Math.floorMod(day, 1000) * 0.1031f);
        p *= p + 33.33f;
        p *= p + p;
        return (int) (fract(p) * 10.0f);
    }

    private static float option(Function<String, String> values, String name, float fallback) {
        String value = values.apply(name);
        if (value == null) return fallback;
        try {
            float number = Float.parseFloat(value);
            return Float.isFinite(number) ? number : fallback;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(value, 1.0f));
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float smooth(float fraction) {
        return fraction * fraction * (3.0f - 2.0f * fraction);
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private record FogNoise(int width, int height, int[] pixels) {
        static FogNoise decode(byte[] png) {
            if (png == null) return null;
            try {
                var image = ImageIO.read(new ByteArrayInputStream(png));
                if (image == null) return null;
                int width = image.getWidth();
                int height = image.getHeight();
                return new FogNoise(width, height, image.getRGB(0, 0, width, height, null, 0, width));
            } catch (IOException failure) {
                LogUtils.getLogger().warn("Could not read Bliss's fog noise for Vapor", failure);
                return null;
            }
        }

        float fog(float x, float y, float z) {
            x /= 36.0f;
            y /= 18.0f;
            z /= 36.0f;
            float u = (float) Math.floor(x) + smooth(fract(x));
            float v = (float) Math.floor(z) + smooth(fract(z)) + (float) Math.floor(y) * 193.0f;
            return mix(channel(u, v, 8), channel(u, v, 16), smooth(fract(y)));
        }

        private float channel(float u, float v, int shift) {
            // The pack's noise texture uses linear filtering and repeat wrapping.
            u = u * width / 512.0f - 0.5f;
            v = v * height / 512.0f - 0.5f;
            int x = (int) Math.floor(u);
            int y = (int) Math.floor(v);
            float lower = mix(texel(x, y, shift), texel(x + 1, y, shift), fract(u));
            float upper = mix(texel(x, y + 1, shift), texel(x + 1, y + 1, shift), fract(u));
            return mix(lower, upper, fract(v));
        }

        private float texel(int x, int y, int shift) {
            int pixel = pixels[Math.floorMod(y, height) * width + Math.floorMod(x, width)];
            return ((pixel >>> shift) & 255) / 255.0f;
        }
    }
}
