const express = require('express');
const { Readable } = require('stream');

const router = express.Router();

const { validateSpotifyPlaylistUrl } = require('../utils/validators');

const PY_BACKEND_URL = process.env.PY_BACKEND_URL || 'http://localhost:4000';
const STREAM_HIGH_WATER_MARK = Number(process.env.MIDDLEWARE_STREAM_HIGH_WATER_MARK || 262144);

function pipeUpstreamBody(upstream, res) {
  if (!upstream.body) {
    throw new Error('Upstream response has no body stream');
  }
  const nodeStream = Readable.fromWeb(upstream.body, {
    highWaterMark: STREAM_HIGH_WATER_MARK,
  });
  nodeStream.on('error', (err) => {
    if (!res.headersSent) {
      res.status(502).json({
        success: false,
        error: 'ProxyStreamError',
        message: err.message,
      });
      return;
    }
    res.destroy(err);
  });
  nodeStream.pipe(res);
}

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

// GET /api/search-youtube
router.get('/search-youtube', async (req, res, next) => {
  try {
    const { query, title, artist, album } = req.query;

    // Debe existir al menos una fuente de búsqueda (query libre o metadatos)
    if (!query && !title && !artist && !album) {
      return res.status(400).json({
        success: false,
        error: 'Missing query',
        message: 'Please provide a search query or song metadata',
      });
    }

    const url = new URL(`${PY_BACKEND_URL}/api/search-youtube`);

    if (query) url.searchParams.set('query', query);
    if (title) url.searchParams.set('title', title);
    if (artist) url.searchParams.set('artist', artist);
    if (album) url.searchParams.set('album', album);

    const upstream = await fetch(url);
    const body = await upstream.text();

    res
      .status(upstream.status)
      .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

// GET /api/search-song-suggestions?query=...&limit=...
router.get('/search-song-suggestions', async (req, res, next) => {
  try {
    const { query, limit } = req.query;

    if (!query || !String(query).trim()) {
      return res.status(400).json({
        success: false,
        error: 'MissingQuery',
        message: 'Please provide query',
      });
    }

    const url = new URL(`${PY_BACKEND_URL}/api/search-song-suggestions`);
    url.searchParams.set('query', String(query).trim());
    if (limit !== undefined && String(limit).trim() !== '') {
      url.searchParams.set('limit', String(limit).trim());
    }

    let upstream;
    try {
      upstream = await fetch(url);
    } catch (e) {
      return res.status(502).json({
        success: false,
        error: 'UpstreamUnavailable',
        message: `No se pudo conectar al backend Python (${PY_BACKEND_URL}). ${e?.message || e}`,
      });
    }

    const body = await upstream.text();
    return res
      .status(upstream.status)
      .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

// GET /api/lyrics?title=...&artist=...
router.get('/lyrics', async (req, res, next) => {
  try {
    const { title, artist } = req.query;

    if (!title || !artist) {
      return res.status(400).json({
        success: false,
        error: 'MissingMetadata',
        message: 'Please provide title and artist',
      });
    }

    const url = new URL(`${PY_BACKEND_URL}/api/lyrics`);
    url.searchParams.set('title', title);
    url.searchParams.set('artist', artist);

    const upstream = await fetch(url);
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

    pipeUpstreamBody(upstream, res);
  } catch (error) {
    next(error);
  }
});

// GET /api/download-auto
router.get('/download-auto', async (req, res, next) => {
  try {
    const { query, title, artist, album, imageUrl } = req.query;

    // Debe existir al menos una fuente de búsqueda (query libre o metadatos)
    if (!query && !title && !artist && !album) {
      return res.status(400).json({
        success: false,
        error: 'Missing query',
        message: 'Please provide a search query or song metadata (title, artist, album)',
      });
    }

    const url = new URL(`${PY_BACKEND_URL}/api/download-auto`);

    if (query) url.searchParams.set('query', query);
    if (title) url.searchParams.set('title', title);
    if (artist) url.searchParams.set('artist', artist);
    if (album) url.searchParams.set('album', album);
    if (imageUrl) url.searchParams.set('imageUrl', imageUrl);

    let upstream;
    try {
      upstream = await fetch(url);
    } catch (e) {
      return res.status(502).json({
        success: false,
        error: 'UpstreamUnavailable',
        message: `No se pudo conectar al backend Python (${PY_BACKEND_URL}). ${e?.message || e}`,
      });
    }

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

    pipeUpstreamBody(upstream, res);
  } catch (error) {
    next(error);
  }
});

// GET /api/download-youtube-album
router.get('/download-youtube-album', async (req, res, next) => {
  try {
    const { playlistUrl } = req.query;

    if (!playlistUrl) {
      return res.status(400).json({
        success: false,
        error: 'MissingMetadata',
        message: 'Provide playlistUrl',
      });
    }

    const url = new URL(`${PY_BACKEND_URL}/api/download-youtube-album`);
    if (playlistUrl) url.searchParams.set('playlistUrl', playlistUrl);

    let upstream;
    try {
      upstream = await fetch(url);
    } catch (e) {
      return res.status(502).json({
        success: false,
        error: 'UpstreamUnavailable',
        message: `No se pudo conectar al backend Python (${PY_BACKEND_URL}). ${e?.message || e}`,
      });
    }

    const contentType = upstream.headers.get('content-type') || '';
    if (!upstream.ok && contentType.includes('application/json')) {
      const json = await upstream.text();
      return res
        .status(upstream.status)
        .set('Content-Type', contentType)
        .send(json);
    }

    const disposition = upstream.headers.get('content-disposition');
    if (disposition) {
      res.setHeader('Content-Disposition', disposition);
    }
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/zip');

    pipeUpstreamBody(upstream, res);
  } catch (error) {
    next(error);
  }
});

// GET /api/resolve-youtube-album
router.get('/resolve-youtube-album', async (req, res, next) => {
  try {
    const { playlistUrl } = req.query;

    if (!playlistUrl) {
      return res.status(400).json({
        success: false,
        error: 'MissingMetadata',
        message: 'Provide playlistUrl',
      });
    }

    const url = new URL(`${PY_BACKEND_URL}/api/resolve-youtube-album`);
    if (playlistUrl) url.searchParams.set('playlistUrl', playlistUrl);

    let upstream;
    try {
      upstream = await fetch(url);
    } catch (e) {
      return res.status(502).json({
        success: false,
        error: 'UpstreamUnavailable',
        message: `No se pudo conectar al backend Python (${PY_BACKEND_URL}). ${e?.message || e}`,
      });
    }
    const body = await upstream.text();

    res
      .status(upstream.status)
      .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

// GET /api/download-job?jobId=...
router.get('/download-job', async (req, res, next) => {
  try {
    const { jobId } = req.query;
    if (!jobId) {
      return res.status(400).json({
        success: false,
        error: 'Missing jobId',
        message: 'Please provide a jobId',
      });
    }

    const upstream = await fetch(`${PY_BACKEND_URL}/api/download-job?jobId=${encodeURIComponent(jobId)}`);
    const body = await upstream.text();

    res
      .status(upstream.status)
      .set('Content-Type', upstream.headers.get('content-type') || 'application/json')
      .send(body);
  } catch (error) {
    next(error);
  }
});

// GET /api/download-job/stream?jobId=...
router.get('/download-job/stream', async (req, res, next) => {
  try {
    const { jobId } = req.query;
    if (!jobId) {
      return res.status(400).json({
        success: false,
        error: 'Missing jobId',
        message: 'Please provide a jobId',
      });
    }

    const upstream = await fetch(`${PY_BACKEND_URL}/api/download-job/stream?jobId=${encodeURIComponent(jobId)}`);

    const contentType = upstream.headers.get('content-type') || '';
    if (!upstream.ok && contentType.includes('application/json')) {
      const json = await upstream.text();
      return res
        .status(upstream.status)
        .set('Content-Type', contentType)
        .send(json);
    }

    const disposition = upstream.headers.get('content-disposition');
    if (disposition) {
      res.setHeader('Content-Disposition', disposition);
    }
    res.setHeader('Content-Type', upstream.headers.get('content-type') || 'audio/mpeg');

    pipeUpstreamBody(upstream, res);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
