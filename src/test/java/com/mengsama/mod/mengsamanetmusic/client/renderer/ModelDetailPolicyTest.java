package com.mengsama.mod.mengsamanetmusic.client.renderer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.mengsama.mod.mengsamanetmusic.client.renderer.ModelDetailPolicy.Detail.*;

class ModelDetailPolicyTest {
    @Test void initialSelectionUsesCameraDistance() {
        assertEquals(FULL, ModelDetailPolicy.select(12*12,null));
        assertEquals(MEDIUM, ModelDetailPolicy.select(13*13,null));
        assertEquals(MEDIUM, ModelDetailPolicy.select(24*24,null));
        assertEquals(FAR, ModelDetailPolicy.select(25*25,null));
    }
    @Test void movingAroundBoundaryDoesNotKeepSwitchingGeometry() {
        var detail=FULL;
        for (double distance : new double[]{12.1,11.9,12.05,11,10.1}) {
            detail=ModelDetailPolicy.select(distance*distance,detail);
            assertEquals(MEDIUM, detail);
        }
        assertEquals(FULL,ModelDetailPolicy.select(100,detail));
        detail=MEDIUM;
        for (double distance : new double[]{24.1,23.9,24.01,23,22.1}) {
            detail=ModelDetailPolicy.select(distance*distance,detail);
            assertEquals(FAR,detail);
        }
        assertEquals(MEDIUM,ModelDetailPolicy.select(484,detail));
    }
    @Test void teleportsCanCrossBothLevelsImmediately() {
        assertEquals(FAR,ModelDetailPolicy.select(40*40,FULL));
        assertEquals(FULL,ModelDetailPolicy.select(3*3,FAR));
    }
    @Test void invalidDistanceKeepsVisibleFullModel() {
        for (double d:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1})
            assertEquals(FULL,ModelDetailPolicy.select(d,FAR));
    }
}
