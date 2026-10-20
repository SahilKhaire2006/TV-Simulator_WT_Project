/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        orbitron: ["'Orbitron'", 'monospace'],
        inter: ["'Inter'", 'sans-serif'],
      },
      colors: {
        primary:    '#00d4ff',
        secondary:  '#7c3aed',
        bg:         '#08080f',
        surface:    '#12121a',
        surface2:   '#1c1c2e',
        surface3:   '#242436',
        tvgray:     '#2d2d44',
        success:    '#22c55e',
        danger:     '#ef4444',
        warn:       '#f59e0b',
        muted:      '#64748b',
      },
      boxShadow: {
        glow:     '0 0 20px rgba(0, 212, 255, 0.4)',
        glowLg:   '0 0 40px rgba(0, 212, 255, 0.5)',
        glowPurp: '0 0 20px rgba(124, 58, 237, 0.5)',
        inner:    'inset 0 2px 8px rgba(0,0,0,0.6)',
      },
      animation: {
        'pulse-slow':  'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'blink':       'blink 1.4s step-start infinite',
        'slide-up':    'slideUp 0.4s ease',
        'bounce-once': 'bounceOnce 0.35s ease',
      },
      keyframes: {
        blink:       { '0%,100%': { opacity: 1 }, '50%': { opacity: 0 } },
        slideUp:     { from: { opacity: 0, transform: 'translateY(12px)' }, to: { opacity: 1, transform: 'none' } },
        bounceOnce:  { '0%': { transform: 'scale(1)' }, '50%': { transform: 'scale(0.92)' }, '100%': { transform: 'scale(1)' } },
      },
      backgroundImage: {
        'remote-grad':  'linear-gradient(160deg, #1c1c2e 0%, #12121a 60%, #0a0a14 100%)',
        'btn-num-grad': 'linear-gradient(145deg, #242436, #1a1a28)',
        'lcd-grad':     'linear-gradient(160deg, #0a1628 0%, #0d1f3c 100%)',
      }
    },
  },
  plugins: [],
}
