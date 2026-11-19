# Momento Estoico

Pausas programadas con un pensamiento estoico: cita, máxima o práctica.

## Compilar

    ./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/
    ./gradlew testDebugUnitTest    # pruebas

## Estructura (`com.momentoestoico`)

- `assets/thoughts.json` — biblioteca bilingüe (es/fr): 196 pensamientos con autor y fuente
- `data/Thoughts.kt` — carga de la biblioteca e idiomas
- `data/Model.kt` — momentos programados (hora, días, frecuencia) y selección sin repetición
- `data/Repository.kt` — persistencia (DataStore + JSON)
- `alarm/` — AlarmManager, receptor de alarmas y reprogramación tras reinicio
- `notify/` — canal y notificación (sonido de cuenco sintetizado, `res/raw/cuenco.wav`)
- `ui/` — Compose: Hoy, Momentos, Biblioteca y pantalla del momento con respiración guiada

## Ampliar la biblioteca

Los textos se editan en `tools/thoughts_part1.py` (estoicos) y `tools/thoughts_part2.py`
(afines y prácticas). Después:

    python3 tools/build_thoughts.py app/src/main/assets/thoughts.json

El script valida ids, autores, obras y que haya texto en ambos idiomas.

## Idiomas

Español y francés. La app sigue el idioma del sistema (cualquier otro → español), y se puede
fijar en Momentos › Idioma o, en Android 13+, en Ajustes › Apps › Momento Estoico › Idioma.
Textos de la interfaz en `res/values/strings.xml` y `res/values-fr/strings.xml`.

Tipografía: Cormorant Garamond (SIL Open Font License, ver `OFL-CormorantGaramond.txt`).
Las traducciones de los textos clásicos son versiones libres.

## Licencia

Código bajo GPL-3.0 (ver `LICENSE`). © 2026 sukhoi-ar2.
