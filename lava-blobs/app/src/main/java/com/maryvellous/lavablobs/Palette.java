package com.maryvellous.lavablobs;

/** Nine exact user-supplied colours, 1-based size wraps after size 9. */
final class Palette {
    static final int[] COLORS={
        0xFF065143,0xFF4B748E,0xFF6D9DC5,
        0xFF6E73AD,0xFF53599A,0xFF6F4994,
        0xFF8A4C88,0xFFA54F7B,0xFFDB5461
    };
    static int forTier(int tier){return COLORS[Math.floorMod(tier,COLORS.length)];}
}
