#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test rápido del scraper con playlist grande
"""

from services.spotify import SpotifyPlaylistScraper

url = "https://open.spotify.com/playlist/14Q15MLolFRLpIF2spUn2w"

print("\nIniciando test con playlist de 126 canciones...")
print(f"URL: {url}\n")

scraper = SpotifyPlaylistScraper(headless=True)
resultado = scraper.obtener_canciones_playlist(url)
scraper.cerrar_driver()

if resultado['success']:
    canciones = resultado.get('canciones', [])
    print(f"\n{'='*60}")
    print(f"RESULTADO FINAL:")
    print(f"{'='*60}")
    print(f"Canciones extraidas: {len(canciones)} / 126")
    print(f"Porcentaje: {len(canciones)/126*100:.1f}%")
    
    if len(canciones) >= 120:
        print("\n✅ EXCELENTE! Casi todas las canciones")
    elif len(canciones) >= 100:
        print("\n✅ BIEN! Mayoría de canciones obtenidas")
    elif len(canciones) >= 80:
        print("\n⚠️ REGULAR: Falta mejorar el scroll")
    else:
        print("\n❌ INSUFICIENTE: El scroll no funciona bien")
    
    print(f"\n{'='*60}")
else:
    print(f"\n❌ ERROR: {resultado.get('error')}")
