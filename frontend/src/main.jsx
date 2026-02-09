import { StrictMode } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './globals.css'
import { QueryProvider } from './providers/QueryProvider.jsx'
import { Toaster } from './components/ui/sonner.jsx'
import ConfirmDialog from './components/ui/ConfirmDialog.jsx'

ReactDOM.createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <QueryProvider>
        <App />
        <Toaster />
        <ConfirmDialog />
      </QueryProvider>
    </BrowserRouter>
  </StrictMode>,
)
