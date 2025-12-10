// Health check endpoint for ALB (부하테스트 최적화)
export default function handler(req, res) {
  if (req.method === 'GET') {
    // 최소한의 응답만 반환 (부하 최소화)
    res.setHeader('Cache-Control', 'no-cache');
    res.status(200).json({ status: 'ok' });
  } else {
    res.status(405).json({ error: 'Method not allowed' });
  }
}

