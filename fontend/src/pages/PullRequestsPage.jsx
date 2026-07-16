import { useState, useEffect } from "react";
import { GitPullRequest, GitMerge, ArrowRight } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export default function PullRequestsPage() {
  const { repoId } = useParams();
  const [prs, setPrs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchPrs = async () => {
    try {
      const res = await fetch("/api/pull-requests");
      if (res.ok) {
        const data = await res.json();
        setPrs(data);
      } else {
        setError("Failed to fetch pull requests");
      }
    } catch (err) {
      setError(err.message || "Failed to load pull requests");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPrs();
  }, []);

  const handleMerge = async (prId) => {
    try {
      const res = await fetch("/api/pull-requests/merge", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: prId }),
      });
      if (res.ok) {
        fetchPrs();
      } else {
        const err = await res.json();
        alert(err.error || "Failed to merge pull request");
      }
    } catch (err) {
      alert(err.message || "Error merging pull request");
    }
  };

  if (loading) {
    return (
      <div className="flex min-h-[400px] items-center justify-center">
        <p className="text-slate-500">Loading pull requests...</p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Header */}

      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-3 text-3xl font-bold text-slate-900 dark:text-white">
            <GitPullRequest className="text-cyan-500" size={32} />
            Pull Requests
          </h1>

          <p className="mt-2 text-slate-500 dark:text-slate-400">
            Review and merge repository changes.
          </p>
        </div>

        <Link
          to={`/repo/${repoId}/pull-requests/new`}
          className="rounded-xl bg-cyan-500 px-5 py-3 font-medium text-white transition hover:bg-cyan-600"
        >
          + New Pull Request
        </Link>
      </div>

      {/* Error */}
      {error && (
        <div className="rounded-xl border border-rose-300 bg-rose-50 p-4 text-rose-700">
          ❌ {error}
        </div>
      )}

      {/* Pull Requests */}

      <div className="space-y-5">
        {prs.length === 0 ? (
          <p className="text-slate-500">No pull requests found.</p>
        ) : (
          prs.map((pr) => (
            <div
              key={pr.id}
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition hover:shadow-lg dark:border-slate-700 dark:bg-slate-900"
            >
              {/* Top */}

              <div className="flex items-start justify-between">
                <div>
                  <h2 className="text-xl font-semibold">
                    #{pr.id?.replace("pr-", "") || pr.number} {pr.title}
                  </h2>

                  <p className="mt-2 text-slate-500">
                    {pr.description}
                  </p>
                </div>

                <span
                  className={`rounded-full px-3 py-1 text-sm font-medium ${
                    pr.status === "merged"
                      ? "bg-emerald-100 text-emerald-600"
                      : "bg-yellow-100 text-yellow-700"
                  }`}
                >
                  {pr.status}
                </span>
              </div>

              {/* Branches */}

              <div className="mt-6 flex items-center gap-3 text-sm">
                <span className="rounded-lg bg-slate-100 px-3 py-1 dark:bg-slate-800">
                  {pr.sourceBranch}
                </span>

                <ArrowRight size={16} />

                <span className="rounded-lg bg-slate-100 px-3 py-1 dark:bg-slate-800">
                  {pr.targetBranch}
                </span>
              </div>

              {/* Footer */}

              <div className="mt-6 flex items-center justify-between">
                <span className="text-sm text-slate-500">
                  Created: {pr.createdAt || "Just now"}
                </span>

                {pr.status === "open" ? (
                  <button
                    onClick={() => handleMerge(pr.id)}
                    className="flex items-center gap-2 rounded-xl bg-cyan-500 px-5 py-2 text-white transition hover:bg-cyan-600"
                  >
                    <GitMerge size={18} />
                    Merge
                  </button>
                ) : (
                  <button
                    disabled
                    className="cursor-not-allowed rounded-xl bg-slate-200 px-5 py-2 text-slate-500 dark:bg-slate-800"
                  >
                    Already Merged
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}