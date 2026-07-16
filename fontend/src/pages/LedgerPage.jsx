import { useEffect, useState } from "react";
import { History, Clock, User, ArrowLeftRight, HelpCircle, AlertCircle } from "lucide-react";
import { useParams } from "react-router-dom";
import { useRepo } from "../context/RepoContext";

export default function LedgerPage() {
  const { repoId } = useParams();
  const { selectedRepo } = useRepo();
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function fetchLedger() {
      try {
        setLoading(true);
        setError(null);
        const res = await fetch("/api/ledger");
        if (!res.ok) {
          throw new Error(`Failed to fetch ledger logs (HTTP ${res.status})`);
        }
        const data = await res.json();
        setEntries(data || []);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    fetchLedger();
  }, [repoId]);

  const formatDate = (timestamp) => {
    if (!timestamp) return "N/A";
    // Check if timestamp is in seconds or milliseconds
    const date = new Date(timestamp > 9999999999 ? timestamp : timestamp * 1000);
    return date.toLocaleString();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="rounded-3xl border border-slate-200/70 bg-white/80 p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.35em] text-cyan-500">Transaction History</p>
          <h2 className="mt-2 text-2xl font-semibold text-slate-900 dark:text-white">
            {selectedRepo?.name || repoId} Ledger
          </h2>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            Chronological log of reference changes, commit activities, and checkout updates in this repository.
          </p>
        </div>
      </div>

      {loading ? (
        <div className="flex min-h-[300px] items-center justify-center rounded-3xl border border-slate-200/70 bg-white/80 p-8 dark:border-slate-800 dark:bg-slate-900/70">
          <div className="flex flex-col items-center gap-3">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-cyan-500" />
            <p className="text-sm text-slate-500 dark:text-slate-400">Reading ledger transactions...</p>
          </div>
        </div>
      ) : error ? (
        <div className="rounded-3xl border border-rose-200 bg-rose-50/50 p-6 text-rose-700 dark:border-rose-900/30 dark:bg-rose-950/20 dark:text-rose-400">
          <div className="flex items-center gap-3">
            <AlertCircle size={20} />
            <div>
              <p className="font-semibold">Unable to Load Ledger</p>
              <p className="mt-1 text-sm">{error}</p>
            </div>
          </div>
        </div>
      ) : entries.length === 0 ? (
        <div className="flex min-h-[300px] flex-col items-center justify-center rounded-3xl border border-slate-200/70 bg-white/80 p-8 text-center dark:border-slate-800 dark:bg-slate-900/70">
          <History size={40} className="text-slate-400" />
          <h3 className="mt-4 text-lg font-medium text-slate-950 dark:text-white">Ledger is Empty</h3>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">No VCS actions or commits have been logged yet.</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-3xl border border-slate-200/70 bg-white/80 shadow-sm dark:border-slate-800 dark:bg-slate-900/70">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  <th className="px-6 py-4">Action & Log Message</th>
                  <th className="px-6 py-4">Ref Transition (Old → New)</th>
                  <th className="px-6 py-4">Operator</th>
                  <th className="px-6 py-4">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60 text-sm text-slate-700 dark:text-slate-350">
                {entries.map((entry, idx) => (
                  <tr key={idx} className="hover:bg-slate-50/50 dark:hover:bg-slate-950/20 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-start gap-2.5">
                        <History size={16} className="mt-0.5 text-cyan-500 shrink-0" />
                        <span className="font-medium text-slate-900 dark:text-white break-words">
                          {entry.message || "Logged transition"}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4 font-mono text-xs">
                      <div className="flex items-center gap-2">
                        <span className="bg-slate-100 px-1.5 py-0.5 rounded text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                          {entry.oldHash?.substring(0, 8) || "00000000"}
                        </span>
                        <ArrowLeftRight size={12} className="text-slate-400" />
                        <span className="bg-cyan-500/10 px-1.5 py-0.5 rounded text-cyan-600 dark:text-cyan-400">
                          {entry.newHash?.substring(0, 8) || "00000000"}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-1.5">
                        <User size={14} className="text-slate-400" />
                        <span>{entry.author || "System"}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-xs">
                      <div className="flex items-center gap-1.5 text-slate-500">
                        <Clock size={14} />
                        <span>{formatDate(entry.timestamp)}</span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
