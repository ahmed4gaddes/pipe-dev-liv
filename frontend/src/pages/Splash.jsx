import { motion } from 'framer-motion';
import { useAuth } from '../auth/AuthContext';
import Button from '../components/ui/Button';
import biatLogo from '../assets/biat-logo.png';
import './Splash.css';

export default function Splash() {
  const { login, initialized } = useAuth();

  return (
    <div className="splash">
      <div className="splash-glow splash-glow-1" />
      <div className="splash-glow splash-glow-2" />

      <motion.div
        className="splash-panel"
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: 'easeOut' }}
      >
        <img src={biatLogo} alt="BIAT Innovation & Technology" className="splash-logo" />
        <h1 className="splash-title">Pipe Dev Liv</h1>
        <p className="splash-tagline">
          Plateforme de livraison continue — tickets, pipelines et audit, du même endroit.
        </p>
        <Button variant="gold" size="lg" onClick={login} disabled={!initialized}>
          Se connecter
        </Button>
        <div className="splash-footer">BIAT · Innovation &amp; Technology</div>
      </motion.div>
    </div>
  );
}
