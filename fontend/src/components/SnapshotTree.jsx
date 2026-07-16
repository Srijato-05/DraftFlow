import { useEffect, useState, useMemo } from "react";
import { useParams } from "react-router-dom";
import { FolderGit2, FileText, GitCommit, GitBranch } from "lucide-react";
import { useRepo } from "../context/RepoContext";

export default function SnapshotTree() {
  const { repoId, snapshotId } = useParams();
  const { selectedRepo } = useRepo();
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const commit = useMemo(() => {
    return (selectedRepo?.commits || []).find(c => c.id === snapshotId);
  }, [selectedRepo, snapshotId]);

  useEffect(() => {
    const fetchTree = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await fetch(`/api/commit-tree?commit=${encodeURIComponent(snapshotId)}`);
        if (res.ok) {
          const data = await res.json();
          setFiles(data);
        } else {
          setError("Failed to fetch snapshot file tree");
        }
      } catch (err) {
        setError(err.message || "Failed to load snapshot files");
      } finally {
        setLoading(false);
      }
    };
    fetchTree();
  }, [snapshotId]);

  if (loading) {
    return <div className="text-slate-500">Loading snapshot files...</div>;
  }

  if (error) {
    return (
      <div className="rounded-2xl border border-red-300 bg-red-50 p-8 text-center dark:border-red-800 dark:bg-red-950">
        <h2 className="text-2xl font-bold text-red-600">
          Error
        </h2>
        <p className="mt-3 text-slate-600 dark:text-slate-300">
          {error}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="flex items-center gap-3 text-3xl font-bold">
          <FolderGit2 className="text-cyan-500" size={32} />
          Snapshot Viewer
        </h1>
        <p className="mt-2 text-slate-500">
          Browse the complete repository snapshot.
        </p>
      </div>

      {/* Summary */}
      <div className="grid gap-4 md:grid-cols-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <p className="text-sm text-slate-500">Repository</p>
          <h2 className="mt-2 text-lg font-semibold">{repoId}</h2>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-center gap-2">
            <GitCommit size={16} />
            <p className="text-sm text-slate-500">Commit</p>
          </div>
          <h2 className="mt-2 text-lg font-semibold truncate" title={snapshotId}>
            {snapshotId?.slice(0, 8)}
          </h2>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-center gap-2">
            <FolderGit2 size={16} />
            <p className="text-sm text-slate-500">Message</p>
          </div>
          <h2 className="mt-2 text-lg font-semibold truncate" title={commit?.message}>
            {commit?.message || "No commit details"}
          </h2>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex items-center gap-2">
            <GitBranch size={16} />
            <p className="text-sm text-slate-500">Branch</p>
          </div>
          <h2 className="mt-2 text-lg font-semibold">
            {selectedRepo?.currentBranch || "main"}
          </h2>
        </div>
      </div>

      {/* Files */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-900">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-xl font-semibold">
            <FolderGit2 className="text-cyan-500" />
            Snapshot Files
          </h2>
          <span className="rounded-full bg-cyan-500/10 px-3 py-1 text-sm font-medium text-cyan-600">
            {files.length} Files
          </span>
        </div>

        {files.length === 0 ? (
          <p className="text-slate-500">No files found.</p>
        ) : (
          <div className="space-y-3">
            {files.map((file) => (
              <div
                key={file.path}
                className="flex items-center justify-between rounded-xl border border-slate-200 p-4 transition hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
              >
                <div className="flex items-center gap-3">
                  <FileText size={18} className="text-cyan-500" />
                  <div>
                    <p className="font-medium">{file.name}</p>
                    <p className="text-sm text-slate-500">{file.path}</p>
                  </div>
                </div>

                <div className="text-right">
                  <p className="text-sm font-medium">{file.extension || "N/A"}</p>
                  <p className="text-xs text-slate-500">{file.size} bytes</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}