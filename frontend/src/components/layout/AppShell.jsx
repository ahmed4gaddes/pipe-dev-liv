import { useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { useAuth } from '../../auth/AuthContext';
import { useCurrentUser } from '../../api/users';
import { useUnreadCount } from '../../api/notifications';
import Icon from '../ui/Icon';
import Avatar from '../ui/Avatar';
import biatLogo from '../../assets/biat-logo.png';
import './AppShell.css';

const NAV_ITEMS = [
  { to: '/', label: 'Tableau de bord', icon: 'dashboard', end: true, minRole: 'ROLE_VIEWER' },
  { to: '/tickets', label: 'Tickets', icon: 'ticket', minRole: 'ROLE_VIEWER' },
  { to: '/pipelines', label: 'Pipelines', icon: 'pipeline', minRole: 'ROLE_VIEWER' },
  { to: '/notifications', label: 'Notifications', icon: 'bell', minRole: 'ROLE_VIEWER' },
  { to: '/team', label: 'Équipe', icon: 'team', minRole: 'ROLE_TECH_LEAD' },
  { to: '/audit-logs', label: "Journal d'audit", icon: 'audit', minRole: 'ROLE_ADMIN' },
];

export default function AppShell() {
  const { hasRole, fullName, email, logout } = useAuth();
  // Déclenche la synchronisation du profil (POST implicite via GET /api/users/me, voir
  // UserController) dès qu'une page protégée par AppShell est atteinte, au lieu de ne le
  // faire que si l'utilisateur visite /profile — sans ça, "Créé par" ne peut jamais résoudre
  // le nom d'un utilisateur qui n'a jamais ouvert cette page précise.
  useCurrentUser();
  const { data: unread } = useUnreadCount();
  const location = useLocation();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);

  const currentItem = NAV_ITEMS.find((item) =>
    item.end ? location.pathname === item.to : location.pathname.startsWith(item.to)
  );

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <img src={biatLogo} alt="BIAT Innovation & Technology" className="sidebar-logo" />
          <div className="sidebar-product">BIAT&nbsp;Flow</div>
        </div>

        <nav className="sidebar-nav">
          {NAV_ITEMS.filter((item) => hasRole(item.minRole)).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-item ${isActive ? 'nav-item-active' : ''}`}
            >
              <Icon name={item.icon} size={19} />
              <span>{item.label}</span>
              {item.to === '/notifications' && unread?.count > 0 && (
                <span className="nav-badge">{unread.count}</span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="sidebar-footer-title">BIAT · Innovation &amp; Technology</div>
          <div className="sidebar-footer-sub">Plateforme de livraison continue</div>
        </div>
      </aside>

      <div className="shell-main">
        <header className="topbar">
          <h1 className="topbar-title">{currentItem?.label ?? ''}</h1>

          <div className="topbar-actions">
            <button className="bell-btn" onClick={() => navigate('/notifications')} aria-label="Notifications">
              <Icon name="bell" size={20} />
              {unread?.count > 0 && <span className="bell-dot" />}
            </button>

            <div className="user-menu" tabIndex={0} onBlur={() => setMenuOpen(false)}>
              <button className="user-menu-trigger" onClick={() => setMenuOpen((o) => !o)}>
                <Avatar name={fullName || email} size={34} />
                <Icon name="chevronDown" size={16} className="user-menu-chevron" />
              </button>
              <AnimatePresence>
                {menuOpen && (
                  <motion.div
                    className="user-menu-dropdown"
                    initial={{ opacity: 0, y: -6 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -6 }}
                    transition={{ duration: 0.14 }}
                  >
                    <div className="user-menu-name">{fullName || 'Utilisateur'}</div>
                    <div className="user-menu-email">{email}</div>
                    <button className="user-menu-link" onMouseDown={() => navigate('/profile')}>
                      <Icon name="profile" size={16} /> Profil
                    </button>
                    <button className="user-menu-link user-menu-logout" onMouseDown={logout}>
                      <Icon name="logout" size={16} /> Déconnexion
                    </button>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </div>
        </header>

        <main className="content">
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.18 }}
            >
              <Outlet />
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  );
}
