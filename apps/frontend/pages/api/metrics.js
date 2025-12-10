import client from 'prom-client';

// 기본 메트릭 수집 (CPU, 메모리, 이벤트 루프 등)
const collectDefaultMetrics = client.collectDefaultMetrics;
const Registry = client.Registry;
const register = new Registry();

collectDefaultMetrics({ register });

// 커스텀 메트릭: HTTP 요청 카운터
const httpRequestsTotal = new client.Counter({
  name: 'nextjs_http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'path', 'status'],
  registers: [register],
});

// 커스텀 메트릭: HTTP 요청 지속 시간
const httpRequestDuration = new client.Histogram({
  name: 'nextjs_http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'path', 'status'],
  buckets: [0.1, 0.3, 0.5, 0.7, 1, 3, 5, 7, 10],
  registers: [register],
});

// 앱 정보 메트릭
const appInfo = new client.Gauge({
  name: 'nextjs_app_info',
  help: 'Application information',
  labelNames: ['version', 'nodejs_version'],
  registers: [register],
});

appInfo.set({ version: '1.0.0', nodejs_version: process.version }, 1);

export default async function handler(req, res) {
  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  try {
    res.setHeader('Content-Type', register.contentType);
    const metrics = await register.metrics();
    res.status(200).send(metrics);
  } catch (error) {
    console.error('Error collecting metrics:', error);
    res.status(500).json({ error: 'Error collecting metrics' });
  }
}

// 다른 API 라우트에서 사용할 수 있도록 export
export { httpRequestsTotal, httpRequestDuration, register };

