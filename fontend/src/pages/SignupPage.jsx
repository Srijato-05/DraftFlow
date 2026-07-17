import { useMemo, useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'

const domains = [
  'Frontend Development',
  'Backend Development',
  'Full Stack',
  'DevOps',
  'AI / ML',
  'Mobile Development',
  'Cyber Security',
  'Cloud Computing',
  'Data Science',
  'Game Development',
  'Student',
  'Other',
]

const experienceLevels = ['Beginner', 'Intermediate', 'Advanced', 'Professional']
const technologies = [
  'React',
  'Angular',
  'Vue',
  'Node.js',
  'Python',
  'Java',
  'C++',
  'Go',
  'Rust',
  'Docker',
  'Kubernetes',
  'AWS',
  'Azure',
  'GCP',
  'TensorFlow',
  'Next.js',
  'Express',
  'MongoDB',
  'PostgreSQL',
  'Redis',
]

const interestOptions = [
  'Open Source',
  'Git',
  'Version Control',
  'CI/CD',
  'DevOps',
  'AI Coding',
  'Team Collaboration',
  'Code Review',
  'Cloud',
  'Security',
]

function SignupPage({ onSignup }) {
  const navigate = useNavigate()
  const [form, setForm] = useState({
    fullName: '',
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    age: '',
    country: 'United States',
    timezone: 'UTC',
    domain: domains[0],
    experience: experienceLevels[1],
    preferredTheme: 'dark',
    technologies: [],
    interests: [],
    terms: false,
    privacy: false,
  })
  const [error, setError] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const canSubmit = useMemo(() => {
    return (
      form.fullName &&
      form.username &&
      form.email &&
      form.password &&
      form.password === form.confirmPassword &&
      form.terms &&
      form.privacy
    )
  }, [form])

  const handleChange = (event) => {
    const { name, value, type, checked } = event.target

    setForm((current) => ({
      ...current,
      [name]: type === 'checkbox' ? checked : value,
    }))
  }

  const toggleChip = (field, value) => {
    setForm((current) => {
      const list = current[field] || []
      const exists = list.includes(value)

      return {
        ...current,
        [field]: exists ? list.filter((item) => item !== value) : [...list, value],
      }
    })
  }

  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()

    if (!canSubmit) {
      setError('Please complete the required fields and accept the policies.')
      return
    }

    const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    if (!emailRegex.test(form.email)) {
      setError('Please enter a valid email address.')
      return
    }

    setLoading(true)
    setError('')
    try {
      await onSignup({
        name: form.fullName,
        username: form.username,
        email: form.email,
        password: form.password,
        domain: form.domain,
        experience: form.experience,
        preferredTheme: form.preferredTheme,
        technologies: form.technologies,
        interests: form.interests,
      })
      navigate('/welcome')
    } catch (err) {
      setError(err.message || 'Signup failed')
    } finally {
      setLoading(false)
    }
  }

  const inputClassName =
    'w-full rounded-2xl border border-slate-700 bg-slate-900/80 px-4 py-3 text-sm text-slate-100 placeholder:text-slate-500 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/10'

  return (
    <div className="min-h-screen bg-slate-950 px-4 py-8 text-slate-100 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-7xl overflow-hidden rounded-4xl border border-slate-800 bg-slate-900/80 shadow-2xl shadow-black/30 lg:grid lg:grid-cols-[0.9fr_1.1fr]">
        <div className="flex flex-col justify-between border-b border-slate-800 bg-linear-to-br from-slate-900 via-slate-800 to-slate-900 p-8 sm:p-10 lg:border-b-0 lg:border-r lg:p-12">
          <div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-cyan-400/20 bg-cyan-500/10 text-sm font-semibold tracking-[0.3em] text-cyan-300">V</div>
            <p className="mt-6 text-[11px] font-semibold uppercase tracking-[0.35em] text-slate-400">Create Account</p>
            <h1 className="mt-4 text-4xl font-semibold tracking-tight text-white sm:text-5xl">Join VCS</h1>
            <p className="mt-4 max-w-md text-base leading-7 text-slate-400">Build a profile tailored for modern development work, code review, and team collaboration.</p>
          </div>

          <div className="mt-10 rounded-2xl border border-slate-800 bg-slate-950/60 p-5 text-sm text-slate-400">
            <p className="font-medium text-white">Why teams choose VCS</p>
            <ul className="mt-3 space-y-2">
              <li>• Review work clearly with a structured commit history</li>
              <li>• Manage branches and resolve conflicts with confidence</li>
              <li>• Keep your developer workflow focused and calm</li>
            </ul>
          </div>
        </div>

        <div className="flex flex-col justify-center p-8 sm:p-10 lg:p-12">
          <div className="mb-8 flex items-center justify-between rounded-2xl border border-slate-800 bg-slate-950/60 p-2 text-sm text-slate-400">
            <Link to="/" className="px-3 py-2 font-medium text-slate-400 transition hover:text-white">Sign In</Link>
            <div className="flex items-center gap-2 rounded-xl bg-slate-800 px-3 py-2 text-white">
              <span className="relative flex h-2.5 w-2.5 rounded-full bg-cyan-400" />
              <span className="font-medium">Sign Up</span>
            </div>
          </div>

          <div className="mb-6">
            <h2 className="text-3xl font-semibold tracking-tight text-white">Create your workspace</h2>
            <p className="mt-2 text-sm text-slate-400">Set up your profile and get started in seconds.</p>
          </div>

          <form className="space-y-6" onSubmit={handleSubmit}>
            <section className="rounded-[20px] border border-slate-800 bg-slate-950/60 p-5 sm:p-6">
              <h3 className="text-sm font-semibold uppercase tracking-[0.3em] text-slate-400">Personal details</h3>
              <div className="mt-5 grid gap-4 md:grid-cols-2">
                <label className="text-sm text-slate-300"><span className="mb-2 block">Full Name</span><input name="fullName" value={form.fullName} onChange={handleChange} className={inputClassName} placeholder="Jane Doe" /></label>
                <label className="text-sm text-slate-300"><span className="mb-2 block">Username</span><input name="username" value={form.username} onChange={handleChange} className={inputClassName} placeholder="jane.dev" /></label>
                <label className="text-sm text-slate-300"><span className="mb-2 block">Email Address</span><input name="email" type="email" value={form.email} onChange={handleChange} className={inputClassName} placeholder="jane@vcs.dev" /></label>
                <label className="text-sm text-slate-300"><span className="mb-2 block">Age</span><input name="age" type="number" value={form.age} onChange={handleChange} className={inputClassName} placeholder="24" /></label>
                <div className="relative md:col-span-2">
                  <label className="text-sm text-slate-300"><span className="mb-2 block">Password</span><input name="password" type={showPassword ? 'text' : 'password'} value={form.password} onChange={handleChange} className={`${inputClassName} pr-12`} placeholder="••••••••" /></label>
                  <button type="button" onClick={() => setShowPassword((current) => !current)} className="absolute right-3 top-[2.4rem] text-sm text-slate-400 transition hover:text-white" aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button>
                </div>
                <div className="relative md:col-span-2">
                  <label className="text-sm text-slate-300"><span className="mb-2 block">Confirm Password</span><input name="confirmPassword" type={showConfirmPassword ? 'text' : 'password'} value={form.confirmPassword} onChange={handleChange} className={`${inputClassName} pr-12`} placeholder="••••••••" /></label>
                  <button type="button" onClick={() => setShowConfirmPassword((current) => !current)} className="absolute right-3 top-[2.4rem] text-sm text-slate-400 transition hover:text-white" aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}>{showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}</button>
                </div>
                <label className="text-sm text-slate-300"><span className="mb-2 block">Country</span><input name="country" value={form.country} onChange={handleChange} className={inputClassName} /></label>
                <label className="text-sm text-slate-300"><span className="mb-2 block">Timezone</span><input name="timezone" value={form.timezone} onChange={handleChange} className={inputClassName} /></label>
              </div>
            </section>

            <section className="rounded-[20px] border border-slate-800 bg-slate-950/60 p-5 sm:p-6">
              <h3 className="text-sm font-semibold uppercase tracking-[0.3em] text-slate-400">Professional profile</h3>
              <div className="mt-5 grid gap-4 md:grid-cols-2">
                <label className="text-sm text-slate-300"><span className="mb-2 block">Primary Domain</span><select name="domain" value={form.domain} onChange={handleChange} className={inputClassName}>{domains.map((domain) => (<option key={domain} value={domain}>{domain}</option>))}</select></label>
                <label className="text-sm text-slate-300"><span className="mb-2 block">Experience Level</span><select name="experience" value={form.experience} onChange={handleChange} className={inputClassName}>{experienceLevels.map((level) => (<option key={level} value={level}>{level}</option>))}</select></label>
              </div>

              <div className="mt-6">
                <p className="mb-3 text-sm font-medium text-slate-300">Preferred Technologies</p>
                <div className="flex flex-wrap gap-2">
                  {technologies.map((technology) => {
                    const active = form.technologies.includes(technology)
                    return (
                      <button key={technology} type="button" onClick={() => toggleChip('technologies', technology)} className={`rounded-full px-3 py-2 text-sm transition ${active ? 'bg-cyan-500 text-slate-950' : 'bg-white/10 text-slate-300 hover:bg-white/20'}`}>
                        {technology}
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className="mt-6">
                <p className="mb-3 text-sm font-medium text-slate-300">Interested In</p>
                <div className="flex flex-wrap gap-2">
                  {interestOptions.map((interest) => {
                    const active = form.interests.includes(interest)
                    return (
                      <button key={interest} type="button" onClick={() => toggleChip('interests', interest)} className={`rounded-full px-3 py-2 text-sm transition ${active ? 'bg-cyan-500 text-slate-950' : 'bg-white/10 text-slate-300 hover:bg-white/20'}`}>
                        {interest}
                      </button>
                    )
                  })}
                </div>
              </div>
            </section>

            <section className="rounded-[20px] border border-slate-800 bg-slate-950/60 p-5 sm:p-6">
              <div className="space-y-3 text-sm text-slate-300">
                <label className="flex items-start gap-3 rounded-2xl border border-slate-800 bg-slate-900/70 p-3"><input type="checkbox" name="terms" checked={form.terms} onChange={handleChange} className="mt-1 h-4 w-4 rounded border-slate-700 bg-black text-cyan-400" /><span>I agree to the Terms of Service.</span></label>
                <label className="flex items-start gap-3 rounded-2xl border border-slate-800 bg-slate-900/70 p-3"><input type="checkbox" name="privacy" checked={form.privacy} onChange={handleChange} className="mt-1 h-4 w-4 rounded border-slate-700 bg-black text-cyan-400" /><span>I agree to the Privacy Policy.</span></label>
                <label className="flex items-start gap-3 rounded-2xl border border-slate-800 bg-slate-900/70 p-3"><span className="w-full"><span className="mb-2 block">Preferred Theme</span><select name="preferredTheme" value={form.preferredTheme ?? 'dark'} onChange={handleChange} className={inputClassName}><option value="dark">Dark</option><option value="light">Light</option><option value="system">System</option></select></span></label>
              </div>
            </section>

            {error ? <p className="text-sm text-rose-400">{error}</p> : null}

            <button type="submit" disabled={loading || !canSubmit} className="w-full rounded-2xl bg-cyan-500 px-4 py-3 text-sm font-semibold text-slate-950 transition duration-200 hover:-translate-y-0.5 hover:bg-cyan-400 disabled:opacity-50">
              {loading ? 'Creating Account...' : 'Create Account'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}

export default SignupPage
