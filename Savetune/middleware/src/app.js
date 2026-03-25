require('dotenv').config();
const express = require('express');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const apiRoutes = require('./routes/api');

const app = express();

// --- Logs (.log) para depuración ---
const LOG_DIR = process.env.SAVETUNE_LOG_DIR || path.join(__dirname, '..', 'logs');
const GET_LOG_PATH = process.env.SAVETUNE_GET_LOG || path.join(LOG_DIR, 'get_requests.log');
const ERROR_LOG_PATH =
  process.env.SAVETUNE_ERROR_LOG || path.join(LOG_DIR, 'errors_4xx_5xx.log');
const CONNECTION_LOG_PATH =
  process.env.SAVETUNE_CONNECTION_LOG || path.join(LOG_DIR, 'connections.log');

fs.mkdirSync(LOG_DIR, { recursive: true });
const getLogStream = fs.createWriteStream(GET_LOG_PATH, { flags: 'a' });
const errorLogStream = fs.createWriteStream(ERROR_LOG_PATH, { flags: 'a' });
const connLogStream = fs.createWriteStream(CONNECTION_LOG_PATH, { flags: 'a' });

const logLine = (stream, line) => {
  try {
    stream.write(line + '\n');
  } catch (_) {
    // no-op
  }
};

// 1) Log de todas las peticiones GET y errores (>= 400)
app.use((req, res, next) => {
  if (req.method !== 'GET') return next();

  const startedAt = Date.now();
  res.on('finish', () => {
    const elapsedMs = Date.now() - startedAt;
    const status = res.statusCode;
    const url = req.originalUrl || req.url;
    const ts = new Date().toISOString();

    logLine(getLogStream, `${ts} ${url} status=${status} elapsed_ms=${elapsedMs}`);
    if (status >= 400) {
      logLine(errorLogStream, `${ts} ${url} status=${status} elapsed_ms=${elapsedMs}`);
    }
  });

  next();
});

// 2) Conexiones activas (puerto del middle local: 3000) en "tiempo real"
const CONNECTION_POLL_INTERVAL_MS = Number(
  process.env.SAVETUNE_CONNECTION_POLL_INTERVAL_MS || '2000'
);
setInterval(() => {
  const ts = new Date().toISOString();
  exec('netstat -ano', { maxBuffer: 1024 * 1024 }, (err, stdout) => {
    logLine(connLogStream, `---- ${ts} port=3000 ----`);
    if (!stdout) return;
    stdout
      .toString()
      .split(/\r?\n/)
      .filter(Boolean)
      .filter((ln) => ln.includes(':3000'))
      .slice(0, 200)
      .forEach((ln) => logLine(connLogStream, ln.trim()));
  });
}, CONNECTION_POLL_INTERVAL_MS);

// Middleware
app.use(helmet());
app.use(cors());
app.use(
  compression({
    filter: (req, res) => {
      // Evitar compresión en streams binarios grandes (audio/zip) para no penalizar throughput.
      if (
        req.path === '/api/download' ||
        req.path === '/api/download-auto' ||
        req.path === '/api/download-youtube-album' ||
        req.path === '/api/download-job/stream'
      ) {
        return false;
      }
      return compression.filter(req, res);
    },
  })
);
app.use(express.json());

// Routes
app.use('/api', apiRoutes);

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// Error handler
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({
    success: false,
    error: 'Internal Server Error',
    message: err.message,
  });
});

module.exports = app;

// Permitir ejecutar en local con `node src/app.js`
if (require.main === module) {
  const port = process.env.PORT || 3000;
  app.listen(port, () => {
    // eslint-disable-next-line no-console
    console.log(`SaveTune backend listening on http://localhost:${port}`);
  });
}
