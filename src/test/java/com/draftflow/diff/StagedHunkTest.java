package com.draftflow.diff;

import com.draftflow.merge.LineMerge.EditType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StagedHunkTest {

    @Test
    public void testComputeAndApplyHunks() {
        // 1. Compute hunks with no changes
        List<String> base = Arrays.asList("line 1", "line 2", "line 3");
        List<String> target = Arrays.asList("line 1", "line 2", "line 3");
        List<StagedHunk> hunks = StagedHunk.computeHunks(base, target);
        assertTrue(hunks.isEmpty());

        // 2. Compute hunks with multiple edits
        base = Arrays.asList("line 1", "line 2", "line 3", "line 4");
        target = Arrays.asList("line 1", "line 2 modified", "line 3", "line 4", "line 5");
        hunks = StagedHunk.computeHunks(base, target);
        assertEquals(2, hunks.size());

        StagedHunk hunk1 = hunks.get(0);
        assertEquals(1, hunk1.startLineBase);
        assertEquals(2, hunk1.endLineBase);
        assertEquals(1, hunk1.startLineTarget);
        assertEquals(2, hunk1.endLineTarget);
        assertEquals(2, hunk1.edits.size());
        assertEquals(EditType.DELETE, hunk1.edits.get(0).type);
        assertEquals(EditType.INSERT, hunk1.edits.get(1).type);

        StagedHunk hunk2 = hunks.get(1);
        assertEquals(4, hunk2.startLineBase);
        assertEquals(4, hunk2.endLineBase);
        assertEquals(4, hunk2.startLineTarget);
        assertEquals(5, hunk2.endLineTarget);
        assertEquals(1, hunk2.edits.size());
        assertEquals(EditType.INSERT, hunk2.edits.get(0).type);

        // 3. Apply hunks
        List<String> result = StagedHunk.applyHunks(base, target, Collections.singletonList(hunk1));
        List<String> expectedResult = Arrays.asList("line 1", "line 2 modified", "line 3", "line 4");
        assertEquals(expectedResult, result);

        result = StagedHunk.applyHunks(base, target, Collections.singletonList(hunk2));
        expectedResult = Arrays.asList("line 1", "line 2", "line 3", "line 4", "line 5");
        assertEquals(expectedResult, result);

        result = StagedHunk.applyHunks(base, target, hunks);
        assertEquals(target, result);

        result = StagedHunk.applyHunks(base, target, Collections.emptyList());
        assertEquals(base, result);
    }
    
    @Test
    public void testComputeHunksWithConsecutiveDeletesAndInserts() {
        List<String> base = Arrays.asList("A", "B", "C", "D");
        List<String> target = Arrays.asList("A", "X", "Y", "D");
        List<StagedHunk> hunks = StagedHunk.computeHunks(base, target);
        assertEquals(1, hunks.size());
        StagedHunk hunk = hunks.get(0);
        assertEquals(1, hunk.startLineBase);
        assertEquals(3, hunk.endLineBase);
        assertEquals(1, hunk.startLineTarget);
        assertEquals(3, hunk.endLineTarget);
        assertEquals(4, hunk.edits.size());
    }
}
