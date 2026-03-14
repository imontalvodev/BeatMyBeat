#!/usr/bin/env python3
"""
Spotify Playlist Scraper - VERSIÓN MEJORADA
Optimizado para playlists grandes (100-500+ canciones)
✅ Scroll mejorado que garantiza capturar TODAS las canciones
✅ Enriquecimiento con fuentes externas (oEmbed, iTunes)
"""

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.firefox.options import Options as FirefoxOptions
from selenium.webdriver.common.keys import Keys
import time
import re
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.parse import quote as urlquote
from urllib.request import urlopen, Request


class SpotifyPlaylistScraper:
    """
    Clase para extraer información de playlists de Spotify mediante web scraping
    """

    def __init__(self, headless=True):
        self.headless = headless
        self.driver = None

    def iniciar_driver(self):
        """Configura e inicia el driver de Firefox"""
        firefox_options = FirefoxOptions()

        if self.headless:
            firefox_options.add_argument('--headless')

        firefox_options.add_argument('--width=1920')
        firefox_options.add_argument('--height=1080')
        
        # User agent
        firefox_options.set_preference('general.useragent.override', 
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0')

        try:
            self.driver = webdriver.Firefox(options=firefox_options)
            print("✅ Firefox iniciado correctamente")
        except Exception as e:
            print(f"❌ Error al iniciar Firefox: {e}")
            raise

    def cerrar_driver(self):
        """Cierra el navegador"""
        if self.driver:
            self.driver.quit()
            print("✅ Navegador cerrado")

    def parsear_duracion(self, duracion_str):
        """Convierte string de duración (mm:ss) a segundos"""
        try:
            partes = duracion_str.strip().split(':')
            if len(partes) == 2:
                minutos = int(partes[0])
                segundos = int(partes[1])
                return minutos * 60 + segundos
        except:
            pass
        return 0

    def formatear_duracion(self, segundos):
        """Convierte segundos a formato mm:ss"""
        minutos = segundos // 60
        segs = segundos % 60
        return f"{minutos}:{segs:02d}"

    def _obtener_artista_desde_oembed(self, track_id: str) -> str:
        """
        Usa el oEmbed público de Spotify para obtener el artista de un track.
        No requiere autenticación.
        """
        try:
            track_url = f"https://open.spotify.com/track/{track_id}"
            oembed_url = f"https://open.spotify.com/oembed?url={urlquote(track_url, safe=':/?=&')}"
            with urlopen(oembed_url, timeout=5) as resp:
                data = json.loads(resp.read().decode("utf-8") or "{}")
            artist = data.get("author_name", "") or ""
            return artist.strip()
        except Exception:
            return ""

    def _obtener_album_desde_http(self, track_id: str) -> str:
        """
        Fallback sin Selenium: descarga la página del track, extrae el album_id
        desde las meta tags y luego consulta el oEmbed del álbum para obtener
        el nombre del álbum.
        """
        try:
            # 1) Descargar HTML del track
            track_url = f"https://open.spotify.com/track/{track_id}"
            req = Request(
                track_url,
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
                },
            )
            with urlopen(req, timeout=7) as resp:
                html = resp.read().decode("utf-8", errors="ignore")

            # 2) Buscar meta music:album -> spotify:album:<ID>
            m = re.search(
                r'property="music:album"\s+content="spotify:album:([a-zA-Z0-9]+)"',
                html,
            )
            if not m:
                return ""
            album_id = m.group(1)

            # 3) Consultar oEmbed del álbum para obtener el nombre
            album_url = f"https://open.spotify.com/album/{album_id}"
            oembed_url = f"https://open.spotify.com/oembed?url={urlquote(album_url, safe=':/?=&')}"
            with urlopen(oembed_url, timeout=7) as resp:
                data = json.loads(resp.read().decode("utf-8") or "{}")
            album_title = data.get("title", "") or ""
            return album_title.strip()
        except Exception:
            return ""

    def _buscar_en_itunes(self, titulo: str, artista: str | None = None) -> tuple[str, str]:
        """
        Usa la iTunes Search API (pública) para intentar obtener artista y álbum
        basados en el título (y opcionalmente artista).
        Devuelve (artist, album), pudiendo ser cadenas vacías si no se encuentra nada.
        """
        try:
            query = titulo
            if artista and artista.lower() != "unknown artist":
                query = f"{titulo} {artista}"

            params = {
                "term": query,
                "media": "music",
                "limit": 1,
            }
            url = "https://itunes.apple.com/search?" + "&".join(
                f"{urlquote(str(k))}={urlquote(str(v))}" for k, v in params.items()
            )
            req = Request(
                url,
                headers={
                    "User-Agent": "SaveTune/1.0 (https://github.com/)",
                    "Accept": "application/json",
                },
            )
            with urlopen(req, timeout=7) as resp:
                data = json.loads(resp.read().decode("utf-8") or "{}")

            results = data.get("results") or []
            if not results:
                return "", ""

            r = results[0]
            artist_name = (r.get("artistName") or "").strip()
            album_name = (r.get("collectionName") or "").strip()
            return artist_name, album_name
        except Exception:
            return "", ""

    def extraer_playlist_id(self, url):
        """Extrae el ID de la playlist desde una URL"""
        if "playlist/" in url:
            match = re.search(r'playlist/([a-zA-Z0-9]+)', url)
            if match:
                return match.group(1)
        return url

    def _scroll_completo_mejorado(self):
        """
        🔥 SCROLL MEJORADO - Garantiza capturar TODAS las canciones
        Optimizado para playlists de 100-500+ canciones
        """
        print("🔄 Iniciando scroll inteligente optimizado...")
        
        inicio = time.time()
        max_tiempo = 600  # 10 minutos máximo (antes 5)
        intentos_sin_cambio = 0
        max_intentos = 25  # MUY aumentado (antes 10)
        ultimo_conteo = 0
        iteracion = 0
        
        # Script de scroll MULTI-ESTRATEGIA
        scroll_agresivo = """
        // ESTRATEGIA 1: Scroll en window principal
        window.scrollTo(0, document.body.scrollHeight);
        
        // ESTRATEGIA 2: Encontrar TODOS los contenedores scrollables
        var scrollables = document.querySelectorAll(`
            div[data-testid="playlist-tracklist"],
            div[class*="main-view-container"],
            main[role="main"],
            div[role="presentation"],
            div[class*="scroll"],
            div[style*="overflow"]
        `);
        
        var scrollCount = 0;
        scrollables.forEach(function(el) {
            try {
                var antes = el.scrollTop;
                el.scrollTop = el.scrollHeight;
                if (el.scrollTop > antes) scrollCount++;
            } catch(e) {}
        });
        
        // ESTRATEGIA 3: ScrollIntoView en último track
        try {
            var tracks = document.querySelectorAll('a[href*="/track/"]');
            if (tracks.length > 0) {
                tracks[tracks.length - 1].scrollIntoView({behavior: 'instant', block: 'end'});
            }
        } catch(e) {}
        
        // ESTRATEGIA 4: Buscar y hacer scroll en el contenedor específico de la tracklist
        try {
            var tracklist = document.querySelector('[data-testid="playlist-tracklist"]');
            if (!tracklist) {
                tracklist = document.querySelector('div[role="grid"]');
            }
            if (tracklist) {
                tracklist.scrollTop = tracklist.scrollHeight;
            }
        } catch(e) {}
        
        return scrollCount;
        """
        
        # MÉTODO ALTERNATIVO: Usar teclas
        def scroll_con_teclas():
            try:
                body = self.driver.find_element(By.TAG_NAME, 'body')
                for _ in range(5):
                    body.send_keys(Keys.PAGE_DOWN)
                    time.sleep(0.3)
                body.send_keys(Keys.END)
            except:
                pass
        
        while (time.time() - inicio) < max_tiempo:
            iteracion += 1
            
            # FASE 1: Scroll JavaScript agresivo
            try:
                self.driver.execute_script(scroll_agresivo)
            except Exception as e:
                if iteracion % 20 == 0:
                    print(f"   ⚠️ Error scroll JS: {str(e)[:50]}")
            
            # FASE 2: Scroll con teclas (alternativa)
            if iteracion % 3 == 0:
                scroll_con_teclas()
            
            # Esperar carga - MÁS TIEMPO
            time.sleep(3.0)  # Aumentado de 2.0 a 3.0
            
            # Contar tracks
            try:
                links = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
                conteo_actual = len(links)
            except:
                conteo_actual = 0
            
            # Mostrar progreso - MÁS FRECUENTE
            if iteracion % 3 == 0 or conteo_actual != ultimo_conteo:
                print(f"   📊 Iteración {iteracion} - Tracks cargados: {conteo_actual}")
            
            # Detectar progreso
            if conteo_actual > ultimo_conteo:
                ganancia = conteo_actual - ultimo_conteo
                print(f"   ✅ ¡Progreso! +{ganancia} tracks (total: {conteo_actual})")
                intentos_sin_cambio = 0
                ultimo_conteo = conteo_actual
            else:
                intentos_sin_cambio += 1
                
                # Si llevamos mucho sin cambios, scroll EXTRA agresivo
                if intentos_sin_cambio == 3:  # Más temprano (antes 4)
                    print(f"   🔁 Activando modo EXTRA agresivo...")
                    for _ in range(10):  # Más intentos (antes 5)
                        self.driver.execute_script(scroll_agresivo)
                        scroll_con_teclas()
                        time.sleep(1.5)  # Más tiempo
                
                # Timeout
                if intentos_sin_cambio >= max_intentos:
                    print(f"   ✅ Sin más contenido ({intentos_sin_cambio} intentos)")
                    break
        
        # Scroll final de confirmación - MÁS AGRESIVO
        print("🔄 Scroll final de confirmación...")
        for i in range(15):  # Más intentos (antes 8)
            self.driver.execute_script(scroll_agresivo)
            scroll_con_teclas()
            time.sleep(2.0)  # Más tiempo (antes 1.0)
        
        # Conteo final
        links_finales = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
        total_final = len(links_finales)
        
        tiempo_total = time.time() - inicio
        print(f"✅ Scroll completado en {tiempo_total:.1f}s")
        print(f"📊 Total de enlaces de tracks cargados: {total_final}")
        
        return total_final

    def obtener_canciones_playlist(self, url):
        """🔥 CAPTURA INCREMENTAL - Extrae canciones MIENTRAS hace scroll"""
        if not self.driver:
            self.iniciar_driver()

        try:
            print(f"\n🔍 Accediendo a la playlist...")
            self.driver.get(url)
            time.sleep(5)

            # Obtener nombre de playlist
            nombre_playlist = "Unknown Playlist"
            try:
                nombre_element = self.driver.find_element(By.TAG_NAME, 'h1')
                nombre_playlist = nombre_element.text.strip()
            except:
                pass

            # Diccionario para almacenar canciones únicas {track_id: info_dict}
            todas_las_canciones = {}
            
            print("🔄 Iniciando captura incremental durante scroll LENTO...")
            
            inicio = time.time()
            intentos_sin_cambio = 0
            ultimo_conteo = 0
            iteracion = 0
            
            # Localizar el contenedor scrollable
            scroll_container = None
            try:
                scroll_container = self.driver.find_element(
                    By.XPATH, 
                    "//div[@data-testid='playlist-tracklist']//ancestor::div[contains(@class, 'os-viewport')]"
                )
                print("✅ Contenedor scroll detectado")
            except:
                print("ℹ️ Usando scroll en body")

            # Detectar recomendaciones una sola vez
            recomendaciones_y = None
            try:
                recomendaciones_elems = self.driver.find_elements(
                    By.XPATH,
                    "//*[contains(translate(text(), 'RECOMENDACIONES', 'recomendaciones'), 'recomendaciones') or contains(translate(text(), 'RECOMMENDED', 'recommended'), 'recommended')]"
                )
                for elem in recomendaciones_elems:
                    try:
                        text = elem.text.strip().lower()
                        if 'recomenda' in text or 'recommend' in text:
                            elem_y = elem.location.get('y', 0)
                            if elem_y > 0:
                                recomendaciones_y = elem_y
                                print(f"📍 Sección 'Recomendaciones' detectada en Y={recomendaciones_y}")
                                break
                    except:
                        continue
            except:
                pass

            # 🔥 SCROLL LENTO + CAPTURA INCREMENTAL
            while (time.time() - inicio) < 600:  # Max 10 min
                iteracion += 1
                
                # --- 1. CAPTURAR LO VISIBLE AHORA ---
                track_elements = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
                
                for link in track_elements:
                    try:
                        href = link.get_attribute('href')
                        if not href:
                            continue
                            
                        track_id_match = re.search(r'/track/([a-zA-Z0-9]+)', href)
                        if not track_id_match:
                            continue
                            
                        track_id = track_id_match.group(1)
                        
                        # Skip si ya tenemos esta canción
                        if track_id in todas_las_canciones:
                            continue
                        
                        # Encontrar la fila
                        row = None
                        try:
                            row = link.find_element(By.XPATH, './ancestor::div[@data-testid="tracklist-row"]')
                        except:
                            try:
                                row = link.find_element(By.XPATH, './ancestor::div[@role="row"]')
                            except:
                                row = link
                        
                        # Filtro de recomendaciones
                        if recomendaciones_y is not None:
                            try:
                                row_y = row.location.get('y', 0)
                                if row_y >= recomendaciones_y:
                                    continue
                            except:
                                pass
                        
                        # Título
                        titulo = link.text.strip()
                        if not titulo:
                            if row != link:
                                try:
                                    titulo_element = row.find_element(By.CSS_SELECTOR, '[data-testid="internal-track-link"]')
                                    titulo = titulo_element.text.strip()
                                except:
                                    pass
                        
                        if not titulo:
                            continue

                        # Artistas
                        artistas = "Unknown Artist"
                        try:
                            artist_links = row.find_elements(By.XPATH, './/a[contains(@href, "/artist/")]')
                            artist_names = [a.text.strip() for a in artist_links[:3] if a.text and a.text.strip()]
                            if artist_names:
                                artistas = ", ".join(artist_names)
                        except:
                            pass

                        # Álbum
                        album = "Unknown Album"
                        try:
                            album_link_el = row.find_element(By.XPATH, './/a[contains(@href, "/album/")]')
                            album_text = album_link_el.text.strip()
                            if album_text:
                                album = album_text
                        except:
                            pass

                        # Duración
                        duracion_segundos = 0
                        duracion_str = ""
                        try:
                            duracion_element = row.find_element(By.CSS_SELECTOR, '[aria-colindex="5"] div[data-encore-id="text"]')
                            duracion_str = duracion_element.text.strip()
                            if duracion_str:
                                duracion_segundos = self.parsear_duracion(duracion_str)
                        except:
                            pass

                        # Imagen
                        imagen_url = ""
                        try:
                            img_element = row.find_element(By.TAG_NAME, 'img')
                            imagen_url = img_element.get_attribute('src') or ""
                        except:
                            pass

                        todas_las_canciones[track_id] = {
                            'id': track_id,
                            'titulo': titulo,
                            'artistas': artistas,
                            'album': album,
                            'duracion_segundos': duracion_segundos,
                            'duracion': self.formatear_duracion(duracion_segundos) if duracion_segundos > 0 else duracion_str,
                            'spotify_url': href,
                            'imagen_url': imagen_url
                        }
                    except:
                        continue

                # --- 2. HACER SCROLL MUY CORTO Y LENTO ---
                if scroll_container:
                    # Scroll en el contenedor específico - 300px por salto
                    self.driver.execute_script("arguments[0].scrollTop += 300;", scroll_container)
                else:
                    # Fallback: usar tecla PAGE_DOWN
                    try:
                        body = self.driver.find_element(By.TAG_NAME, 'body')
                        body.send_keys(Keys.PAGE_DOWN)
                    except:
                        self.driver.execute_script("window.scrollBy(0, 300);")
                
                # ESPERAR MÁS TIEMPO para que Spotify cargue
                time.sleep(2.5)

                # --- 3. CONTROL DE PARADA ---
                conteo_actual = len(todas_las_canciones)
                
                if conteo_actual > ultimo_conteo:
                    ganancia = conteo_actual - ultimo_conteo
                    if iteracion % 3 == 0 or ganancia > 0:
                        print(f"   📊 Iteración {iteracion} - ✅ Capturadas: {conteo_actual} (+{ganancia})")
                    ultimo_conteo = conteo_actual
                    intentos_sin_cambio = 0
                else:
                    intentos_sin_cambio += 1
                    if iteracion % 5 == 0:
                        print(f"   ⏳ Iteración {iteracion} - Sin cambios ({intentos_sin_cambio}/20)")
                
                # Paciencia: 20 intentos sin cambio
                if intentos_sin_cambio >= 20:
                    print(f"   ✅ Completado: Sin más canciones tras 20 intentos")
                    break

            # --- SCROLL FINAL EXTRA ---
            print("🔄 Scroll final de confirmación...")
            for i in range(10):
                if scroll_container:
                    self.driver.execute_script("arguments[0].scrollTop += 500;", scroll_container)
                else:
                    try:
                        body = self.driver.find_element(By.TAG_NAME, 'body')
                        body.send_keys(Keys.END)
                    except:
                        self.driver.execute_script("window.scrollBy(0, 1000);")
                time.sleep(2.0)
                
                # Capturar últimas canciones
                track_elements = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
                for link in track_elements:
                    try:
                        href = link.get_attribute('href')
                        if not href:
                            continue
                        track_id_match = re.search(r'/track/([a-zA-Z0-9]+)', href)
                        if not track_id_match:
                            continue
                        track_id = track_id_match.group(1)
                        if track_id in todas_las_canciones:
                            continue
                        
                        # Proceso rápido para últimas canciones
                        row = None
                        try:
                            row = link.find_element(By.XPATH, './ancestor::div[@data-testid="tracklist-row"]')
                        except:
                            row = link
                        
                        if recomendaciones_y is not None:
                            try:
                                row_y = row.location.get('y', 0)
                                if row_y >= recomendaciones_y:
                                    continue
                            except:
                                pass
                        
                        titulo = link.text.strip()
                        if not titulo:
                            continue
                        
                        todas_las_canciones[track_id] = {
                            'id': track_id,
                            'titulo': titulo,
                            'artistas': "Unknown Artist",
                            'album': "Unknown Album",
                            'duracion_segundos': 0,
                            'duracion': "",
                            'spotify_url': href,
                            'imagen_url': ""
                        }
                    except:
                        continue

            # Convertir diccionario a lista
            canciones = list(todas_las_canciones.values())
            
            tiempo_total = time.time() - inicio
            print(f"\n✅ Captura completada en {tiempo_total:.1f}s")
            print(f"📊 Total canciones capturadas: {len(canciones)}")

            # FASE 2: ENRIQUECIMIENTO EXTERNO (solo para Unknown)
            if canciones:
                print("🔁 Enriqueciendo datos faltantes con fuentes externas...")

                def enriquecer(cancion: dict) -> None:
                    try:
                        track_id = cancion.get("id") or ""
                        titulo = cancion.get("titulo") or ""
                        artistas = cancion.get("artistas") or "Unknown Artist"
                        album = cancion.get("album") or "Unknown Album"

                        if artistas == "Unknown Artist":
                            artista_oembed = self._obtener_artista_desde_oembed(track_id)
                            if artista_oembed:
                                artistas = artista_oembed
                            else:
                                it_artist, _ = self._buscar_en_itunes(titulo, None)
                                if it_artist:
                                    artistas = it_artist

                        if album == "Unknown Album":
                            album_http = self._obtener_album_desde_http(track_id)
                            if album_http:
                                album = album_http
                            else:
                                _, it_album = self._buscar_en_itunes(titulo, artistas)
                                if it_album:
                                    album = it_album

                        cancion["artistas"] = artistas
                        cancion["album"] = album
                    except Exception:
                        pass

                max_workers = min(8, len(canciones))
                with ThreadPoolExecutor(max_workers=max_workers) as executor:
                    futures = [executor.submit(enriquecer, c) for c in canciones]
                    for f in as_completed(futures):
                        _ = f.result()

            print(f"✅ Enriquecimiento completado")

            return {
                'success': True,
                'playlist': {
                    'nombre': nombre_playlist,
                    'total_canciones': len(canciones),
                    'url': url
                },
                'canciones': canciones
            }

        except Exception as e:
            print(f"\n❌ Error al extraer playlist: {e}")
            import traceback
            traceback.print_exc()
            return {
                'success': False,
                'error': str(e)
            }

    def mostrar_canciones(self, resultado):
        """Muestra las canciones en formato de tabla"""
        if not resultado['success']:
            print(f"❌ Error: {resultado.get('error', 'Unknown error')}")
            return

        playlist = resultado['playlist']
        canciones = resultado['canciones']

        print("\n" + "=" * 120)
        print(f"📀 Playlist: {playlist['nombre']}")
        print(f"🎵 Total de canciones: {playlist['total_canciones']}")
        print(f"🔗 URL: {playlist['url']}")
        print("=" * 120)

        if not canciones:
            print("❌ No se encontraron canciones")
            return

        print(f"\n{'#':<4} {'Título':<40} {'Artista(s)':<30} {'Álbum':<30} {'Duración':<10}")
        print("=" * 120)

        for idx, cancion in enumerate(canciones, 1):
            titulo = cancion['titulo'][:37] + "..." if len(cancion['titulo']) > 40 else cancion['titulo']
            artistas = cancion['artistas'][:27] + "..." if len(cancion['artistas']) > 30 else cancion['artistas']
            album = cancion['album'][:27] + "..." if len(cancion['album']) > 30 else cancion['album']

            print(f"{idx:<4} {titulo:<40} {artistas:<30} {album:<30} {cancion['duracion']:<10}")

    def exportar_a_csv(self, resultado, nombre_archivo="playlist_spotify.csv"):
        """Exporta las canciones a un archivo CSV"""
        import csv

        if not resultado['success']:
            print(f"❌ Error: {resultado.get('error', 'Unknown error')}")
            return

        canciones = resultado['canciones']

        if not canciones:
            print("❌ No hay canciones para exportar")
            return

        try:
            with open(nombre_archivo, 'w', newline='', encoding='utf-8') as csvfile:
                campos = ['Número', 'ID', 'Título', 'Artista(s)', 'Álbum', 'Duración', 'Spotify URL']
                writer = csv.DictWriter(csvfile, fieldnames=campos)

                writer.writeheader()
                for idx, cancion in enumerate(canciones, 1):
                    writer.writerow({
                        'Número': idx,
                        'ID': cancion['id'],
                        'Título': cancion['titulo'],
                        'Artista(s)': cancion['artistas'],
                        'Álbum': cancion['album'],
                        'Duración': cancion['duracion'],
                        'Spotify URL': cancion['spotify_url']
                    })

            print(f"\n✅ Playlist exportada a: {nombre_archivo}")

        except Exception as e:
            print(f"❌ Error al exportar: {e}")


def main():
    """Función principal - interfaz de usuario"""
    print("\n" + "=" * 120)
    print("🎵  SPOTIFY PLAYLIST SCRAPER - VERSIÓN MEJORADA  🎵")
    print("✅ Optimizado para playlists grandes (100-500+ canciones)")
    print("=" * 120)

    scraper = SpotifyPlaylistScraper(headless=True)

    try:
        while True:
            print("\nOpciones:")
            print("1. Leer playlist")
            print("2. Salir")

            opcion = input("\nElige una opción (1-2): ").strip()

            if opcion == "1":
                playlist_url = input("\n🔗 Ingresa la URL de la playlist de Spotify: ").strip()

                if playlist_url:
                    resultado = scraper.obtener_canciones_playlist(playlist_url)

                    if resultado['success']:
                        scraper.mostrar_canciones(resultado)

                        exportar = input("\n¿Exportar a CSV? (s/n): ").strip().lower()
                        if exportar == 's':
                            nombre_csv = input("Nombre del archivo (Enter='playlist_spotify.csv'): ").strip()
                            if not nombre_csv:
                                nombre_csv = "playlist_spotify.csv"
                            elif not nombre_csv.endswith('.csv'):
                                nombre_csv += '.csv'

                            scraper.exportar_a_csv(resultado, nombre_csv)
                else:
                    print("❌ Por favor ingresa una URL válida")

            elif opcion == "2":
                print("\n👋 ¡Hasta luego!")
                break
            else:
                print("❌ Opción no válida")

    except KeyboardInterrupt:
        print("\n\n👋 Programa interrumpido")
    except Exception as e:
        print(f"\n❌ Error: {e}")
    finally:
        scraper.cerrar_driver()


if __name__ == "__main__":
    main()