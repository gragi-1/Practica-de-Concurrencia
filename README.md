# CarreteraSim

Simulador concurrente de una carretera con varios coches y carriles, implementado en Java con distintos mecanismos de sincronización (Monitor, CSP…).

## Características

- **Visualización GUI** con Swing: cada coche muestra su identificador y los ticks restantes.
- **Modularidad**: cambio sencillo de la implementación del recurso compartido (Monitor, CSP, …).
- **Parámetros configurables**: número de segmentos, carriles, velocidades, modo manual/automático de ticks.
- **Registro de eventos**: ventana de texto que muestra cada llamada y su respuesta (entrar, avanzar, circulando, salir, tick…).

## Estructura del proyecto

```
PracticaConcu/
├─ src/cc/carretera/   # Código fuente Java
├─ lib/                # Dependencias (p.ej. cclib-0.4.9.jar)
└─ bin/                # Ficheros .class generados
```

## Requisitos

- JDK 8 o superior  
- Biblioteca de concurrencia “cclib-0.4.9.jar” (colócala en `lib/`)  
- IDE o editor que soporte proyectos Java (Eclipse, IntelliJ, VS Code…)

## Compilar y ejecutar

> Desde tu IDE favorito: importa el proyecto y ajusta las rutas de librerías.

> Desde línea de comandos (Unix/macOS):
````bash
javac -d . -cp .:cclib-0.4.9.jar src/cc/carretera/*.java
java -cp .:cclib-0.4.9.jar cc.carretera.CarreteraSim
````

> En Windows (PowerShell o CMD):
````powershell
javac -d . -cp .;lib\cclib-0.4.9.jar src\cc\carretera\*.java
java -cp .;lib\cclib-0.4.9.jar cc.carretera.CarreteraSim
````

## Cambiar la implementación del recurso compartido

En `CarreteraSim.java`, línea 533, sustituye la creación del monitor por la versión que quieras probar:

```diff
-      crPre = new CarreteraMonitor(segmentos, carriles);
+      crPre = new CarreteraCSP(      segmentos, carriles);
```

Después **recompila** todo antes de ejecutar de nuevo:

````bash
javac -d . -cp .:cclib-0.4.9.jar src/cc/carretera/*.java
````

## Actualizaciones

Este repositorio se actualizará periódicamente con:

- Mejoras en el simulador (rendimiento, nuevas opciones).
- Correcciones de errores detectados.
- Plantillas y ejemplos adicionales de código concurrente.

¡Disfruta del simulador y no dudes en contribuir con pull requests o reportando issues!## Actualizaciones

Este repositorio se actualizará periódicamente con:

- Mejoras en el simulador (rendimiento, nuevas opciones).
- Correcciones de errores detectados.
- Plantillas y ejemplos adicionales de código concurrente.

