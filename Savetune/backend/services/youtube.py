#!/usr/bin/env python3
"""
YouTube Music Downloader
Busca y descarga canciones de YouTube en formato MP3
"""

import yt_dlp
import os


def descargar_cancion(nombre_cancion, carpeta_destino="descargas"):
    """
    Busca y descarga una canción de YouTube en formato MP3

    Args:
        nombre_cancion (str): Nombre de la canción a buscar
        carpeta_destino (str): Carpeta donde se guardará el archivo

    Returns:
        bool: True si la descarga fue exitosa, False en caso contrario
    """

    # Crear carpeta de destino si no existe
    if not os.path.exists(carpeta_destino):
        os.makedirs(carpeta_destino)

    # Configuración de yt-dlp (SIN conversión - descarga en formato original)
    ydl_opts = {
        'format': 'bestaudio/best',  # Descarga solo audio en el mejor formato disponible
        'outtmpl': os.path.join(carpeta_destino, '%(title)s.%(ext)s'),
        'quiet': False,
        'no_warnings': False,
        'default_search': 'ytsearch1',  # Busca en YouTube y toma el primer resultado
    }

    try:
        print(f"\n🔍 Buscando: {nombre_cancion}")
        print("=" * 50)

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # Descargar
            info = ydl.extract_info(nombre_cancion, download=True)

            # Obtener información del video
            if 'entries' in info:
                video = info['entries'][0]
            else:
                video = info

            titulo = video.get('title', 'Desconocido')
            duracion = video.get('duration', 0)
            minutos = duracion // 60
            segundos = duracion % 60

            print("\n" + "=" * 50)
            print(f"✅ Descarga completada!")
            print(f"📝 Título: {titulo}")
            print(f"⏱️  Duración: {minutos}:{segundos:02d}")
            print(f"📁 Guardado en: {carpeta_destino}/")
            print("=" * 50)

            return True

    except Exception as e:
        print(f"\n❌ Error al descargar: {str(e)}")
        return False


def main():
    """
    Función principal - interfaz de usuario
    """
    print("\n" + "=" * 50)
    print("🎵  YOUTUBE MUSIC DOWNLOADER  🎵")
    print("=" * 50)

    while True:
        print("\nOpciones:")
        print("1. Descargar una canción")
        print("2. Salir")

        opcion = input("\nElige una opción (1-2): ").strip()

        if opcion == "1":
            nombre_cancion = input("\n🎤 Ingresa el nombre de la canción: ").strip()

            if nombre_cancion:
                descargar_cancion(nombre_cancion)
            else:
                print("❌ Por favor ingresa un nombre válido")

        elif opcion == "2":
            print("\n👋 ¡Hasta luego!")
            break
        else:
            print("❌ Opción no válida")


if __name__ == "__main__":
    main()