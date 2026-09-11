import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

const root = document.getElementById('root')
if (root === null) {
  throw new Error('index.html is missing the #root element the app mounts into')
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
