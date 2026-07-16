import { useState, useEffect } from "react";
import { GitPullRequest } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";
import { useRepo } from "../context/RepoContext";

export default function RaisePullRequestPage() {
  const { repoId } = useParams();
  const navigate = useNavigate();
  const { selectedRepo } = useRepo();

  const branches = selectedRepo?.branches || ["main"];

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [sourceBranch, setSourceBranch] = useState(branches[0] || "main");
  const [targetBranch, setTargetBranch] = useState(branches[0] || "main");
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (branches.length > 0) {
      setSourceBranch(branches[0]);
      setTargetBranch(branches[0]);
    }
  }, [selectedRepo]);

  async function handleSubmit(e) {
    e.preventDefault();
    setLoading(true);
    setError("");
    setSuccess(false);

    try {
      const res = await fetch("/api/pull-requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title,
          description,
          sourceBranch,
          targetBranch,
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.error || "Failed to raise pull request");
      }

      setSuccess(true);
      setTitle("");
      setDescription("");

      setTimeout(() => {
        navigate(`/repo/${repoId}/pull-requests`);
      }, 1500);
    } catch (err) {
      setError(err.message || "Failed to raise pull request");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8">

      <div>
        <h1 className="flex items-center gap-3 text-3xl font-bold">
          <GitPullRequest className="text-cyan-500" size={32} />
          Raise Pull Request
        </h1>

        <p className="mt-2 text-slate-500">
          Create a new pull request between branches.
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        className="space-y-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-700 dark:bg-slate-900"
      >
        <div>
          <label className="mb-2 block font-medium">
            Pull Request Title
          </label>

          <input
            type="text"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-cyan-500"
            placeholder="Enter PR title..."
          />
        </div>

        <div>
          <label className="mb-2 block font-medium">
            Description
          </label>

          <textarea
            rows={5}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full rounded-xl border border-slate-300 p-3 outline-none focus:border-cyan-500"
            placeholder="Describe your changes..."
          />
        </div>

        <div className="grid gap-5 md:grid-cols-2">

          <div>
            <label className="mb-2 block font-medium">
              Source Branch
            </label>

            <select
              value={sourceBranch}
              onChange={(e) => setSourceBranch(e.target.value)}
              className="w-full rounded-xl border border-slate-300 p-3"
            >
              {branches.map(b => (
                <option key={b} value={b}>{b}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-2 block font-medium">
              Target Branch
            </label>

            <select
              value={targetBranch}
              onChange={(e) => setTargetBranch(e.target.value)}
              className="w-full rounded-xl border border-slate-300 p-3"
            >
              {branches.map(b => (
                <option key={b} value={b}>{b}</option>
              ))}
            </select>
          </div>

        </div>

        <button
          type="submit"
          disabled={loading}
          className="rounded-xl bg-cyan-500 px-6 py-3 font-medium text-white transition hover:bg-cyan-600 disabled:opacity-50"
        >
          {loading ? "Creating..." : "Create Pull Request"}
        </button>

        {success && (
          <div className="rounded-xl border border-green-300 bg-green-50 p-4 text-green-700">
            ✅ Pull Request Created Successfully!
          </div>
        )}

        {error && (
          <div className="rounded-xl border border-rose-300 bg-rose-50 p-4 text-rose-700">
            ❌ {error}
          </div>
        )}
      </form>

    </div>
  );
}