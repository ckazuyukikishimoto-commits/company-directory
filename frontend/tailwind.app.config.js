/**
 * ログイン画面・companies／admin／account 配下の画面用の Tailwind 設定。
 * 以前は各 HTML の中に `tailwind.config = {...}` を書き、CDN の Tailwind が
 * ブラウザ上で CSS を作っていた。オフラインでも崩れないように、
 * 各画面の設定をまとめてここに移し、事前に CSS を作ってアプリに同梱する。
 */
module.exports = {
  content: [
    '../src/main/resources/templates/login.html',
    '../src/main/resources/templates/companies/**/*.html',
    '../src/main/resources/templates/admin/**/*.html',
    '../src/main/resources/templates/account/**/*.html',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: '#0066cc',
        'background-light': '#fffbf0',
        'background-dark': '#fffbf0',
        'text-light-primary': '#222222',
        'text-light-secondary': '#444444',
        surface: '#ffffff',
      },
      fontFamily: {
        // form.html / import.html では 2 番目が Space Grotesk だった（各画面の <style> で上書き）
        display: ['"Noto Sans JP"', 'Inter', 'sans-serif'],
      },
      borderRadius: {
        DEFAULT: '0.5rem',
        lg: '0.75rem',
        xl: '1rem',
        full: '9999px',
      },
    },
  },
  plugins: [require('@tailwindcss/forms'), require('@tailwindcss/container-queries')],
};
