import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import './index.css'
import { QueryProvider } from './providers/QueryProvider.jsx'
import ToastContainer from './components/ui/Toast.jsx'
import ConfirmDialog from './components/ui/ConfirmDialog.jsx'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <QueryProvider>
        <App />
        <ToastContainer />
        <ConfirmDialog />
      </QueryProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
