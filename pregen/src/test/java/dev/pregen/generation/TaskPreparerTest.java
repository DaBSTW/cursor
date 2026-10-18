package dev.pregen.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import dev.pregen.util.ChunkPos;
import org.junit.jupiter.api.Test;

class TaskPreparerTest {

    @Test
    void countsChunksWhoseCenterIsInsideTheShape() {
        Selection square = new Selection("w", 0, 0, 80, 80, ShapeType.SQUARE, PatternType.REGION, "");
        assertEquals(100, TaskPreparer.prepare(square, new long[0], 0, 0, true).totalChunks());
        Selection circle = square.withShape(ShapeType.CIRCLE).withRadius(1600);
        long total = TaskPreparer.prepare(circle, new long[0], 0, 0, true).totalChunks();
        assertEquals(Math.PI * 100 * 100, total, total * 0.01);
    }

    @Test
    void resumeCountsOnlySelectedChunksBeforeTheIndex() {
        Selection circle = new Selection("w", 0, 0, 160, 160, ShapeType.CIRCLE, PatternType.LOOP, "");
        TaskPlan fresh = TaskPreparer.prepare(circle, new long[0], 0, 0, true);
        TaskPlan resumed = TaskPreparer.prepare(circle, new long[0], fresh.totalChunks() * 2, 0, true);
        assertTrue(resumed.initialProcessed() < fresh.totalChunks() * 2);
        assertTrue(resumed.initialProcessed() > 0);
        assertEquals(fresh.totalChunks(), resumed.totalChunks());
    }

    @Test
    void estimatesHugeSelectionsInsteadOfCounting() {
        Selection huge = new Selection("w", 0, 0, 200_000, 200_000, ShapeType.CIRCLE, PatternType.REGION, "");
        TaskPlan plan = TaskPreparer.prepare(huge, new long[0], 0, 0, true);
        assertFalse(plan.exactTotal());
        assertEquals(Math.PI * 12_500 * 12_500, plan.totalChunks(), plan.totalChunks() * 0.001);
    }

    @Test
    void listPatternsUseTheListSizeAndNeverSkipForWorldPattern() {
        long[] list = {ChunkPos.pack(1, 1), ChunkPos.pack(900, 900)};
        Selection world = Selection.defaults("w").withPattern(PatternType.WORLD, "");
        TaskPlan plan = TaskPreparer.prepare(world, list, 1, 0, true);
        assertEquals(2, plan.totalChunks());
        assertEquals(1, plan.initialProcessed());
        assertFalse(plan.skipExistingChunks());
    }
}
