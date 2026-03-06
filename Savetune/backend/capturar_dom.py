#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Script MINIMO para capturar el DOM de Spotify
"""

from selenium import webdriver
from selenium.webdriver.firefox.options import Options
import time

print("Iniciando Firefox...")

options = Options()
options.add_argument('--headless')

try:
    driver = webdriver.Firefox(options=options)
except:
    print("Firefox no funciona, probando Chrome...")
    from selenium.webdriver.chrome.options import Options as ChromeOptions
    chrome_opts = ChromeOptions()
    chrome_opts.add_argument('--headless=new')
    driver = webdriver.Chrome(options=chrome_opts)

print("Cargando Spotify...")
driver.get("https://open.spotify.com/playlist/3Ak7qNCGgxBlcPxKGHvMym")

print("Esperando 8 segundos...")
time.sleep(8)

print("Guardando HTML...")
with open("spotify_dom_raw.html", "w", encoding="utf-8") as f:
    f.write(driver.page_source)

print("Guardando screenshot...")
driver.save_screenshot("spotify_screenshot.png")

driver.quit()

print("\n[OK] Archivos guardados:")
print("  - spotify_dom_raw.html")
print("  - spotify_screenshot.png")
print("\nAbre spotify_dom_raw.html y busca (Ctrl+F):")
print("  'Dracula' o 'Tame Impala' para encontrar donde estan las canciones")
