import { ArrowRight, GitBranch, GitCommitVertical, Sparkles } from 'lucide-react'

function CommitList({ commits, selectedCommitId, onSelect }) {
  return (
    <div className="space-y-3">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200/70 bg-white/70 px-4 py-3 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <div className="text-sm font-medium text-slate-600 dark:text-slate-300">Recent history</div>
        <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-slate-500 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-400">
          <Sparkles size={12} className="text-cyan-500" />
          {commits.length} entries
        </div>
      </div>
      {commits.map((commit) => {
        const isActive = selectedCommitId === commit.commitId
        const isLatest = commit.commitId === commits[0]?.commitId

        return (
          <button key={commit.commitId} type="button" onClick={() => onSelect(commit)} className={`flex w-full flex-col gap-3 rounded-[20px] border p-4 text-left transition-all duration-200 md:flex-row md:items-start md:justify-between ${isActive ? 'border-cyan-500 bg-cyan-500/10 shadow-md' : 'border-slate-200 bg-white hover:-translate-y-0.5 hover:border-cyan-400 hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900/70 dark:hover:bg-slate-800'}`}>
            <div className="max-w-2xl">
              <div className="flex flex-wrap items-center gap-2">
                <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${isActive ? 'bg-cyan-500 text-slate-950' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'}`}>{commit.commitId.slice(0, 7)}</span>
                {isLatest ? <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${isActive ? 'bg-slate-900 text-white' : 'bg-emerald-500/10 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-400'}`}>Latest</span> : null}
                {commit.mergeCommit ? <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${isActive ? 'bg-violet-500 text-white' : 'bg-violet-500/10 text-violet-600 dark:bg-violet-500/15 dark:text-violet-400'}`}>Merge</span> : null}
                {commit.branch ? <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${isActive ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'}`}><GitBranch size={12} />{commit.branch}</span> : null}
              </div>

              <p className={`mt-2 font-medium ${isActive ? 'text-slate-900 dark:text-white' : 'text-slate-800 dark:text-slate-100'}`}>{commit.message}</p>
              <p className={`mt-1 text-sm ${isActive ? 'text-slate-600 dark:text-slate-300' : 'text-slate-500 dark:text-slate-400'}`}>{commit.author} • {commit.timestamp}</p>
            </div>

            <div className={`inline-flex items-center gap-2 text-sm font-medium ${isActive ? 'text-cyan-600 dark:text-cyan-300' : 'text-slate-500 dark:text-slate-400'}`}>
              <GitCommitVertical size={14} />
              Open diff
              <ArrowRight size={14} />
            </div>
          </button>
        )
      })}
    </div>
  )
}

export default CommitList