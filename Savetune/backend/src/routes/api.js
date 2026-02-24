const express = require('express');
const router = express.Router();

const spotifyService = require('../services/spotifyService');
const youtubeService = require('../services/youtubeService');
const { validateSpotifyPlaylistUrl } = require('../utils/validators');

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

    const result = await spotifyService.getPlaylist(url);
    res.json(result);
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

    const result = await youtubeService.searchVideo(query);
    res.json({ success: true, video: result });
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

    const videoUrl = `https://www.youtube.com/watch?v=${videoId}`;
    const { stream, title } = await youtubeService.downloadAudio(videoUrl);

    // Configurar headers para descarga
    res.setHeader('Content-Type', 'audio/mpeg');
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${title}.mp3"`
    );

    // Pipe el stream al response
    stream.pipe(res);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
