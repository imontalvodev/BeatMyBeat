const puppeteer = require('puppeteer');

class SpotifyService {
  async getPlaylist(url) {
    let browser;
    try {
      browser = await puppeteer.launch({
        headless: 'new',
        args: ['--no-sandbox', '--disable-setuid-sandbox'],
      });

      const page = await browser.newPage();

      // User agent para evitar detección
      await page.setUserAgent(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
      );

      await page.goto(url, {
        waitUntil: 'networkidle2',
        timeout: 30000,
      });

      // Esperar a que existan enlaces a tracks (más estable)
      await page.waitForSelector('a[href*="/track/"]', {
        timeout: 20000,
      });

      // Extraer información de la playlist
      const playlistData = await page.evaluate(() => {
        // Nombre de la playlist
        const nameEl = document.querySelector('h1');
        const playlistName = nameEl ? nameEl.textContent.trim() : 'Unknown Playlist';

        // Canciones
        const songs = [];
        const seen = new Set();
        const trackLinks = document.querySelectorAll('a[href*="/track/"]');

        trackLinks.forEach((link) => {
          const href = link.getAttribute('href') || '';
          const trackId = href.split('/track/')[1]?.split('?')[0];
          if (!href) return;
          if (trackId && seen.has(trackId)) return;
          if (trackId) seen.add(trackId);

          // Título
          const title = link.textContent.trim() || 'Unknown';
          if (!title) return;

          // Intentar localizar la fila/row de la canción, pero si falla seguimos con datos mínimos
          let row =
            link.closest('[data-testid="tracklist-row"]') ||
            link.closest('div[role="row"]') ||
            link.parentElement?.parentElement;

          // Artistas
          let artists = 'Unknown Artist';
          let album = 'Unknown Album';
          if (row) {
            const artistLinks = row.querySelectorAll('a[href*="/artist/"]');
            const artistText = Array.from(artistLinks)
              .map((a) => a.textContent.trim())
              .filter(Boolean)
              .join(', ');
            if (artistText) {
              artists = artistText;
            }

            const albumLink = row.querySelector('a[href*="/album/"]');
            if (albumLink) {
              const albumText = albumLink.textContent.trim();
              if (albumText) {
                album = albumText;
              }
            }
          }

          // Imagen
          let imageUrl = '';
          if (row) {
            const img = row.querySelector('img');
            if (img) imageUrl = img.src;
          }

          // Duración
          let duration = 0;
          if (row) {
            const durationEl = row.querySelector('[data-testid="duration"]');
            if (durationEl) {
              const timeStr = durationEl.textContent.trim();
              const parts = timeStr.split(':');
              if (parts.length === 2) {
                duration = parseInt(parts[0]) * 60 + parseInt(parts[1]);
              }
            }
          }

          songs.push({
            id: trackId || href,
            title,
            artist: artists,
            album,
            imageUrl,
            duration,
          });
        });

        return {
          name: playlistName,
          songs,
        };
      });

      return {
        success: true,
        playlist: {
          name: playlistData.name,
          totalTracks: playlistData.songs.length,
        },
        songs: playlistData.songs,
      };
    } catch (error) {
      console.error('Error scraping Spotify:', error);
      throw new Error('Failed to scrape Spotify playlist');
    } finally {
      if (browser) await browser.close();
    }
  }
}

module.exports = new SpotifyService();
