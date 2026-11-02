package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class LiveSceneTest {
    @Test
    public void clockPhaseWithoutAPairedApp() {
        assertEquals("dawn", LiveScene.phaseAt(LocalTime.of(6, 0)));
        assertEquals("day", LiveScene.phaseAt(LocalTime.of(12, 0)));
        assertEquals("dusk", LiveScene.phaseAt(LocalTime.of(18, 30)));
        assertEquals("night", LiveScene.phaseAt(LocalTime.of(23, 0)));
    }

    @Test
    public void seasons() {
        assertEquals("autumn", LiveScene.season(LocalDate.of(2026, 11, 2)));
        assertEquals("winter", LiveScene.season(LocalDate.of(2026, 12, 25)));
        assertEquals("spring", LiveScene.season(LocalDate.of(2026, 4, 1)));
    }

    @Test
    public void layers() {
        List<Integer> clearDay = LiveScene.layersFor("day", "clear", "autumn");
        assertTrue(clearDay.contains(SceneView.SUNBEAMS));
        assertTrue(clearDay.contains(SceneView.BIRDS));
        assertTrue(clearDay.contains(SceneView.LEAVES));
        List<Integer> night = LiveScene.layersFor("night", "clear", "autumn");
        assertTrue(night.contains(SceneView.FIREFLIES));
        assertFalse(night.contains(SceneView.BIRDS));
        List<Integer> rain = LiveScene.layersFor("dusk", "rain", "summer");
        assertEquals(1, rain.size());
        assertEquals(SceneView.RAIN, (int) rain.get(0));
        assertTrue(LiveScene.layersFor("night", "fog", "autumn").contains(SceneView.FIREFLIES));
    }
}
