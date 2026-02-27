#!/usr/bin/env python3
"""
Spotify Playlist Scraper
Lee información de canciones de playlists de Spotify usando web scraping
"""

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
import time
import re


class SpotifyPlaylistScraper:
    """
    Clase para extraer información de playlists de Spotify mediante web scraping
    """

    def __init__(self, headless=True):
        """
        Inicializa el scraper con configuración de Chrome

        Args:
            headless (bool): Si True, ejecuta Chrome en modo headless (sin interfaz)
        """
        self.headless = headless
        self.driver = None

    def iniciar_driver(self):
        """
        Configura e inicia el driver de Chrome
        """
        chrome_options = Options()

        if self.headless:
            chrome_options.add_argument('--headless=new')

        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-setuid-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        chrome_options.add_argument('--disable-gpu')
        chrome_options.add_argument('--window-size=1920,1080')
        chrome_options.add_argument(
            'user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')

        try:
            self.driver = webdriver.Chrome(options=chrome_options)
            print("✅ Chrome iniciado correctamente")
        except Exception as e:
            print(f"❌ Error al iniciar Chrome: {e}")
            print("\n💡 Asegúrate de tener ChromeDriver instalado:")
            print("   pip install webdriver-manager")
            raise

    def cerrar_driver(self):
        """
        Cierra el navegador
        """
        if self.driver:
            self.driver.quit()
            print("✅ Navegador cerrado")

    def parsear_duracion(self, duracion_str):
        """
        Convierte string de duración (mm:ss) a segundos

        Args:
            duracion_str (str): Duración en formato "mm:ss"

        Returns:
            int: Duración en segundos
        """
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
        """
        Convierte segundos a formato mm:ss

        Args:
            segundos (int): Duración en segundos

        Returns:
            str: Duración en formato mm:ss
        """
        minutos = segundos // 60
        segs = segundos % 60
        return f"{minutos}:{segs:02d}"

    def extraer_playlist_id(self, url):
        """
        Extrae el ID de la playlist desde una URL

        Args:
            url (str): URL de Spotify

        Returns:
            str: ID de la playlist
        """
        if "playlist/" in url:
            match = re.search(r'playlist/([a-zA-Z0-9]+)', url)
            if match:
                return match.group(1)
        return url

    def obtener_canciones_playlist(self, url):
        """
        Extrae todas las canciones de una playlist de Spotify

        Args:
            url (str): URL de la playlist de Spotify

        Returns:
            dict: Información de la playlist y sus canciones
        """
        if not self.driver:
            self.iniciar_driver()

        try:
            print(f"\n🔍 Accediendo a la playlist...")
            self.driver.get(url)

            # Esperar a que cargue el contenido
            print("⏳ Esperando que cargue la página...")
            time.sleep(3)

            # Esperar a que aparezcan los tracks
            try:
                WebDriverWait(self.driver, 15).until(
                    EC.presence_of_element_located((By.CSS_SELECTOR, 'a[href*="/track/"]'))
                )
            except:
                print("⚠️ Tardando más de lo esperado, continuando...")

            # Hacer scroll para cargar todas las canciones
            print("📜 Cargando todas las canciones...")
            last_height = self.driver.execute_script("return document.body.scrollHeight")
            scroll_attempts = 0
            max_scrolls = 20  # Límite de scrolls para evitar bucles infinitos

            while scroll_attempts < max_scrolls:
                # Scroll hacia abajo
                self.driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
                time.sleep(1.5)

                # Calcular nueva altura
                new_height = self.driver.execute_script("return document.body.scrollHeight")

                if new_height == last_height:
                    break

                last_height = new_height
                scroll_attempts += 1
                print(f"   Scroll {scroll_attempts}/{max_scrolls}...")

            print("✅ Todas las canciones cargadas")

            # Extraer información de la playlist
            print("\n📊 Extrayendo información...")

            # Nombre de la playlist
            nombre_playlist = "Unknown Playlist"
            try:
                nombre_element = self.driver.find_element(By.TAG_NAME, 'h1')
                nombre_playlist = nombre_element.text.strip()
            except:
                pass

            # Extraer todas las canciones
            canciones = []
            track_ids_vistos = set()

            # Intentar localizar el encabezado de "Recomendaciones" para saber
            # a partir de qué punto empiezan las sugerencias (no forman parte de la playlist)
            recomendaciones_y = None
            try:
                recomendaciones_header = self.driver.find_element(
                    By.XPATH,
                    "//*[normalize-space(text())='Recomendaciones' or normalize-space(text())='Recomendations' or normalize-space(text())='Recommendations']"
                )
                recomendaciones_y = recomendaciones_header.location.get("y", None)
                print(f"📍 Encabezado 'Recomendaciones' localizado en Y={recomendaciones_y}")
            except Exception:
                # Si no encontramos el encabezado, simplemente no aplicamos este filtro adicional
                print("ℹ️ No se encontró encabezado de 'Recomendaciones', se procesará todo el listado")

            # Encontrar todos los enlaces de tracks
            track_links = self.driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')

            print(f"🎵 Procesando {len(track_links)} enlaces de canciones...")

            for link in track_links:
                try:
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

                    # Encontrar la fila/row de la canción
                    row = None
                    try:
                        row = link.find_element(By.XPATH, './ancestor::div[@data-testid="tracklist-row"]')
                    except:
                        try:
                            row = link.find_element(By.XPATH, './ancestor::div[@role="row"]')
                        except:
                            row = link.find_element(By.XPATH, './ancestor::div[contains(@class, "track")]')

                    if not row:
                        continue

                    # Si conocemos la posición Y del bloque de "Recomendaciones",
                    # descartamos cualquier fila que esté por debajo -> es sugerencia, no parte de la playlist
                    if recomendaciones_y is not None:
                        try:
                            row_y = row.location.get("y", 0)
                            if row_y >= recomendaciones_y:
                                # Esta fila ya pertenece a la sección de recomendaciones
                                continue
                        except Exception:
                            pass

                    # Filtrar recomendaciones con botón "Añadir" / "Add" por si acaso
                    try:
                        add_buttons = row.find_elements(
                            By.XPATH,
                            ".//button[normalize-space(text())='Añadir' or normalize-space(text())='Add']"
                        )
                        if add_buttons:
                            continue
                    except Exception:
                        pass

                    # Título
                    titulo = "Unknown"
                    try:
                        titulo_element = row.find_element(By.CSS_SELECTOR, '[data-testid="track-name"]')
                        titulo = titulo_element.text.strip()
                    except:
                        try:
                            titulo = link.text.strip()
                        except:
                            pass

                    if not titulo or titulo == "Unknown":
                        continue

                    # Artistas
                    artistas = "Unknown Artist"
                    try:
                        artist_links = row.find_elements(By.CSS_SELECTOR, 'a[href*="/artist/"]')
                        if artist_links:
                            artistas = ', '.join([a.text.strip() for a in artist_links if a.text.strip()])
                    except:
                        pass

                    # Álbum
                    album = "Unknown Album"
                    try:
                        album_link = row.find_element(By.CSS_SELECTOR, 'a[href*="/album/"]')
                        if album_link and album_link.text.strip():
                            album = album_link.text.strip()
                    except:
                        pass

                    # Duración
                    duracion_segundos = 0
                    duracion_str = ""
                    try:
                        duracion_element = row.find_element(By.CSS_SELECTOR, '[data-testid="duration"]')
                        duracion_str = duracion_element.text.strip()
                        duracion_segundos = self.parsear_duracion(duracion_str)
                    except:
                        pass

                    # Imagen
                    imagen_url = ""
                    try:
                        img_element = row.find_element(By.TAG_NAME, 'img')
                        imagen_url = img_element.get_attribute('src')
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

                except Exception as e:
                    # Silenciar errores individuales para continuar con otras canciones
                    continue

            resultado = {
                'success': True,
                'playlist': {
                    'nombre': nombre_playlist,
                    'total_canciones': len(canciones),
                    'url': url
                },
                'canciones': canciones
            }

            print(f"\n✅ Se extrajeron {len(canciones)} canciones exitosamente")

            return resultado

        except Exception as e:
            print(f"\n❌ Error al extraer playlist: {e}")
            return {
                'success': False,
                'error': str(e)
            }

    def mostrar_canciones(self, resultado):
        """
        Muestra las canciones en formato de tabla

        Args:
            resultado (dict): Resultado de obtener_canciones_playlist
        """
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
        """
        Exporta las canciones a un archivo CSV

        Args:
            resultado (dict): Resultado de obtener_canciones_playlist
            nombre_archivo (str): Nombre del archivo CSV
        """
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
    """
    Función principal - interfaz de usuario
    """
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
                    # Obtener canciones
                    resultado = scraper.obtener_canciones_playlist(playlist_url)

                    if resultado['success']:
                        # Mostrar canciones
                        scraper.mostrar_canciones(resultado)

                        # Preguntar si quiere exportar
                        exportar = input("\n¿Exportar a CSV? (s/n): ").strip().lower()
                        if exportar == 's':
                            nombre_csv = input(
                                "Nombre del archivo (presiona Enter para 'playlist_spotify.csv'): ").strip()
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
        print("\n\n👋 Programa interrumpido por el usuario")
    except Exception as e:
        print(f"\n❌ Error: {e}")
    finally:
        scraper.cerrar_driver()


if __name__ == "__main__":
    main()