// Placeholder for URL and input validators.

function validateSpotifyPlaylistUrl(url) {
  return typeof url === 'string' && url.includes('spotify.com/playlist/');
}

module.exports = {
  validateSpotifyPlaylistUrl,
};
