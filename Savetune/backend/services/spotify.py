#!/usr/bin/env python3
"""
Spotify Playlist Scraper - VERSIÓN CORREGIDA
Carga TODAS las canciones con scroll hasta el final de la página
"""

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
import time
import re


class SpotifyPlaylistScraper:
    """
    Clase para extraer información de playlists de Spotify mediante web scraping
    """

    def __init__(self, headless=True):
        self.headless = headless
        self.driver = None

    def iniciar_driver(self):
        """Configura e inicia el driver de Chrome"""
        chrome_options = Options()

        if self.headless:
            chrome_options.add_argument('--headless=new')

        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-setuid-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        chrome_options.add_argument('--disable-gpu')
        chrome_options.add_argument('--window-size=1920,1080')
        chrome_options.add_argument(
            'user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36')

        try:
            self.driver = webdriver.Chrome(options=chrome_options)
            print("✅ Chrome iniciado correctamente")
        except Exception as e:
            print(f"❌ Error al iniciar Chrome: {e}")
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

    def extraer_playlist_id(self, url):
        """Extrae el ID de la playlist desde una URL"""
        if "playlist/" in url:
            match = re.search(r'playlist/([a-zA-Z0-9]+)', url)
            if match:
                return match.group(1)
        return url

    def obtener_canciones_playlist(self, url):
        """Extrae todas las canciones de una playlist de Spotify"""
        if not self.driver:
            self.iniciar_driver()

        try:
            print(f"\n🔍 Accediendo a la playlist...")
            self.driver.get(url)

            print("⏳ Esperando que cargue la página...")
            time.sleep(5)

            try:
                WebDriverWait(self.driver, 15).until(
                    EC.presence_of_element_located((By.CSS_SELECTOR, 'a[href*="/track/"]'))
                )
            except:
                print("⚠️ Tardando más de lo esperado...")

            # SCROLL ESTRATEGIA MEJORADA: Scroll hasta el FINAL de la página
            print("📜 Haciendo scroll hasta el final de la página...")

            last_height = self.driver.execute_script("return document.body.scrollHeight")
            no_change_count = 0
            scroll_count = 0

            while scroll_count < 100:  # Máximo 100 scrolls
                # Scroll al final de la página
                self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                time.sleep(1.5)  # Tiempo para que cargue contenido

                # Obtener nueva altura
                new_height = self.driver.execute_script("return document.body.scrollHeight")

                # Contar ENLACES (más confiable que filas)
                links = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')

                if scroll_count % 5 == 0:
                    print(f"   Scroll {scroll_count + 1} - Altura: {new_height} - Enlaces: {len(links)}")

                if new_height == last_height:
                    no_change_count += 1
                    if no_change_count >= 3:
                        print(f"✅ Llegamos al final (altura no cambia)")
                        break
                else:
                    no_change_count = 0
                    last_height = new_height

                scroll_count += 1

            # Espera adicional para renderizado
            time.sleep(3)

            # Contar enlaces finales
            final_links = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
            print(f"✅ Scroll completado - Total de enlaces cargados: {len(final_links)}")

            # DETECTAR POSICIÓN DE RECOMENDACIONES
            print("\n📊 Extrayendo información...")

            recomendaciones_y = None
            try:
                # Buscar texto "Recomendaciones" en cualquier elemento
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
            except Exception as e:
                print(f"ℹ️ No se detectó sección de recomendaciones")

            # Nombre de la playlist
            nombre_playlist = "Unknown Playlist"
            try:
                nombre_element = self.driver.find_element(By.TAG_NAME, 'h1')
                nombre_playlist = nombre_element.text.strip()
            except:
                pass

            # EXTRAER CANCIONES
            canciones = []
            track_ids_vistos = set()

            track_links = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
            print(f"🎵 Procesando {len(track_links)} enlaces de canciones...")

            canciones_omitidas_por_recomendaciones = 0
            canciones_procesadas = 0

            for idx, link in enumerate(track_links):
                try:
                    # Mostrar progreso cada 10 canciones
                    if idx % 10 == 0 and idx > 0:
                        print(f"   Procesadas {idx}/{len(track_links)} canciones...")

                    href = link.get_attribute('href')
                    if not href:
                        continue

                    # Extraer track ID
                    track_id_match = re.search(r'/track/([a-zA-Z0-9]+)', href)
                    if not track_id_match:
                        continue

                    track_id = track_id_match.group(1)

                    # Evitar duplicados
                    if track_id in track_ids_vistos:
                        continue
                    track_ids_vistos.add(track_id)

                    # Encontrar la fila - ESTRATEGIAS SIMPLIFICADAS Y RÁPIDAS
                    row = None

                    # Estrategia 1: data-testid="tracklist-row" (más rápida)
                    try:
                        row = link.find_element(By.XPATH, './ancestor::div[@data-testid="tracklist-row"]')
                    except:
                        pass

                    # Estrategia 2: role="row"
                    if not row:
                        try:
                            row = link.find_element(By.XPATH, './ancestor::div[@role="row"]')
                        except:
                            pass

                    # Estrategia 3: Usar el enlace mismo
                    if not row:
                        row = link

                    # FILTRO: Verificar si está después de "Recomendaciones"
                    if recomendaciones_y is not None:
                        try:
                            row_y = row.location.get('y', 0)
                            if row_y >= recomendaciones_y:
                                canciones_omitidas_por_recomendaciones += 1
                                continue
                        except:
                            pass

                    # Título - ESTRATEGIAS RÁPIDAS
                    titulo = None

                    # Estrategia 1: Texto del enlace (más rápido)
                    try:
                        titulo = link.text.strip()
                        if titulo and len(titulo) > 0:
                            pass  # Título válido
                        else:
                            titulo = None
                    except:
                        titulo = None

                    # Estrategia 2: data-testid="track-name"
                    if not titulo and row != link:
                        try:
                            titulo_element = row.find_element(By.CSS_SELECTOR, '[data-testid="track-name"]')
                            titulo = titulo_element.text.strip()
                        except:
                            pass

                    # Si no tenemos título, skip
                    if not titulo:
                        continue

                    # Artistas - MEJORADO para buscar en todo el documento si es necesario
                    artistas = "Unknown Artist"
                    if row != link:
                        try:
                            artist_links = row.find_elements(By.CSS_SELECTOR, 'a[href*="/artist/"]')
                            if artist_links:
                                nombres = [a.text.strip() for a in artist_links[:3] if a.text.strip()]
                                if nombres:
                                    artistas = ', '.join(nombres)
                        except:
                            pass

                    # Si no se encontró, buscar usando JavaScript en el contenedor del track
                    if artistas == "Unknown Artist":
                        try:
                            # Buscar div padre que contenga el track
                            parent_script = """
                            var link = arguments[0];
                            var parent = link.closest('[data-testid="tracklist-row"]') || link.closest('[role="row"]');
                            if (!parent) {
                                for (var i = 0; i < 5; i++) {
                                    if (link.parentElement) {
                                        link = link.parentElement;
                                        if (link.querySelector('a[href*="/artist/"]')) {
                                            parent = link;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (parent) {
                                var artists = parent.querySelectorAll('a[href*="/artist/"]');
                                return Array.from(artists).map(a => a.textContent.trim()).filter(t => t).slice(0, 3).join(', ');
                            }
                            return '';
                            """
                            result = self.driver.execute_script(parent_script, link)
                            if result:
                                artistas = result
                        except:
                            pass

                    # Álbum - MEJORADO
                    album = "Unknown Album"
                    if row != link:
                        try:
                            album_link = row.find_element(By.CSS_SELECTOR, 'a[href*="/album/"]')
                            album_text = album_link.text.strip()
                            if album_text:
                                album = album_text
                        except:
                            pass

                    # Si no se encontró, buscar con JavaScript
                    if album == "Unknown Album":
                        try:
                            album_script = """
                            var link = arguments[0];
                            var parent = link.closest('[data-testid="tracklist-row"]') || link.closest('[role="row"]');
                            if (!parent) {
                                for (var i = 0; i < 5; i++) {
                                    if (link.parentElement) {
                                        link = link.parentElement;
                                        var albumLink = link.querySelector('a[href*="/album/"]');
                                        if (albumLink) {
                                            return albumLink.textContent.trim();
                                        }
                                    }
                                }
                            }
                            if (parent) {
                                var albumLink = parent.querySelector('a[href*="/album/"]');
                                return albumLink ? albumLink.textContent.trim() : '';
                            }
                            return '';
                            """
                            result = self.driver.execute_script(album_script, link)
                            if result:
                                album = result
                        except:
                            pass

                    # Duración - MEJORADO
                    duracion_segundos = 0
                    duracion_str = ""
                    if row != link:
                        try:
                            duracion_element = row.find_element(By.CSS_SELECTOR, '[data-testid="duration"]')
                            duracion_str = duracion_element.text.strip()
                            if duracion_str:
                                duracion_segundos = self.parsear_duracion(duracion_str)
                        except:
                            pass

                    # Si no se encontró, buscar con JavaScript
                    if not duracion_str:
                        try:
                            duration_script = """
                            var link = arguments[0];
                            var parent = link.closest('[data-testid="tracklist-row"]') || link.closest('[role="row"]');
                            if (!parent) {
                                for (var i = 0; i < 5; i++) {
                                    if (link.parentElement) {
                                        link = link.parentElement;
                                        var text = link.textContent;
                                        var match = text.match(/(\d{1,2}:\d{2})/);
                                        if (match) return match[1];
                                    }
                                }
                            }
                            if (parent) {
                                var dur = parent.querySelector('[data-testid="duration"]');
                                return dur ? dur.textContent.trim() : '';
                            }
                            return '';
                            """
                            result = self.driver.execute_script(duration_script, link)
                            if result:
                                duracion_str = result
                                duracion_segundos = self.parsear_duracion(duracion_str)
                        except:
                            pass

                    # Imagen - MEJORADO
                    imagen_url = ""
                    if row != link:
                        try:
                            img_element = row.find_element(By.TAG_NAME, 'img')
                            imagen_url = img_element.get_attribute('src') or ""
                        except:
                            pass

                    # Si no se encontró, buscar con JavaScript
                    if not imagen_url:
                        try:
                            image_script = """
                            var link = arguments[0];
                            var parent = link.closest('[data-testid="tracklist-row"]') || link.closest('[role="row"]');
                            if (!parent) {
                                for (var i = 0; i < 5; i++) {
                                    if (link.parentElement) {
                                        link = link.parentElement;
                                        var img = link.querySelector('img');
                                        if (img) return img.src;
                                    }
                                }
                            }
                            if (parent) {
                                var img = parent.querySelector('img');
                                return img ? img.src : '';
                            }
                            return '';
                            """
                            result = self.driver.execute_script(image_script, link)
                            if result:
                                imagen_url = result
                        except:
                            pass

                    cancion_info = {
                        'id': track_id,
                        'titulo': titulo,
                        'artistas': artistas,
                        'album': album,
                        'duracion_segundos': duracion_segundos,
                        'duracion': self.formatear_duracion(
                            duracion_segundos) if duracion_segundos > 0 else duracion_str,
                        'spotify_url': href,
                        'imagen_url': imagen_url
                    }

                    canciones.append(cancion_info)
                    canciones_procesadas += 1

                except Exception as e:
                    # Log error pero continuar
                    if idx % 20 == 0:  # Solo log cada 20 para no saturar
                        print(f"   ⚠️ Error en enlace {idx}: {str(e)[:50]}")
                    continue

            if canciones_omitidas_por_recomendaciones > 0:
                print(f"ℹ️ Se omitieron {canciones_omitidas_por_recomendaciones} canciones de recomendaciones")

            print(f"\n✅ Se extrajeron {len(canciones)} canciones de la playlist")

            resultado = {
                'success': True,
                'playlist': {
                    'nombre': nombre_playlist,
                    'total_canciones': len(canciones),
                    'url': url
                },
                'canciones': canciones
            }

            return resultado

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
    print("🎵  SPOTIFY PLAYLIST SCRAPER  🎵")
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