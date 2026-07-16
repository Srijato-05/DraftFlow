import { createContext, useContext, useEffect, useMemo, useState } from 'react'

const AUTH_STORAGE_KEY = 'vcsUser'

function buildProfile(account = {}) {
  const email = account.email || ''
  const name = account.name || account.fullName || account.username || 'Developer'
  const username = account.username || email.split('@')[0] || 'developer'

  return {
    id: account.id || `${Date.now()}`,
    name,
    username,
    email,
    password: account.password || '',
    avatar: account.avatar || 'D',
    age: account.age ?? '',
    country: account.country || '',
    domain: account.domain || '',
    experience: account.experience || '',
    preferredTheme: account.preferredTheme || 'dark',
    technologies: account.technologies || [],
    interests: account.interests || [],
    rememberMe: Boolean(account.rememberMe),
    joinedDate: account.joinedDate || new Date().toISOString(),
  }
}

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    if (typeof window === 'undefined') {
      return null
    }

    const stored = window.localStorage.getItem(AUTH_STORAGE_KEY)

    if (!stored) {
      return null
    }

    try {
      return buildProfile(JSON.parse(stored))
    } catch {
      return null
    }
  })

  useEffect(() => {
    if (typeof window === 'undefined') {
      return
    }

    if (user) {
      window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))
      return
    }

    window.localStorage.removeItem(AUTH_STORAGE_KEY)
  }, [user])

  const login = async (credentials) => {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials),
    })
    if (!res.ok) {
      const err = await res.json()
      throw new Error(err.error || 'Login failed')
    }
    const data = await res.json()
    setUser(buildProfile(data))
  }

  const signup = async (account) => {
    const res = await fetch('/api/auth/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(account),
    })
    if (!res.ok) {
      const err = await res.json()
      throw new Error(err.error || 'Signup failed')
    }
    const data = await res.json()
    const profile = buildProfile(data)
    setUser(profile)
    return profile
  }

  const logout = () => {
    setUser(null)
  }

  const updateProfile = async (updates) => {
    if (!user) return
    const res = await fetch('/api/auth/profile', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-User-Email': user.email,
      },
      body: JSON.stringify({ ...updates, email: user.email }),
    })
    if (!res.ok) {
      const err = await res.json()
      throw new Error(err.error || 'Failed to update profile')
    }
    const data = await res.json()
    setUser(buildProfile(data))
  }

  const value = useMemo(
    () => ({ user, setUser, login, signup, logout, updateProfile }),
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }

  return context
}
