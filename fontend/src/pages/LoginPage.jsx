import { useState } from 'react'
import { Eye, EyeOff, GitBranch, KeyRound, Sparkles } from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'

function LoginPage({ onLogin, user }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(true)
  const [error, setError] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  if (user) {
    return <Navigate to="/repos" replace />
  }

  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setLoading(true)
    setError('')
    try {
      await onLogin({ email: email.trim(), password })
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  const handleSocialLogin = (provider) => {
    setError('')
    onLogin({
      email: `${provider.toLowerCase()}@demo.dev`,
      name: provider,
      rememberMe,
    })
  }

  const inputClassName =
    'w-full rounded-2xl border border-slate-700 bg-slate-900/80 px-4 py-3 text-sm text-slate-100 placeholder:text-slate-500 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/10'

  const providers = [
    {
      name: 'Google',
      icon: (
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" aria-hidden="true">
          <path d="M21 12.23c0-.78-.07-1.53-.2-2.25H12v4.26h5.2a4.45 4.45 0 0 1-1.93 2.92v2.42h3.12c1.83-1.69 2.88-4.17 2.88-7.35Z" fill="#4285F4" />
          <path d="M12 22c2.6 0 4.78-.86 6.37-2.33l-3.12-2.42c-.86.58-1.97.92-3.25.92-2.49 0-4.6-1.68-5.35-3.95H3.4v2.48A10 10 0 0 0 12 22Z" fill="#34A853" />
          <path d="M6.65 13.22A6.01 6.01 0 0 1 6.65 10.8H3.4V8.32a10 10 0 0 0 0 9.8l3.25-2.9Z" fill="#FBBC05" />
          <path d="M12 6.08c1.4 0 2.66.48 3.65 1.42l2.74-2.74A10 10 0 0 0 3.4 8.32l3.25 2.9C7.4 7.76 9.51 6.08 12 6.08Z" fill="#EA4335" />
        </svg>
      ),
    },
    {
      name: 'GitHub',
      icon: (
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden="true">
          <path d="M12 .5a12 12 0 0 0-3.79 23.04c.6.11.82-.26.82-.58v-2.04c-3.34.72-4.04-1.43-4.04-1.43-.54-1.38-1.33-1.75-1.33-1.75-1.08-.74.08-.73.08-.73 1.2.08 1.83 1.24 1.83 1.24 1.06 1.81 2.78 1.29 3.46.99.11-.77.42-1.29.76-1.59-2.67-.3-5.47-1.34-5.47-5.95 0-1.32.47-2.4 1.24-3.25-.12-.3-.54-1.52.12-3.17 0 0 1.01-.32 3.3 1.23a11.4 11.4 0 0 1 6.01 0c2.29-1.55 3.3-1.23 3.3-1.23.66 1.65.24 2.87.12 3.17.77.85 1.24 1.93 1.24 3.25 0 4.62-2.8 5.64-5.47 5.94.43.37.82 1.1.82 2.22v3.29c0 .32.22.7.83.58A12 12 0 0 0 12 .5Z" />
        </svg>
      ),
    },
    {
      name: 'LinkedIn',
      icon: (
        <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden="true">
          <path d="M6.94 8.5A1.56 1.56 0 1 0 6.94 5.38a1.56 1.56 0 0 0 0 3.12ZM5.5 9.7h2.88V18H5.5zM10.1 9.7h2.76v1.15h.04c.38-.72 1.32-1.48 2.72-1.48 2.9 0 3.44 1.91 3.44 4.4V18H16.1v-7.38c0-1.76-.03-4.02-2.45-4.02-2.46 0-2.84 1.92-2.84 3.9V18H10.1z" />
        </svg>
      ),
    },
  ]

  return (
    <div className="min-h-screen bg-slate-950 px-4 py-8 text-slate-100 sm:px-6 lg:px-8">
      <div className="mx-auto flex max-w-6xl flex-col overflow-hidden rounded-4xl border border-slate-800 bg-slate-900/80 shadow-2xl shadow-black/30 lg:flex-row">
        <div className="flex flex-1 flex-col justify-between bg-linear-to-br from-slate-900 via-slate-800 to-slate-900 p-8 sm:p-10 lg:p-12">
          <div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-cyan-400/20 bg-cyan-500/10 text-sm font-semibold tracking-[0.3em] text-cyan-300">
              V
            </div>
            <p className="mt-6 text-[11px] font-semibold uppercase tracking-[0.35em] text-slate-400">Version Control System</p>
            <h1 className="mt-4 text-4xl font-semibold tracking-tight text-white sm:text-5xl">Sign in to VCS</h1>
            <p className="mt-4 max-w-md text-base leading-7 text-slate-400">A polished workspace for exploring repositories, commit history, diffs, and branching flows.</p>
          </div>

          <div className="mt-8 space-y-3">
            {providers.map((provider) => (
              <button key={provider.name} type="button" onClick={() => handleSocialLogin(provider.name)} className="flex w-full max-w-sm items-center justify-start gap-3 rounded-2xl border border-slate-700 bg-slate-950/70 px-4 py-3 text-sm font-medium text-slate-200 transition duration-200 hover:-translate-y-0.5 hover:border-cyan-400/40 hover:bg-slate-800">
                <span className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-700 bg-slate-900 text-white">
                  {provider.icon}
                </span>
                <span>Continue with {provider.name}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="flex flex-1 items-center justify-center p-8 sm:p-10 lg:p-12">
          <div className="w-full max-w-md">
            <div className="mb-8">
              <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-400">Sign in</p>
              <h2 className="mt-2 text-3xl font-semibold text-white">Welcome back</h2>
              <p className="mt-2 text-sm text-slate-400">Use your mock workspace credentials to continue.</p>
            </div>

            <form className="space-y-5" onSubmit={handleSubmit}>
              <div>
                <label className="mb-2 block text-sm font-medium text-slate-300" htmlFor="email">Email</label>
                <div className="relative">
                  <KeyRound size={16} className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  <input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} className={`${inputClassName} pl-11`} placeholder="dev@vcs.dev" />
                </div>
              </div>

              <div>
                <label className="mb-2 block text-sm font-medium text-slate-300" htmlFor="password">Password</label>
                <div className="relative">
                  <KeyRound size={16} className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  <input id="password" type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} className={`${inputClassName} pr-12 pl-11`} placeholder="••••••••" />
                  <button type="button" onClick={() => setShowPassword((current) => !current)} className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400 transition hover:text-white" aria-label={showPassword ? 'Hide password' : 'Show password'}>
                    {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between gap-3 rounded-2xl border border-slate-800 bg-slate-950/60 px-3 py-2 text-sm text-slate-400">
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={rememberMe} onChange={() => setRememberMe((current) => !current)} className="h-4 w-4 rounded border-slate-700 bg-slate-900 text-cyan-500" />
                  <span>Remember me</span>
                </label>
                <button type="button" className="transition hover:text-white">Forgot password</button>
              </div>

              {error ? <p className="text-sm text-rose-400">{error}</p> : null}

              <button type="submit" disabled={loading} className="w-full rounded-2xl bg-cyan-500 px-4 py-3 text-sm font-semibold text-slate-950 transition duration-200 hover:bg-cyan-400 disabled:opacity-50">
                {loading ? 'Signing in...' : 'Sign in'}
              </button>
            </form>

            <div className="mt-6 flex flex-wrap items-center justify-between gap-3 text-sm text-slate-400">
              <span>Don&apos;t have an account?</span>
              <Link to="/signup" className="rounded-full border border-slate-700 px-3 py-2 font-medium text-slate-200 transition hover:border-cyan-400 hover:text-white">Create account</Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
