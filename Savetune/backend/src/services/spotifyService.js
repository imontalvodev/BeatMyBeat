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

          // Intentar localizar la fila/row de la canción
          let row =
            link.closest('[data-testid="tracklist-row"]') ||
            link.closest('div[role="row"]') ||
            link.parentElement?.parentElement;

          // Título: priorizar elementos específicos dentro de la fila
          let title = 'Unknown';
          if (row) {
            const titleEl =
              row.querySelector('[data-testid="track-name"]') ||
              row.querySelector('a[href*="/track/"]') ||
              row.querySelector('span');
            if (titleEl && titleEl.textContent.trim()) {
              title = titleEl.textContent.trim();
            }
          }
          if (title === 'Unknown') {
            const fromLink = link.textContent.trim();
            if (fromLink) title = fromLink;
          }
          if (!title || title === 'Unknown') return;

          // Artistas
          let artists = 'Unknown Artist';
          if (row) {
            const artistLinks =
              row.querySelectorAll('a[href*="/artist/"]') ||
              row.querySelectorAll('span a');
            const artistText = Array.from(artistLinks || [])
              .map((a) => a.textContent.trim())
              .filter(Boolean)
              .join(', ');
            if (artistText) {
              artists = artistText;
            }
          }

          // Álbum
          let album = 'Unknown Album';
          if (row) {
            const albumLink =
              row.querySelector('a[href*="/album/"]') ||
              row.querySelector('[data-testid="album-link"]');
            if (albumLink && albumLink.textContent.trim()) {
              album = albumLink.textContent.trim();
            }
          }

          // Imagen
          let imageUrl = '';
          if (row) {
            const img =
              row.querySelector('img') || document.querySelector('figure img');
            if (img) imageUrl = img.src;
          }

          // Duración
          let duration = 0;
          if (row) {
            const durationEl =
              row.querySelector('[data-testid="duration"]') ||
              row.querySelector('div[aria-colindex="5"] span');
            if (durationEl && durationEl.textContent.trim()) {
              const timeStr = durationEl.textContent.trim();
              const parts = timeStr.split(':');
              if (parts.length === 2) {
                const mins = parseInt(parts[0], 10);
                const secs = parseInt(parts[1], 10);
                if (!Number.isNaN(mins) && !Number.isNaN(secs)) {
                  duration = mins * 60 + secs;
                }
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
