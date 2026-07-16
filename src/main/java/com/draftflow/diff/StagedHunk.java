/**
 * @file StagedHunk.java
 * @description Fine-grained line-level hunk grouping and staging engine.
 * Identifies contiguous block differences (inserts/deletes) and handles partial code patch applications.
 * 
 * DESIGN RATIONALE:
 * - Direct whole-file staging forces developers to commit temporary code experiments alongside finalized lines.
 * - This engine computes discrete modification boundaries (hunks) relative to KEEP operations.
 *   This enables a Git-like interactive staging pipeline (`git add -p`) where developers can choose
 *   to stage specific chunks while discarding other lines.
 */

package com.draftflow.diff;

import com.draftflow.merge.LineMerge;
import com.draftflow.merge.LineMerge.Edit;
import com.draftflow.merge.LineMerge.EditType;

import java.util.ArrayList;
import java.util.List;

public class StagedHunk {
    public final int startLineBase;
    public final int endLineBase;
    public final int startLineTarget;
    public final int endLineTarget;
    public final List<Edit> edits;

    public StagedHunk(int startLineBase, int endLineBase, int startLineTarget, int endLineTarget, List<Edit> edits) {
        this.startLineBase = startLineBase;
        this.endLineBase = endLineBase;
        this.startLineTarget = startLineTarget;
        this.endLineTarget = endLineTarget;
        this.edits = edits;
    }

    /**
     * Group contiguous inserts/deletes into discrete hunks.
     */
    public static List<StagedHunk> computeHunks(List<String> base, List<String> target) {
        List<Edit> edits = LineMerge.diff(base, target);
        List<StagedHunk> hunks = new ArrayList<>();

        int baseIdx = 0;
        int targetIdx = 0;

        List<Edit> currentEdits = new ArrayList<>();
        int hunkStartBase = -1;
        int hunkStartTarget = -1;

        for (Edit e : edits) {
            if (e.type == EditType.KEEP) {
                if (!currentEdits.isEmpty()) {
                    hunks.add(new StagedHunk(hunkStartBase, baseIdx, hunkStartTarget, targetIdx, new ArrayList<>(currentEdits)));
                    currentEdits.clear();
                    hunkStartBase = -1;
                    hunkStartTarget = -1;
                }
                baseIdx++;
                targetIdx++;
            } else {
                if (currentEdits.isEmpty()) {
                    hunkStartBase = baseIdx;
                    hunkStartTarget = targetIdx;
                }
                currentEdits.add(e);
                if (e.type == EditType.DELETE) {
                    baseIdx++;
                } else if (e.type == EditType.INSERT) {
                    targetIdx++;
                }
            }
        }

        if (!currentEdits.isEmpty()) {
            hunks.add(new StagedHunk(hunkStartBase, baseIdx, hunkStartTarget, targetIdx, new ArrayList<>(currentEdits)));
        }

        return hunks;
    }

    /**
     * Applies a list of selected hunks from target onto base content to produce a partially-staged list of lines.
     */
    public static List<String> applyHunks(List<String> base, List<String> target, List<StagedHunk> hunksToApply) {
        List<String> result = new ArrayList<>();
        List<Edit> edits = LineMerge.diff(base, target);

        // Map which target line edits are part of the accepted hunks
        int baseIdx = 0;
        int targetIdx = 0;

        int editIdx = 0;
        while (editIdx < edits.size()) {
            Edit e = edits.get(editIdx);
            if (e.type == EditType.KEEP) {
                result.add(e.line);
                baseIdx++;
                targetIdx++;
                editIdx++;
            } else {
                // Find which hunk this edit belongs to
                StagedHunk matchedHunk = null;
                for (StagedHunk h : hunksToApply) {
                    // Check if this baseIdx/targetIdx matches the start of the hunk
                    if (baseIdx >= h.startLineBase && baseIdx <= h.endLineBase &&
                        targetIdx >= h.startLineTarget && targetIdx <= h.endLineTarget) {
                        matchedHunk = h;
                        break;
                    }
                }

                if (matchedHunk != null) {
                    // Apply the whole hunk's edits in order
                    for (Edit he : matchedHunk.edits) {
                        if (he.type == EditType.INSERT) {
                            result.add(he.line);
                            targetIdx++;
                        } else if (he.type == EditType.DELETE) {
                            // Skip adding to result since it is deleted
                            baseIdx++;
                        }
                    }
                    editIdx += matchedHunk.edits.size();
                } else {
                    // Hunk not selected, discard the modification (i.e., keep base state)
                    if (e.type == EditType.DELETE) {
                        result.add(e.line); // Keep base version
                        baseIdx++;
                    } else if (e.type == EditType.INSERT) {
                        // Skip target insertion
                        targetIdx++;
                    }
                    editIdx++;
                }
            }
        }

        return result;
    }
}
