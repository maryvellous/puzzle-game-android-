package com.maryvellous.lavablobs;
import org.junit.Test;
import static org.junit.Assert.*;
public class GameConsoleLayoutTest {
    @Test public void allWorldCoordinatesRoundTripAndStayInsideScreen(){
        for(float h:new float[]{780,850,880,930,1100,1400})for(int shape=0;shape<=25;shape++){
            Vessel v=new Vessel(shape,h);float w=0;
            for(float x:v.xs)w=Math.max(w,2*Math.abs(x-210));
            float s=GameConsoleLayout.vesselScale(h,w,v.bottom-v.top);
            assertTrue(GameConsoleLayout.toScreenY(v.top,v.bottom,s,h)>=97.99);
            assertTrue(GameConsoleLayout.visualBottom(h)<GameConsoleLayout.panelTop(h));
            for(int i=0;i<v.xs.length;i++){
                float x=GameConsoleLayout.toScreenX(v.xs[i],s),y=GameConsoleLayout.toScreenY(v.ys[i],v.bottom,s,h);
                assertTrue(x>=20.99&&x<=399.01);
                assertEquals(v.xs[i],GameConsoleLayout.toWorldX(x,s),.001f);
                assertEquals(v.ys[i],GameConsoleLayout.toWorldY(y,v.bottom,s,h),.001f);
            }
            assertTrue(GameConsoleLayout.panelY(h,184)<h-18);
        }
    }
    @Test public void launcherHasBreathingRoomOnAllScreens(){
        for(float h:new float[]{780,850,880,930,1100,1400}){
            assertEquals(22f,GameConsoleLayout.entryY(h)-24f-GameConsoleLayout.visualBottom(h),.001f);
            assertEquals(18f,GameConsoleLayout.panelTop(h)-GameConsoleLayout.entryY(h)-24f,.001f);
        }
    }
    @Test public void tallConsoleUsesMoreSpaceAndKeepsOrder(){
        assertTrue(GameConsoleLayout.panelHeight(900)>GameConsoleLayout.panelHeight(780));
        assertTrue(GameConsoleLayout.panelY(900,103)<GameConsoleLayout.panelY(900,150));
        assertTrue(GameConsoleLayout.panelY(900,150)<GameConsoleLayout.panelY(900,184));
    }
}
