// Health check endpoint for ALB (부하테스트 최적화)
export default function handler(req, res) {
  if (req.method === 'GET') {
    // HashMap 생성 오버헤드 제거, 직접 JSON 문자열 반환
    // 최소한의 메모리 할당과 직렬화 오버헤드
    res.setHeader('Cache-Control', 'no-cache');
    res.status(200).send('{"status":"ok"}');
  } else {
    res.status(405).send('{"error":"Method not allowed"}');
  }
}

