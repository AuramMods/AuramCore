package art.arcane.auram.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class RockAppearanceTest {
    @Test
    public void recognizesInstalledOreNamingConventions() {
        assertEquals(RockHost.NETHERRACK, RockHost.detect("nether_quartz_ore", ""));
        assertEquals(RockHost.NETHERRACK, RockHost.detect("netherrack_osmium_ore", ""));
        assertEquals(RockHost.NETHERRACK, RockHost.detect("ruby_nether_ore", ""));
        assertEquals(RockHost.END_STONE, RockHost.detect("end_draconium_ore", ""));
        assertEquals(RockHost.END_STONE, RockHost.detect("end_stone_lead_ore", ""));
        assertEquals(RockHost.END_STONE, RockHost.detect("alexandrite_end_ore", ""));
        assertEquals(RockHost.DEEPSLATE, RockHost.detect("silver_ore_deepslate", ""));
        assertEquals(RockHost.DEEPSLATE, RockHost.detect("deepslate_quartz_ore", ""));
    }

    @Test
    public void usesTextureHintsWithoutConfusingMaterialNamesWithDimensions() {
        assertEquals(RockHost.END_STONE, RockHost.detect("draconium_ore", "block/ores/endstone_draconium"));
        assertEquals(RockHost.NETHERRACK, RockHost.detect("ruby_ore", "block/nether/ruby_ore"));
        assertEquals(RockHost.DEEPSLATE, RockHost.detect("deepslate_quartz_ore", "block/nether_quartz_ore"));
        assertEquals(RockHost.STONE, RockHost.detect("enderium_ore", "block/enderium_ore"));
        assertEquals(RockHost.STONE, RockHost.detect("netherite_ore", "block/netherite_ore"));
        assertEquals(RockHost.STONE, RockHost.detect("dense_iron_ore", "block/dense_iron_ore"));
    }

    @Test
    public void quartzKeepsItsWhiteInteriorInsteadOfTheRedHostColor() {
        int netherrack = abgr(100, 35, 30);
        int quartz = abgr(238, 229, 212);
        int color = RockPixels.oreColor(16, 16, (x, y) -> x == 8 && y == 8 ? quartz : netherrack,
                16, 16, (x, y) -> netherrack);
        assertEquals(0xEEE5D4, color);
    }

    @Test
    public void findsDarkOreAndHandlesDifferentTextureResolutions() {
        int endStone = abgr(220, 225, 165), ore = abgr(25, 20, 50);
        assertEquals(0x191432, RockPixels.oreColor(32, 32,
                (x, y) -> x >= 14 && x < 18 && y >= 14 && y < 18 ? ore : endStone,
                16, 16, (x, y) -> endStone));
        assertEquals(-1, RockPixels.oreColor(16, 16, (x, y) -> endStone,
                16, 16, (x, y) -> endStone));
        assertEquals(-1, RockPixels.oreColor(16, 16, (x, y) -> 0x00FF0000,
                16, 16, (x, y) -> endStone));
    }

    @Test
    public void generatedHostKeepsTheSilhouetteAlphaAndChangesTheStoneGrain() {
        int mask = 0x7F646464;
        int red = RockPixels.hostPixel(mask, abgr(100, 35, 30));
        int pale = RockPixels.hostPixel(mask, abgr(220, 225, 165));
        int dark = RockPixels.hostPixel(mask, abgr(60, 60, 65));
        assertEquals(0x7F, red >>> 24);
        assertEquals(0, RockPixels.hostPixel(0, abgr(220, 225, 165)) >>> 24);
        assertTrue((red & 255) > (red >>> 8 & 255) * 2);
        assertTrue((pale & 255) > (dark & 255) * 3);
        assertTrue((pale >>> 8 & 255) > (pale >>> 16 & 255));
        assertNotEquals(red, RockPixels.hostPixel(mask, abgr(120, 45, 35)));
    }

    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | b << 16 | g << 8 | r;
    }
}
