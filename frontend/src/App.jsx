import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';
import AppShell from './components/layout/AppShell';
import ProtectedRoute from './auth/ProtectedRoute';
import NotFound from './pages/NotFound';
import { SkeletonRows } from './components/ui/Skeleton';

const Dashboard = lazy(() => import('./pages/Dashboard'));
const TicketsList = lazy(() => import('./pages/TicketsList'));
const TicketCreate = lazy(() => import('./pages/TicketCreate'));
const TicketDetail = lazy(() => import('./pages/TicketDetail'));
const PipelinesList = lazy(() => import('./pages/PipelinesList'));
const PipelineDetail = lazy(() => import('./pages/PipelineDetail'));
const Notifications = lazy(() => import('./pages/Notifications'));
const Team = lazy(() => import('./pages/Team'));
const AuditLogs = lazy(() => import('./pages/AuditLogs'));
const Profile = lazy(() => import('./pages/Profile'));

function PageFallback() {
  return <SkeletonRows rows={5} />;
}

function Lazy({ children }) {
  return <Suspense fallback={<PageFallback />}>{children}</Suspense>;
}

export default function App() {
  return (
    <Routes>
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Lazy><Dashboard /></Lazy>} />
        <Route path="tickets" element={<Lazy><TicketsList /></Lazy>} />
        <Route
          path="tickets/new"
          element={<ProtectedRoute minRole="ROLE_DEVELOPER"><Lazy><TicketCreate /></Lazy></ProtectedRoute>}
        />
        <Route path="tickets/:id" element={<Lazy><TicketDetail /></Lazy>} />
        <Route path="pipelines" element={<Lazy><PipelinesList /></Lazy>} />
        <Route path="pipelines/:id" element={<Lazy><PipelineDetail /></Lazy>} />
        <Route path="notifications" element={<Lazy><Notifications /></Lazy>} />
        <Route
          path="team"
          element={<ProtectedRoute minRole="ROLE_TECH_LEAD"><Lazy><Team /></Lazy></ProtectedRoute>}
        />
        <Route
          path="audit-logs"
          element={<ProtectedRoute minRole="ROLE_ADMIN"><Lazy><AuditLogs /></Lazy></ProtectedRoute>}
        />
        <Route path="profile" element={<Lazy><Profile /></Lazy>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
