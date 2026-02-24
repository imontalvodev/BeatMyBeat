const ytdl = require('ytdl-core');
const { google } = require('googleapis');

class YouTubeService {
  constructor() {
    this.youtube = google.youtube({
      version: 'v3',
      auth: process.env.YOUTUBE_API_KEY, // Opcional
    });
  }

  async searchVideo(query) {
    try {
      // Si tienes API key de YouTube
      if (process.env.YOUTUBE_API_KEY) {
        const response = await this.youtube.search.list({
          part: 'snippet',
          q: query,
          type: 'video',
          maxResults: 1,
          videoCategoryId: '10', // Música
        });

        const video = response.data.items[0];
        if (!video) {
          throw new Error('No YouTube results found');
        }

        return {
          id: video.id.videoId,
          title: video.snippet.title,
          url: `https://www.youtube.com/watch?v=${video.id.videoId}`,
          thumbnail: video.snippet.thumbnails.high.url,
        };
      }

      // Fallback: construir URL de búsqueda
      const searchUrl = `https://www.youtube.com/results?search_query=${encodeURIComponent(
        query
      )}`;

      // Aquí podrías usar Puppeteer para scrapear el primer resultado
      // Por simplicidad, devuelve la URL de búsqueda
      return {
        searchUrl,
        message: 'User must search manually',
      };
    } catch (error) {
      console.error('Error searching YouTube:', error);
      throw new Error('Failed to search YouTube');
    }
  }

  async downloadAudio(videoUrl) {
    try {
      const info = await ytdl.getInfo(videoUrl);

      // Obtener el mejor formato de audio
      const audioFormats = ytdl.filterFormats(info.formats, 'audioonly');
      if (audioFormats.length === 0) {
        throw new Error('No audio formats available');
      }

      // Crear stream de descarga
      const stream = ytdl(videoUrl, {
        quality: 'highestaudio',
        filter: 'audioonly',
      });

      return {
        stream,
        title: info.videoDetails.title,
        duration: info.videoDetails.lengthSeconds,
      };
    } catch (error) {
      console.error('Error downloading from YouTube:', error);
      throw new Error('Failed to download audio');
    }
  }
}

module.exports = new YouTubeService();
