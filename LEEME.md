# Radio Coche · versión 0.1 (sintonizador simulado)

App de radio FM/AM para la unidad Android Witson del coche.

Esta versión **no suena todavía**: usa un sintonizador simulado para probar la interfaz
en la pantalla real. El control del sintonizador de la Witson se añadirá en el siguiente paso
(clase `tuner/SimulatedTuner.kt` → sustituir por la real en `ui/RadioViewModel.kt`).

## Compilar la APK

1. Instala **Android Studio** (gratis): https://developer.android.com/studio
2. Descomprime esta carpeta y ábrela con *File › Open…* (la carpeta `RadioCoche`).
3. Espera a que termine la sincronización de Gradle (la primera vez descarga varias cosas).
   Si pregunta por actualizar el plugin de Android, puedes decir que no.
4. Menú *Build › Build App Bundle(s) / APK(s) › Build APK(s)*.
5. Al terminar, pulsa *locate*: la APK está en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar en la unidad

1. Copia `app-debug.apk` a un pendrive.
2. En la unidad, abre el gestor de archivos y pulsa sobre la APK.
3. Si lo pide, permite instalar apps de origen desconocido para el gestor de archivos.
4. Abre la app **Radio**.

## Estructura

- `model/Model.kt` – bandas, regiones, formatos, tipos PTY.
- `tuner/Tuner.kt` – lo que la app necesita del sintonizador.
- `tuner/SimulatedTuner.kt` – sintonizador de prueba.
- `data/Storage.kt` – presintonías y ajustes guardados.
- `ui/RadioViewModel.kt` – lógica de la radio.
- `ui/RadioScreen.kt`, `ui/SettingsScreen.kt` – pantallas.
- `MainActivity.kt` – `EQ_PACKAGE`: paquete de la app del ecualizador.
