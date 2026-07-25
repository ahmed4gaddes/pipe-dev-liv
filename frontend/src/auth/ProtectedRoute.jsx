import { useAuth } from './AuthContext';
import Splash from '../pages/Splash';
import Forbidden from '../pages/Forbidden';

export default function ProtectedRoute({ children, minRole }) {
  const { initialized, authenticated, hasRole } = useAuth();

  if (!initialized) {
    return null;
  }

  if (!authenticated) {
    return <Splash />;
  }

  if (minRole && !hasRole(minRole)) {
    return <Forbidden />;
  }

  return children;
}
