package com.draftflow.merge;

import java.util.*;

public class LineMerge {

    public enum EditType {
        KEEP,
        INSERT,
        DELETE
    }

    public static class Edit {
        public final EditType type;
        public final int baseIndex; // Index in base file
        public final String line;

        public Edit(EditType type, int baseIndex, String line) {
            this.type = type;
            this.baseIndex = baseIndex;
            this.line = line;
        }
    }

    public static class MergeResult {
        public final boolean clean;
        public final List<String> mergedLines;

        public MergeResult(boolean clean, List<String> mergedLines) {
            this.clean = clean;
            this.mergedLines = mergedLines;
        }
    }

    private static class BaseLineState {
        final String baseLine;
        final List<String> oursInsert = new ArrayList<>();
        boolean oursDeleted = false;
        final List<String> theirsInsert = new ArrayList<>();
        boolean theirsDeleted = false;

        BaseLineState(String baseLine) {
            this.baseLine = baseLine;
        }
    }

    /**
     * Performs a 3-way line merge.
     */
    public static MergeResult merge(List<String> base, List<String> ours, List<String> theirs) {
        if (ours.equals(theirs)) {
            return new MergeResult(true, ours);
        }
        if (ours.equals(base)) {
            return new MergeResult(true, theirs);
        }
        if (theirs.equals(base)) {
            return new MergeResult(true, ours);
        }

        List<Edit> oursEdits = diff(base, ours);
        List<Edit> theirsEdits = diff(base, theirs);

        BaseLineState[] states = new BaseLineState[base.size() + 1];
        for (int k = 0; k <= base.size(); k++) {
            states[k] = new BaseLineState(k < base.size() ? base.get(k) : null);
        }

        for (Edit e : oursEdits) {
            if (e.type == EditType.INSERT) {
                states[e.baseIndex].oursInsert.add(e.line);
            } else if (e.type == EditType.DELETE) {
                states[e.baseIndex].oursDeleted = true;
            }
        }

        for (Edit e : theirsEdits) {
            if (e.type == EditType.INSERT) {
                states[e.baseIndex].theirsInsert.add(e.line);
            } else if (e.type == EditType.DELETE) {
                states[e.baseIndex].theirsDeleted = true;
            }
        }

        List<String> merged = new ArrayList<>();
        boolean clean = true;

        for (int k = 0; k <= base.size(); k++) {
            BaseLineState s = states[k];

            // 1. Resolve insertions before line k
            if (!s.oursInsert.isEmpty() || !s.theirsInsert.isEmpty()) {
                if (s.oursInsert.equals(s.theirsInsert)) {
                    merged.addAll(s.oursInsert);
                } else if (s.oursInsert.isEmpty()) {
                    // Deleted on ours, modified/inserted on theirs (Delete vs Modify conflict)
                    if (k < base.size() && states[k].oursDeleted) {
                        clean = false;
                        merged.add("<<<<<<< OURS (Delete)");
                        merged.add("======= (Modify)");
                        merged.addAll(s.theirsInsert);
                        merged.add(">>>>>>> THEIRS");
                    } else {
                        merged.addAll(s.theirsInsert);
                    }
                } else if (s.theirsInsert.isEmpty()) {
                    // Deleted on theirs, modified/inserted on ours (Delete vs Modify conflict)
                    if (k < base.size() && states[k].theirsDeleted) {
                        clean = false;
                        merged.add("<<<<<<< OURS (Modify)");
                        merged.addAll(s.oursInsert);
                        merged.add("======= (Delete)");
                        merged.add(">>>>>>> THEIRS");
                    } else {
                        merged.addAll(s.oursInsert);
                    }
                } else {
                    // Both inserted different content
                    clean = false;
                    merged.add("<<<<<<< OURS");
                    merged.addAll(s.oursInsert);
                    merged.add("=======");
                    merged.addAll(s.theirsInsert);
                    merged.add(">>>>>>> THEIRS");
                }
            }

            // 2. Resolve the line itself (for k < base.size())
            if (k < base.size()) {
                if (!s.oursDeleted && !s.theirsDeleted) {
                    merged.add(s.baseLine);
                } else if (s.oursDeleted && s.theirsDeleted) {
                    // Both deleted. If they had differing inserts, it was handled in step 1.
                } else if (s.oursDeleted) {
                    // Ours deleted, theirs kept. If theirs had inserts, it's a conflict (step 1).
                    // If theirs did not insert, it's a clean deletion.
                    if (!s.theirsInsert.isEmpty()) {
                        clean = false; // Conflicting delete vs modify/insert
                    }
                } else {
                    // Theirs deleted, ours kept.
                    if (!s.oursInsert.isEmpty()) {
                        clean = false; // Conflicting modify/insert vs delete
                        merged.add(s.baseLine);
                    }
                }
            }
        }

        return new MergeResult(clean, merged);
    }

    /**
     * Helper to find edits from base to target using dynamic programming LCS.
     */
    public static List<Edit> diff(List<String> base, List<String> target) {
        int m = base.size();
        int n = target.size();
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (base.get(i - 1).equals(target.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        List<Edit> edits = new ArrayList<>();
        int i = m, j = n;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && base.get(i - 1).equals(target.get(j - 1))) {
                edits.add(new Edit(EditType.KEEP, i - 1, base.get(i - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                edits.add(new Edit(EditType.INSERT, i, target.get(j - 1)));
                j--;
            } else {
                edits.add(new Edit(EditType.DELETE, i - 1, base.get(i - 1)));
                i--;
            }
        }

        Collections.reverse(edits);
        return edits;
    }
}
