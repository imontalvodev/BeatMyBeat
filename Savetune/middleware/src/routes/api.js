const express = require('express');
const { Readable } = require('stream');

const router = express.Router();

const { validateSpotifyPlaylistUrl } = require('../utils/validators');

const PY_BACKEND_URL = process.env.PY_BACKEND_URL || 'http://localhost:4000';

// GET /api/playlist?url=...
router.get('/playlist', async (req, res, next) => {
  try {
    const { url } = req.query;

    if (!validateSpotifyPlaylistUrl(url)) {
      return res.status(400).json({
        success: false,
        error: 'Invalid Spotify URL',
        message: 'Please provide a valid Spotify playlist URL',
      });
    }

    const upstream = await fetch(
      `${PY_BACKEND_URL}/api/playlist?url=${encodeURIComponent(url)}`
    );
    const body = await upstream.text();

    res
      .status(upstream.status)
      .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

// GET /api/search-youtube?query=...
router.get('/search-youtube', async (req, res, next) => {
  try {
    const { query } = req.query;

    if (!query) {
      return res.status(400).json({
        success: false,
        error: 'Missing query',
        message: 'Please provide a search query',
      });
    }

    const upstream = await fetch(
      `${PY_BACKEND_URL}/api/search-youtube?query=${encodeURIComponent(query)}`
    );
    const body = await upstream.text();

    res
      .status(upstream.status)
      .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

// GET /api/download?videoId=...
router.get('/download', async (req, res, next) => {
  try {
    const { videoId } = req.query;

    if (!videoId) {
      return res.status(400).json({
        success: false,
        error: 'Missing videoId',
        message: 'Please provide a YouTube video ID',
      });
    }

    const upstream = await fetch(
      `${PY_BACKEND_URL}/api/download?videoId=${encodeURIComponent(videoId)}`
    );

    // Si el backend Python responde con error JSON, lo reenviamos tal cual
    const contentType = upstream.headers.get('content-type') || '';
    if (!upstream.ok && contentType.includes('application/json')) {
      const json = await upstream.text();
      return res
        .status(upstream.status)
        .set('Content-Type', contentType)
        .send(json);
    }

    // Copiar headers relevantes (tipo de contenido y descarga)
    const disposition = upstream.headers.get('content-disposition');
    if (disposition) {
      res.setHeader('Content-Disposition', disposition);
    }
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'audio/mpeg');

    const nodeStream = Readable.fromWeb(upstream.body);
    nodeStream.pipe(res);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
