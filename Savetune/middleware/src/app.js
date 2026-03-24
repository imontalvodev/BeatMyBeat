require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const apiRoutes = require('./routes/api');

const app = express();

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
