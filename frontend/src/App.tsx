import { Routes, Route } from 'react-router-dom';
import { AuthGuard } from './components/AuthGuard';
import { LoginPage } from './pages/LoginPage';
import { OAuth2Callback } from './pages/OAuth2Callback';
import { MarketListPage } from './pages/MarketListPage';
import { CoinDetailPage } from './pages/CoinDetailPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/oauth2/callback" element={<OAuth2Callback />} />
      <Route path="/" element={<AuthGuard><MarketListPage /></AuthGuard>} />
      <Route path="/coin/:ticker" element={<AuthGuard><CoinDetailPage /></AuthGuard>} />
      <Route path="/battle" element={<AuthGuard><div>Battle</div></AuthGuard>} />
      <Route path="/ranking" element={<AuthGuard><div>Ranking</div></AuthGuard>} />
      <Route path="/result/:battleId" element={<AuthGuard><div>ResultCard</div></AuthGuard>} />
      <Route path="/portfolio" element={<AuthGuard><div>Portfolio</div></AuthGuard>} />
    </Routes>
  );
}

export default App;
