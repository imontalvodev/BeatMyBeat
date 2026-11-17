# Solo para el build debug. NewPipe Extractor usa `record`, y el dexeo por librería de AGP (D8)
# falla con "Attempt to create a global synthetic for 'Record desugaring' without a
# global-synthetics consumer". Pasar el dexeo por R8 lo resuelve (como ya hace release), pero sin
# ofuscar, optimizar ni recortar: la app se comporta y se depura igual que antes.
-dontobfuscate
-dontoptimize
-dontshrink

# Sin recortar quedan referencias a clases que no existen en Android (ktor, detector de IntelliJ).
-dontwarn java.lang.management.**
