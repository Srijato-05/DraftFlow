import { useState, useEffect } from 'react'

function ConflictResolver({
  conflict,
  onAcceptCurrent,
  onAcceptIncoming,
  onResolve,
  onCustomResolve,
}) {
  const currentStr = conflict.currentContent ? conflict.currentContent.join('') : ''
  const incomingStr = conflict.incomingContent ? conflict.incomingContent.join('') : ''

  // Split lines for side-by-side view
  const currentLines = currentStr.split('\n')
  const incomingLines = incomingStr.split('\n')

  // Custom editor state
  const [isCustomMode, setIsCustomMode] = useState(false)
  const [mergedText, setMergedText] = useState('')

  // Initialize custom editor with conflict markers
  const initConflictMarkers = () => {
    const markers = `<<<<<<< CURRENT (OURS)\n${currentStr}\n=======\n${incomingStr}\n>>>>>>> INCOMING (THEIRS)`
    setMergedText(markers)
  }

  useEffect(() => {
    initConflictMarkers()
  }, [currentStr, incomingStr])

  const handleCustomSave = () => {
    if (onCustomResolve) {
      onCustomResolve(mergedText)
    }
  }

  // Character-level diff utility using LCS
  const computeCharDiff = (str1, str2) => {
    const s1 = str1 || '';
    const s2 = str2 || '';
    if (s1 === s2) {
      return {
        diff1: [{ text: s1, type: 'common' }],
        diff2: [{ text: s2, type: 'common' }]
      };
    }

    // Hybrid optimization: if strings are long, tokenise by words/spaces instead of individual characters
    const useWordDiff = s1.length > 300 || s2.length > 300;
    const tokenize = (str) => {
      if (useWordDiff) {
        // Split by word boundary or whitespace while preserving separators
        return str.split(/(\s+|[.,;:!?"'()\[\]{}])/).filter(Boolean);
      }
      return [...str];
    };

    const tokens1 = tokenize(s1);
    const tokens2 = tokenize(s2);
    const m = tokens1.length;
    const n = tokens2.length;

    // Guard against huge files locking up the thread (fallback to block highlight)
    if (m > 800 || n > 800) {
      return {
        diff1: [{ text: s1, type: 'removed' }],
        diff2: [{ text: s2, type: 'added' }]
      };
    }

    const dp = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        if (tokens1[i - 1] === tokens2[j - 1]) {
          dp[i][j] = dp[i - 1][j - 1] + 1;
        } else {
          dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
        }
      }
    }
    let i = m, j = n;
    const chunks1 = [];
    const chunks2 = [];
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && tokens1[i - 1] === tokens2[j - 1]) {
        chunks1.unshift({ text: tokens1[i - 1], type: 'common' });
        chunks2.unshift({ text: tokens2[j - 1], type: 'common' });
        i--;
        j--;
      } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
        chunks2.unshift({ text: tokens2[j - 1], type: 'added' });
        j--;
      } else {
        chunks1.unshift({ text: tokens1[i - 1], type: 'removed' });
        i--;
      }
    }
    const compress = (chunks) => {
      if (chunks.length === 0) return [];
      const result = [];
      let current = chunks[0];
      for (let k = 1; k < chunks.length; k++) {
        if (chunks[k].type === current.type) {
          current.text += chunks[k].text;
        } else {
          result.push(current);
          current = chunks[k];
        }
      }
      result.push(current);
      return result;
    };
    return {
      diff1: compress(chunks1),
      diff2: compress(chunks2)
    };
  };

  const renderLineDiff1 = (line, counterpart) => {
    const { diff1 } = computeCharDiff(line, counterpart);
    return diff1.map((chunk, idx) => {
      if (chunk.type === 'removed') {
        return (
          <span
            key={idx}
            className="bg-rose-200/80 text-rose-900 dark:bg-rose-900/50 dark:text-rose-200 px-0.5 rounded font-semibold line-through decoration-rose-600 decoration-1"
          >
            {chunk.text}
          </span>
        );
      }
      return <span key={idx}>{chunk.text}</span>;
    });
  };

  const renderLineDiff2 = (line, counterpart) => {
    const { diff2 } = computeCharDiff(counterpart, line);
    return diff2.map((chunk, idx) => {
      if (chunk.type === 'added') {
        return (
          <span
            key={idx}
            className="bg-emerald-200/80 text-emerald-900 dark:bg-emerald-905/50 dark:text-emerald-200 px-0.5 rounded font-semibold"
          >
            {chunk.text}
          </span>
        );
      }
      return <span key={idx}>{chunk.text}</span>;
    });
  };

  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold text-slate-900 dark:text-white">{conflict.fileName}</h3>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Compare changes side-by-side and select a merge option or edit the file customly.
          </p>
        </div>
        <span className="rounded-full border border-amber-300 bg-amber-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-amber-600 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300">
          Conflict
        </span>
      </div>

      {/* Side-by-Side Comparison */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Current (Ours) Column */}
        <div className="flex flex-col rounded-2xl border border-rose-200 bg-rose-50/30 dark:border-rose-500/20 dark:bg-rose-950/10">
          <div className="flex items-center justify-between border-b border-rose-200/50 px-4 py-2 dark:border-rose-500/10">
            <span className="text-xs font-bold uppercase tracking-wider text-rose-600 dark:text-rose-400">Current (Ours)</span>
            <span className="text-xs text-rose-500/70 dark:text-rose-400/50">{currentLines.length} lines</span>
          </div>
          <div className="max-h-96 overflow-auto p-4 font-mono text-xs leading-relaxed text-slate-800 dark:text-slate-200">
            <table className="w-full border-collapse">
              <tbody>
                {currentLines.map((line, idx) => (
                  <tr key={idx} className="hover:bg-rose-100/30 dark:hover:bg-rose-950/20">
                    <td className="w-8 select-none pr-3 text-right text-rose-400/60">{idx + 1}</td>
                    <td className="whitespace-pre-wrap break-all">
                      {renderLineDiff1(line, incomingLines[idx])}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Incoming (Theirs) Column */}
        <div className="flex flex-col rounded-2xl border border-emerald-200 bg-emerald-50/30 dark:border-emerald-500/20 dark:bg-emerald-950/10">
          <div className="flex items-center justify-between border-b border-emerald-200/50 px-4 py-2 dark:border-emerald-500/10">
            <span className="text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">Incoming (Theirs)</span>
            <span className="text-xs text-emerald-500/70 dark:text-emerald-400/50">{incomingLines.length} lines</span>
          </div>
          <div className="max-h-96 overflow-auto p-4 font-mono text-xs leading-relaxed text-slate-800 dark:text-slate-200">
            <table className="w-full border-collapse">
              <tbody>
                {incomingLines.map((line, idx) => (
                  <tr key={idx} className="hover:bg-emerald-100/30 dark:hover:bg-emerald-950/20">
                    <td className="w-8 select-none pr-3 text-right text-emerald-400/60">{idx + 1}</td>
                    <td className="whitespace-pre-wrap break-all">
                      {renderLineDiff2(line, currentLines[idx])}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Main Actions Panel */}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-4 border-t border-slate-100 pt-4 dark:border-slate-800">
        <div className="flex flex-wrap gap-3">
          <button
            type="button"
            onClick={onAcceptCurrent}
            className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 transition hover:bg-rose-100 dark:border-rose-900/30 dark:bg-rose-950/20 dark:text-rose-400 dark:hover:bg-rose-950/40"
          >
            Use Ours (Current)
          </button>

          <button
            type="button"
            onClick={onAcceptIncoming}
            className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-semibold text-emerald-700 transition hover:bg-emerald-100 dark:border-emerald-900/30 dark:bg-emerald-950/20 dark:text-emerald-400 dark:hover:bg-emerald-950/40"
          >
            Use Theirs (Incoming)
          </button>

          <button
            type="button"
            onClick={onResolve}
            className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-100 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            Keep Both (Concat)
          </button>
        </div>

        <button
          type="button"
          onClick={() => setIsCustomMode(!isCustomMode)}
          className={`rounded-xl px-4 py-2 text-sm font-semibold transition ${
            isCustomMode
              ? 'bg-cyan-500 text-white hover:bg-cyan-600'
              : 'border border-cyan-500/30 bg-cyan-500/10 text-cyan-600 hover:bg-cyan-500/20 dark:text-cyan-400'
          }`}
        >
          {isCustomMode ? 'Close Custom Editor' : 'Open Custom Merge Editor'}
        </button>
      </div>

      {/* Expandable Custom Text Editor */}
      {isCustomMode && (
        <div className="mt-6 space-y-4 border-t border-slate-100 pt-6 dark:border-slate-800">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h4 className="text-sm font-semibold text-slate-900 dark:text-white">Custom Merged Content</h4>
              <p className="text-xs text-slate-500 dark:text-slate-400">Edit the merged result below and click save.</p>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setMergedText(currentStr)}
                className="rounded-lg bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                Copy Ours
              </button>
              <button
                type="button"
                onClick={() => setMergedText(incomingStr)}
                className="rounded-lg bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                Copy Theirs
              </button>
              <button
                type="button"
                onClick={initConflictMarkers}
                className="rounded-lg bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                Reset Markers
              </button>
            </div>
          </div>

          <div className="relative rounded-2xl border border-slate-200 bg-slate-50 p-2 dark:border-slate-800 dark:bg-slate-950/40">
            <textarea
              className="min-h-[250px] w-full bg-transparent p-2 font-mono text-xs leading-relaxed text-slate-800 outline-none dark:text-slate-200"
              value={mergedText}
              onChange={(e) => setMergedText(e.target.value)}
              placeholder="Edit merged output here..."
              spellCheck="false"
            />
          </div>

          <div className="flex justify-end">
            <button
              type="button"
              onClick={handleCustomSave}
              className="rounded-xl bg-cyan-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-cyan-700 dark:bg-cyan-500 dark:text-slate-950 dark:hover:bg-cyan-400"
            >
              Save & Resolve Conflict
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default ConflictResolver