## Opciones de anuncios poco intrusivos

- **Banners fijos fuera del foco principal**
  - Debajo del reproductor (opción recomendada).
  - En la parte inferior de la pantalla, con altura pequeña.
  - Siempre fijos, sin cubrir controles ni pedir interacción.

- **Anuncios nativos integrados en listas**
  - Cards de anuncio dentro de listas (por ejemplo, cada 10 ítems).
  - Mismo estilo visual que el contenido, pero claramente marcado como “Anuncio”.
  - Muy poco intrusivos si se limita la frecuencia.

- **Anuncios en pantallas secundarias o de espera**
  - Pantallas de resultados de búsqueda, listas largas o sección “Explorar”.
  - Nunca en mitad de una acción crítica (reproducción, login, pago, etc.).
  - Se pueden usar banners o pequeños bloques nativos.

- **Anuncios sólo cuando el usuario está inactivo**
  - Mientras el usuario está en una pantalla de detalles mirando información, sin tocar nada.
  - Nunca interrumpir la reproducción con popups o vídeos obligatorios.

- **Promos internas (cross‑promo) en lugar de anuncios de red**
  - Bloques que promocionen funciones premium, playlists destacadas, etc.
  - Funcionan como “anuncios” pero mejoran la experiencia en lugar de molestar.

## Qué evitar

- **Intersticiales a pantalla completa** al abrir la app o al iniciar la reproducción.
- **Vídeos recompensados obligatorios** (si se usan, deben ser 100 % opcionales y con recompensa clara).
- **Popups que tapen el reproductor o los controles**.

## Proveedores y tecnologías recomendadas

- **Google AdMob**
  - Estándar para apps móviles (Android/iOS).
  - Fácil de integrar, admite banners y anuncios nativos.

- **Google Ad Manager / AdSense (web)**
  - Para frontend web, buena opción para banners y nativos.

- **Alternativas sin Google**
  - AppLovin, Unity Ads, Meta Audience Network.
  - Más centrados en intersticiales y vídeo; usarlos con cuidado para no molestar.

## Reglas para que los anuncios no sean molestos

- **Frecuencia controlada**
  - Máximo 1 banner visible por pantalla.
  - Si se usan nativos en listas: uno cada 8–12 ítems como mínimo.

- **Ubicación clara y estable**
  - El banner no debe cambiar de tamaño ni hacer “saltos” que muevan el contenido.

- **Diseño discreto**
  - Colores alineados con la UI, sin animaciones agresivas.

- **Respeto a la privacidad**
  - Pantalla clara de consentimiento (GDPR, etc.).
  - Opción de limitar o desactivar el tracking personalizado.

## Preparación para el modo sin anuncios (pago único)

- **Flag centralizado `tiene_premium`**
  - Única fuente de verdad (store global / contexto de usuario).
  - Toda la lógica de mostrar anuncios pasa por ese flag.

- **Arquitectura de componentes de anuncio**
  - Crear un componente único, por ejemplo `AdSlot`, que:
    - Si `tiene_premium` es `true`, no renderiza nada.
    - Si `tiene_premium` es `false`, renderiza el banner / nativo correspondiente.
  - Al implementar el pago único, sólo hay que actualizar ese flag.

- **Persistencia del pago**
  - Guardar el estado en backend (perfil de usuario) y caché local (AsyncStorage / localStorage).
  - Implementar restauración de compras en móvil para que el usuario no pierda el modo sin anuncios al cambiar de dispositivo.

