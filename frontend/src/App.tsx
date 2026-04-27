import { Routes, Route } from 'react-router-dom';

function App() {
  return (
    <Routes>
      <Route path="/" element={<div>Home</div>} />
      <Route path="/coin/:ticker" element={<div>CoinDetail</div>} />
      <Route path="/battle" element={<div>Battle</div>} />
      <Route path="/ranking" element={<div>Ranking</div>} />
      <Route path="/result/:battleId" element={<div>ResultCard</div>} />
      <Route path="/portfolio" element={<div>Portfolio</div>} />
    </Routes>
  );
}

export default App;
