package com.maryvellous.lavablobs;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PaletteTest {
    @Test public void themesContainExactNineOpaqueColors(){
        assertEquals(3,Palette.COUNT);
        int[][] expected={
            {0xFFDAF301,0xFFE9AE08,0xFFEB8403,0xFFF0420D,0xFFD23876,0xFFB435A8,0xFF9937C8,0xFF5A29DA,0xFF2D2383},
            {0xFFA72F38,0xFFE2575A,0xFFEF8F55,0xFFF8DEBB,0xFF49A1A5,0xFF1D3058,0xFF9985C2,0xFF773661,0xFF5B5398},
            {0xFFFDA24B,0xFFFB6204,0xFFCB3C06,0xFF980203,0xFF510207,0xFF5F1564,0xFF5B5398,0xFFA20096,0xFF0C0305}
        };
        for(int i=0;i<Palette.COUNT;i++){
            assertArrayEquals(expected[i],Palette.at(i).blobs);
            for(int color:Palette.at(i).blobs)assertEquals(255,(color>>>24)&255);
        }
    }

    @Test public void thirdThemeKeepsBlackAndRemovesDuplicateViolet(){
        Palette.Theme third=Palette.at(2);
        assertEquals(0xFF0C0305,third.forTier(8));
        for(int color:third.blobs)assertNotEquals(0xFF380842,color);
        assertEquals(0xFFFDA24B,third.forTier(9));
        assertEquals(0xFFFB6204,third.forTier(10));
    }

    @Test public void themeChangesCosmeticsButFixedBackgroundAndOutlineRemain(){
        for(int i=0;i<Palette.COUNT;i++){
            Palette.Theme t=Palette.at(i);
            assertEquals(Palette.BACKGROUND_TOP,0xFF322E32);
            assertEquals(Palette.BACKGROUND_BOTTOM,0xFF3D3D3D);
            assertEquals(Palette.VESSEL_OUTLINE,0xFFE6DFE3);
            assertNotEquals(t.forTier(0),t.forTier(1));
        }
        assertNotEquals(Palette.at(0).primary,Palette.at(1).primary);
        // The neutral surface and the vessel never inherit a theme colour.
        for(int i=0;i<Palette.COUNT;i++) {
            Palette.Theme t=Palette.at(i);
            assertEquals(0xFFE6DFE3,t.surface);
            assertEquals(0xFFE6DFE3,t.surfaceRaised);
            assertEquals(0xFFE6DFE3,t.speck);
            assertEquals(0x2BE6DFE3,t.vesselFill);
            assertEquals(43,(t.vesselFill>>>24)&255);
            assertEquals(0xFF211A26,t.onSurface);
        }
    }
}
