function BranchList({ branches, currentBranch, onSwitch }) {
  return (
    <div className="space-y-3 rounded-[20px] border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
      {branches.map((branch) => {
        const isActive = branch === currentBranch

        return (
          <button
            key={branch}
            type="button"
            onClick={() => onSwitch(branch)}
            className={`flex w-full items-center justify-between rounded-2xl border px-4 py-3 text-left transition-all duration-200 ${
              isActive
                ? 'border-cyan-500 bg-cyan-500/10 text-slate-900 shadow-sm dark:text-white'
                : 'border-slate-200 bg-slate-50 text-slate-700 hover:border-cyan-400 hover:bg-white dark:border-slate-800 dark:bg-slate-950/60 dark:text-slate-200 dark:hover:bg-slate-800'
            }`}
          >
            <span className="font-medium">{branch}</span>

            {isActive ? (
              <span className="text-xs font-semibold uppercase tracking-[0.2em] text-cyan-600 dark:text-cyan-300">
                Active
              </span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}

export default BranchList