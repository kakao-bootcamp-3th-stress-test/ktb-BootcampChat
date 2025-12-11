import client from "prom-client";

// Next.js API 라우트는 HMR/재시작 시 여러 번 로드될 수 있으므로
// 메트릭 초기화를 전역 싱글톤으로 묶어 중복 수집기(setInterval) 누적을 방지한다.
const metricsBundle =
  globalThis.__NEXT_PROM_BUNDLE__ ||
  (() => {
    const register = new client.Registry();

    const heapUsageGauge = new client.Gauge({
      name: "nextjs_heap_usage_ratio",
      help: "Heap memory usage ratio",
      registers: [register]
    });

    const httpRequestsTotal = new client.Counter({
      name: "nextjs_http_requests_total",
      help: "Total number of HTTP requests",
      labelNames: ["method", "path", "status"],
      registers: [register]
    });

    const httpRequestDuration = new client.Histogram({
      name: "nextjs_http_request_duration_seconds",
      help: "Duration of HTTP requests in seconds",
      labelNames: ["method", "path", "status"],
      buckets: [0.1, 0.3, 0.5, 0.7, 1, 3, 5, 7, 10],
      registers: [register]
    });

    const appInfo = new client.Gauge({
      name: "nextjs_app_info",
      help: "Application information",
      labelNames: ["version", "nodejs_version"],
      registers: [register]
    });
    appInfo.set({ version: "1.0.0", nodejs_version: process.version }, 1);

    // 기본 메트릭 수집 (CPU, 메모리, 이벤트 루프 등)
    const stopDefaultMetrics = client.collectDefaultMetrics({
      register,
      gcDurationBuckets: [0.001, 0.01, 0.1, 1, 2, 5] // GC 지속 시간 버킷 추가
    });

    const bundle = {
      register,
      heapUsageGauge,
      httpRequestsTotal,
      httpRequestDuration,
      stopDefaultMetrics
    };

    globalThis.__NEXT_PROM_BUNDLE__ = bundle;
    return bundle;
  })();

const { register, heapUsageGauge, httpRequestsTotal, httpRequestDuration } =
  metricsBundle;

export default async function handler(req, res) {
  if (req.method !== 'GET') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  try {
    // 힙 메모리 사용률 계산 및 업데이트
    const memUsage = process.memoryUsage();
    const heapUsageRatio = memUsage.heapUsed / memUsage.heapTotal;
    heapUsageGauge.set(heapUsageRatio);

    // GC 강제 실행 (메모리 디버깅 모드에서만)
    if (global.gc && process.env.NODE_ENV !== 'production') {
      global.gc();
    }

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
