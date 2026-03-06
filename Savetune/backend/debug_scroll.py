#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Debug del scroll - ver qué está pasando
"""

from selenium import webdriver
from selenium.webdriver.firefox.options import Options
from selenium.webdriver.common.by import By
import time

print("Iniciando Firefox...")
options = Options()
options.add_argument('--headless')

driver = webdriver.Firefox(options=options)

# USA TU URL DE PLAYLIST GRANDE
url = input("URL de playlist grande (100+ canciones): ").strip()

print(f"\nCargando: {url}")
driver.get(url)

print("Esperando 5 segundos...")
time.sleep(5)

print("\n=== INICIO DEL SCROLL ===\n")

last_count = 0
scroll_count = 0
no_change = 0

while scroll_count < 50:
    # Scroll
    driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
    time.sleep(1.2)
    
    # Contar
    links = driver.find_elements(By.CSS_SELECTOR, 'a[href*="/track/"]')
    current_count = len(links)
    
    # Info
    height = driver.execute_script("return document.body.scrollHeight")
    viewport = driver.execute_script("return window.innerHeight")
    scroll_pos = driver.execute_script("return window.pageYOffset")
    
    print(f"Scroll {scroll_count+1:3d}: Links={current_count:3d} | Height={height:5d} | ViewPort={viewport:4d} | ScrollPos={scroll_pos:5d}")
    
    if current_count == last_count:
        no_change += 1
        print(f"           ⚠️ Sin cambios ({no_change}/5)")
        if no_change >= 5:
            print("\n❌ DETENIDO: 5 scrolls sin nuevas canciones")
            break
    else:
        diff = current_count - last_count
        print(f"           ✅ +{diff} nuevas canciones")
        no_change = 0
        last_count = current_count
    
    scroll_count += 1

print(f"\n=== FIN ===")
print(f"Total final: {last_count} canciones")
print(f"Scrolls realizados: {scroll_count}")

# Intentar detectar si hay virtualización
print("\n=== ANÁLISIS DEL DOM ===")

# Ver si hay un contenedor con scroll propio
containers = driver.find_elements(By.CSS_SELECTOR, '[role="presentation"], [class*="main"], [class*="scroll"]')
print(f"Contenedores encontrados: {len(containers)}")

# Ver si hay algo con overflow scroll
script = """
var scrollables = [];
var all = document.querySelectorAll('*');
for (var i = 0; i < all.length; i++) {
    var el = all[i];
    var style = window.getComputedStyle(el);
    if (style.overflow === 'scroll' || style.overflow === 'auto' || 
        style.overflowY === 'scroll' || style.overflowY === 'auto') {
        if (el.scrollHeight > el.clientHeight) {
            scrollables.push({
                tag: el.tagName,
                classes: el.className.substring(0, 50),
                scrollHeight: el.scrollHeight,
                clientHeight: el.clientHeight
            });
        }
    }
}
return scrollables.slice(0, 10);
"""

scrollables = driver.execute_script(script)
print(f"\nElementos con scroll propio: {len(scrollables)}")
for s in scrollables:
    print(f"  - {s['tag']} (scroll={s['scrollHeight']}, client={s['clientHeight']}) {s['classes'][:30]}")

driver.quit()

print("\n💡 Si hay elementos con scroll propio, el problema es que")
print("   estamos haciendo scroll en window pero el contenido está")
print("   en un contenedor interno que necesita scroll.")
