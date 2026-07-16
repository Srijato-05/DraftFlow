import { useMemo, useState } from 'react'
import { GitBranch, GitCommitVertical, Sparkles } from 'lucide-react'

function buildCommitGraph(commits) {
  const nodes = commits.map((commit, index) => ({
    commitId: commit.commitId,
    shortId: commit.commitId.slice(0, 7),
    message: commit.message,
    author: commit.author,
    branch: commit.branch,
    mergeCommit: Boolean(commit.mergeCommit),
    parents: commit.parentCommitIds || [],
    y: index * 110 + 40,
    lane: 0,
  }))

  const idToNode = new Map(nodes.map((node) => [node.commitId, node]))
  const childrenMap = new Map()

  nodes.forEach((node) => {
    node.parents.forEach((parentId) => {
      if (!childrenMap.has(parentId)) childrenMap.set(parentId, [])
      childrenMap.get(parentId).push(node.commitId)
    })
  })

  let nextLane = 0
  const assignLane = (node) => {
    if (node.lane !== undefined && node.lane !== null) return
    const parentLane = node.parents
      .map((pid) => idToNode.get(pid))
      .filter(Boolean)
      .map((parent) => parent.lane)
      .find((lane) => lane !== undefined)

    if (parentLane !== undefined) {
      node.lane = parentLane
    } else {
      node.lane = nextLane
      nextLane += 1
    }
  }

  nodes.forEach(assignLane)

  const nodesWithPosition = nodes.map((node) => ({
    ...node,
    x: 92 + node.lane * 176,
  }))

  const edges = []
  nodesWithPosition.forEach((node) => {
    node.parents.forEach((parentId) => {
      const parent = idToNode.get(parentId)
      if (!parent) return
      edges.push({ from: node, to: parent })
    })
  })

  return { nodes: nodesWithPosition, edges }
}

function CommitGraph({ commits, currentBranch, headCommitId }) {
  const [hoveredCommitId, setHoveredCommitId] = useState(null)

  const graph = useMemo(() => buildCommitGraph(commits), [commits])

  const hoveredCommit = graph.nodes.find((node) => node.commitId === hoveredCommitId) || null
  const branchLabels = useMemo(() => {
    const labels = []
    const seen = new Set()

    graph.nodes.forEach((node) => {
      const branch = node.branch
      if (!branch || seen.has(branch)) return
      seen.add(branch)
      labels.push(branch)
    })

    return labels
  }, [graph.nodes])

  return (
    <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold uppercase tracking-[0.2em] text-emerald-500">
            <GitCommitVertical size={16} />
            DAG commit graph
          </div>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">Snapshot-driven history with merge lanes, branch splits, and HEAD context.</p>
        </div>
        <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-300">
          <GitBranch size={16} className="text-cyan-500" />
          {currentBranch}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {branchLabels.map((branch) => (
          <span key={branch} className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.24em] text-slate-500 dark:border-slate-700 dark:bg-slate-800/70 dark:text-slate-400">
            {branch}
          </span>
        ))}
      </div>

      <div className="mt-4 overflow-x-auto rounded-[20px] border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-800 dark:bg-slate-950/80">
        <svg width={Math.max(760, graph.nodes.length * 176)} height={Math.max(320, graph.nodes.length * 95)} className="min-w-full">
          <defs>
            <linearGradient id="graph-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stopColor="#22d3ee" />
              <stop offset="100%" stopColor="#0f766e" />
            </linearGradient>
          </defs>
          {graph.edges.map((edge) => {
            const fromX = edge.from.x
            const fromY = edge.from.y
            const toX = edge.to.x
            const toY = edge.to.y
            return (
              <path
                key={`${edge.from.commitId}-${edge.to.commitId}`}
                d={`M${fromX} ${fromY - 8} C${fromX} ${(fromY + toY) / 2} ${toX} ${(fromY + toY) / 2} ${toX} ${toY + 8}`}
                fill="none"
                stroke="url(#graph-gradient)"
                strokeWidth="2.3"
                strokeLinecap="round"
                opacity="0.85"
              />
            )
          })}
          {graph.nodes.map((node) => {
            const isHead = node.commitId === headCommitId
            return (
              <g key={node.commitId} onMouseEnter={() => setHoveredCommitId(node.commitId)} onMouseLeave={() => setHoveredCommitId(null)} className="cursor-pointer">
                {node.mergeCommit ? (
                  <polygon points={`${node.x},${node.y - 14} ${node.x + 14},${node.y} ${node.x},${node.y + 14} ${node.x - 14},${node.y}`} fill={isHead ? '#f59e0b' : '#0f766e'} />
                ) : (
                  <circle cx={node.x} cy={node.y} r="13" fill={isHead ? '#f59e0b' : '#0f766e'} stroke={isHead ? '#fde68a' : '#e2e8f0'} strokeWidth="3" />
                )}
                <circle cx={node.x} cy={node.y} r="7" fill={isHead ? '#fff7ed' : '#f8fafc'} />
                <text x={node.x} y={node.y + 4} textAnchor="middle" fontSize="9" fontWeight="700" fill="#0f172a">
                  {node.shortId.slice(0, 3)}
                </text>
                {isHead ? <circle cx={node.x} cy={node.y} r="22" fill="none" stroke="#f59e0b" strokeWidth="2" strokeDasharray="4 3" /> : null}
              </g>
            )
          })}
        </svg>
      </div>

      {hoveredCommit ? (
        <div className="mt-4 rounded-2xl border border-slate-200 bg-slate-50/80 p-4 text-sm text-slate-600 shadow-sm dark:border-slate-700 dark:bg-slate-950/70 dark:text-slate-300">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="font-semibold text-slate-900 dark:text-white">{hoveredCommit.message}</div>
              <div className="mt-1 text-xs uppercase tracking-[0.24em] text-slate-400">{hoveredCommit.author} • {hoveredCommit.shortId}</div>
            </div>
            <div className="inline-flex items-center gap-2 rounded-full border border-cyan-200 bg-cyan-50 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.24em] text-cyan-700 dark:border-cyan-500/20 dark:bg-cyan-500/10 dark:text-cyan-300">
              <Sparkles size={12} />
              {hoveredCommit.mergeCommit ? 'Merge commit' : 'Snapshot commit'}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}

export default CommitGraph
