#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Diagnóstico simple del DOM de Spotify - SIN QUEDARSE COLGADO
Version segura con timeouts cortos
"""

import sys
import os

# Fix encoding Windows
if sys.platform == 'win32':
    os.system('chcp 65001 >nul 2>&1')

print("\n" + "="*80)
print("DIAGNOSTICO SIMPLE DEL DOM DE SPOTIFY")
print("="*80)

# Test 1: Verificar que Selenium funciona
print("\n[1/5] Verificando Selenium...")
try:
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.chrome.options import Options
    print("[OK] Selenium importado correctamente")
except Exception as e:
    print(f"[ERROR] No se pudo importar Selenium: {e}")
    sys.exit(1)

# Test 2: Verificar Firefox (que es el que tienes instalado)
print("\n[2/5] Verificando Firefox...")
try:
    from selenium.webdriver.firefox.options import Options as FirefoxOptions
    
    # Configuración para Firefox
    options = FirefoxOptions()
    options.add_argument('--headless')
    options.add_argument('--width=1920')
    options.add_argument('--height=1080')
    
    print("   Iniciando Firefox (esto puede tardar 5-10 segundos)...")
    
    # Intentar con Firefox primero
    try:
        driver = webdriver.Firefox(options=options)
        print("[OK] Firefox iniciado correctamente")
    except Exception as e1:
        print(f"   Firefox no disponible: {e1}")
        print("   Intentando con Chrome/Chromium...")
        
        # Fallback a Chrome
        from selenium.webdriver.chrome.options import Options as ChromeOptions
        chrome_options = ChromeOptions()
        chrome_options.add_argument('--headless=new')
        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        chrome_options.add_argument('--disable-gpu')
        chrome_options.add_argument('--window-size=1920,1080')
        
        driver = webdriver.Chrome(options=chrome_options)
        print("[OK] Chrome iniciado correctamente")
    
    # Timeout razonable
    try:
        driver.set_page_load_timeout(30)
    except:
        pass
    
except Exception as e:
    print(f"[ERROR] No se pudo iniciar ningun navegador: {e}")
    print("\n[!] Soluciones:")
    print("    1. Para Firefox: pip install selenium")
    print("    2. Verifica que Firefox este instalado")
    print("    3. Actualiza geckodriver si es necesario")
    sys.exit(1)

# Test 3: Cargar página de Spotify
print("\n[3/5] Cargando página de Spotify...")
url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
print(f"   URL: {url[:60]}...")

try:
    print("   Cargando... (timeout 30 segundos)")
    driver.get(url)
    print("[OK] Pagina cargada")
    
except Exception as e:
    print(f"[ERROR] No se pudo cargar la pagina: {e}")
    driver.quit()
    sys.exit(1)

# Test 4: Esperar un poco para que cargue JS
print("\n[4/5] Esperando que cargue el contenido...")
import time
time.sleep(5)
print("[OK] Espera completada")

# Test 5: DIAGNÓSTICO - Buscar lo que el código viejo buscaba
print("\n[5/5] DIAGNOSTICO DEL DOM")
print("="*80)

print("\n--- SELECTORES QUE EL CODIGO VIEJO USABA ---\n")

# 1. Links de tracks
print("1. Links de tracks: a[href*='/track/']")
try:
    track_links = driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
    print(f"   Resultado: {len(track_links)} elementos encontrados")
    if track_links:
        print(f"   [OK] Este selector FUNCIONA")
        # Mostrar ejemplo
        ejemplo = track_links[0]
        print(f"   Ejemplo href: {ejemplo.get_attribute('href')[:60]}...")
        print(f"   Ejemplo text: {ejemplo.text[:40]}...")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# 2. Filas con data-testid="tracklist-row"
print("\n2. Filas: div[data-testid='tracklist-row']")
try:
    rows_testid = driver.find_elements(By.CSS_SELECTOR, 'div[data-testid="tracklist-row"]')
    print(f"   Resultado: {len(rows_testid)} elementos encontrados")
    if rows_testid:
        print(f"   [OK] Este selector FUNCIONA")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# 3. Filas con role="row"
print("\n3. Filas alternativo: div[role='row']")
try:
    rows_role = driver.find_elements(By.CSS_SELECTOR, 'div[role="row"]')
    print(f"   Resultado: {len(rows_role)} elementos encontrados")
    if rows_role:
        print(f"   [OK] Este selector FUNCIONA")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# 4. Track names
print("\n4. Nombres de tracks: [data-testid='track-name']")
try:
    track_names = driver.find_elements(By.CSS_SELECTOR, '[data-testid="track-name"]')
    print(f"   Resultado: {len(track_names)} elementos encontrados")
    if track_names:
        print(f"   [OK] Este selector FUNCIONA")
        print(f"   Ejemplo: {track_names[0].text[:40]}...")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# 5. Artist links
print("\n5. Links de artistas: a[href*='/artist/']")
try:
    artist_links = driver.find_elements(By.CSS_SELECTOR, 'a[href*="/artist/"]')
    print(f"   Resultado: {len(artist_links)} elementos encontrados")
    if artist_links:
        print(f"   [OK] Este selector FUNCIONA")
        print(f"   Ejemplo: {artist_links[0].text[:40]}...")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# 6. Album links
print("\n6. Links de albums: a[href*='/album/']")
try:
    album_links = driver.find_elements(By.CSS_SELECTOR, 'a[href*="/album/"]')
    print(f"   Resultado: {len(album_links)} elementos encontrados")
    if album_links:
        print(f"   [OK] Este selector FUNCIONA")
        print(f"   Ejemplo: {album_links[0].text[:40]}...")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# 7. Duration
print("\n7. Duraciones: [data-testid='duration']")
try:
    durations = driver.find_elements(By.CSS_SELECTOR, '[data-testid="duration"]')
    print(f"   Resultado: {len(durations)} elementos encontrados")
    if durations:
        print(f"   [OK] Este selector FUNCIONA")
        print(f"   Ejemplo: {durations[0].text}")
    else:
        print(f"   [X] Este selector NO funciona (0 elementos)")
except Exception as e:
    print(f"   [ERROR] {e}")

# ANÁLISIS ADICIONAL: Buscar alternativas
print("\n\n--- BUSCANDO SELECTORES ALTERNATIVOS ---\n")

# Buscar todos los data-testid
print("8. Buscando TODOS los data-testid disponibles...")
try:
    script = """
    var elements = document.querySelectorAll('[data-testid]');
    var testids = new Set();
    elements.forEach(el => {
        var tid = el.getAttribute('data-testid');
        if (tid && (tid.includes('track') || tid.includes('row') || tid.includes('list'))) {
            testids.add(tid);
        }
    });
    return Array.from(testids).slice(0, 20);
    """
    testids = driver.execute_script(script)
    print(f"   Encontrados {len(testids)} data-testid relacionados con tracks/rows:")
    for tid in testids:
        print(f"      - {tid}")
except Exception as e:
    print(f"   [ERROR] {e}")

# Buscar clases comunes
print("\n9. Buscando clases CSS relacionadas con tracks...")
try:
    script = """
    var elements = document.querySelectorAll('[class*="track"], [class*="Track"], [class*="row"], [class*="Row"]');
    var classes = new Set();
    for (var i = 0; i < Math.min(50, elements.length); i++) {
        var el = elements[i];
        var classList = el.className.split(' ');
        classList.forEach(cls => {
            if (cls && (cls.toLowerCase().includes('track') || cls.toLowerCase().includes('row'))) {
                classes.add(cls);
            }
        });
    }
    return Array.from(classes).slice(0, 15);
    """
    classes = driver.execute_script(script)
    print(f"   Encontradas {len(classes)} clases relevantes:")
    for cls in classes:
        print(f"      - {cls}")
except Exception as e:
    print(f"   [ERROR] {e}")

# HTML de una fila de ejemplo
print("\n10. HTML de ejemplo de una fila/track...")
try:
    if track_links:
        ejemplo = track_links[0]
        # Buscar el padre
        try:
            parent = ejemplo.find_element(By.XPATH, './ancestor::div[@role="row"]')
        except:
            try:
                parent = ejemplo.find_element(By.XPATH, './ancestor::div[@data-testid]')
            except:
                parent = ejemplo
        
        html = parent.get_attribute('outerHTML')[:500]
        print("   Primeros 500 caracteres:")
        print("-"*80)
        print(html)
        print("-"*80)
except Exception as e:
    print(f"   [ERROR] {e}")

# Guardar página completa para inspección manual
print("\n11. Guardando HTML completo para inspeccion manual...")
try:
    page_source = driver.page_source
    filename = "spotify_dom_snapshot.html"
    with open(filename, 'w', encoding='utf-8') as f:
        f.write(page_source)
    print(f"   [OK] Guardado en: {filename}")
    print(f"   [i] Puedes abrir este archivo y buscar manualmente los selectores")
except Exception as e:
    print(f"   [ERROR] {e}")

# Cerrar navegador
print("\n[*] Cerrando navegador...")
driver.quit()

# RESUMEN FINAL
print("\n" + "="*80)
print("RESUMEN DEL DIAGNOSTICO")
print("="*80)

print("\n[i] Que hacer ahora:")
print("    1. Revisa los resultados arriba")
print("    2. Abre el archivo: spotify_dom_snapshot.html")
print("    3. Busca manualmente (Ctrl+F) por:")
print("       - 'track' (encontrar canciones)")
print("       - 'artist' (encontrar artistas)")
print("       - 'album' (encontrar albums)")
print("    4. Identificar los nuevos selectores que funcionan")
print("")
print("[i] Una vez identificados los selectores correctos,")
print("    te dire como actualizar el codigo del scraper")

print("\n" + "="*80)
