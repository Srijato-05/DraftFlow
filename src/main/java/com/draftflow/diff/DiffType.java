/**
 * @file DiffType.java
 * @description State categories for file differences computed during tree diff runs.
 * 
 * DESIGN RATIONALE:
 * - Direct representation of three core modification operations (addition, deletion, edit)
 *   to align with standard Git-like state tracking in the UI.
 */

package com.draftflow.diff;

public enum DiffType {
    ADDED,
    MODIFIED,
    DELETED
}
