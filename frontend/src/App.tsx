import { NavLink, Route, Routes, Link } from 'react-router-dom';
import { DashboardPage } from './DashboardPage';
import { MonitorPage } from './MonitorPage';
import { IncidentsPage } from './IncidentsPage';
export function App() {
  return <div className="app"><a className="skip-link" href="#main">Skip to content</a><aside className="sidebar">
    <Link className="brand" to="/"><span className="brand-mark" aria-hidden="true">S</span>sentinel<span className="version">0.1</span></Link>
    <p className="nav-label">OBSERVABILITY</p><nav aria-label="Main navigation"><NavLink to="/" end>Overview</NavLink><NavLink to="/incidents">Incidents</NavLink></nav>
    <div className="sidebar-footer"><span className="local-dot"/> Self-hosted workspace<small>Trusted local use</small></div>
  </aside><div className="workspace"><div className="topbar"><span>Sentinel / API monitoring</span><span className="mono">SINGLE INSTANCE</span></div>
    <main id="main"><Routes><Route path="/" element={<DashboardPage/>}/><Route path="/monitors/:id" element={<MonitorPage/>}/><Route path="/incidents" element={<IncidentsPage/>}/><Route path="*" element={<><h1>Page not found</h1><Link to="/">Return to overview</Link></>}/></Routes></main>
    <footer className="workspace-footer">Sentinel · All times shown in your local timezone</footer>
  </div></div>;
}
