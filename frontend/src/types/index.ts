export interface ApiResponse<T> {
  data: T;
  message: string;
}

export interface Position {
  positionId: number;
  ticker: string;
  direction: 'LONG' | 'SHORT';
  quantity: number;
  averagePrice: number;
  leverage: number;
  currentPrice: number;
  evaluatedValue: number;
  unrealizedPnl: number;
  unrealizedPnlRate: number;
  liquidationPrice: number;
  status: 'OPEN' | 'CLOSED';
  openedAt: string;
}

export interface Portfolio {
  userId: number;
  balance: number;
  totalAsset: number;
  totalPnl: number;
  totalPnlRate: number;
}

export interface PortfolioResponse {
  portfolio: Portfolio;
  positions: Position[];
}

export interface BuyOrderRequest {
  idempotencyKey: string;
  ticker: string;
  orderType: 'MARKET' | 'LIMIT';
  direction: 'LONG' | 'SHORT';
  amount: number;
  leverage: number;
  limitPrice: number | null;
}

export interface BuyOrderResponse {
  orderId: number;
  ticker: string;
  direction: string;
  orderType: string;
  requestedAmount: number;
  executedPrice: number | null;
  executedAmount: number | null;
  leverage: number;
  status: 'PENDING' | 'FILLED' | 'CANCELLED';
  createdAt: string;
}

export interface SellOrderRequest {
  idempotencyKey: string;
  positionId: number;
  closeRatio: number;
}

export interface SellOrderResponse {
  orderId: number;
  positionId: number;
  ticker: string;
  direction: string;
  closedQuantity: number;
  executedPrice: number;
  realizedPnl: number;
  remainingQuantity: number;
  status: 'FILLED' | 'PARTIALLY_FILLED';
  createdAt: string;
}
