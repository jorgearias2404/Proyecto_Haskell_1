# Almacen Robotico Concurrente

Esqueleto inicial para el Proyecto 3 de Lenguajes de Programacion. No es una
implementacion funcional: los metodos pendientes lanzan
`UnsupportedOperationException` para hacer visibles las partes que faltan.

## Compilar

Requiere un JDK instalado y `javac` disponible en `PATH`. En Windows:

```bat
compilar.bat
```

La ejecucion se inicia con:

```bat
java -cp out SimulacionRobotsSokoban archivo.txt
```

## Trabajo pendiente

- Acordar y documentar el formato exacto de las cuatro lineas de entrada.
- Implementar la validacion de capacidad y la ubicacion aleatoria inicial.
- Completar el monitor: accesos sincronizados al tablero, contador `_quantum_lock_ticks`, movimientos, empujes, espera/notificacion y extraccion.
- Completar los ciclos de productores y robots, incluida la bateria y la calibracion en (3,3).
- Definir una condicion de parada, detener productores y unir todos los hilos con `join()`.
- Agregar trazas con el formato del enunciado, el reporte estadistico, el tablero final y casos de prueba.
- Documentar las decisiones para reducir interbloqueos y probar configuraciones limite.

## Procesos y recurso compartido

El tablero de 6x6 es el recurso compartido. Productores y robots son hilos; las
operaciones que inspeccionan o modifican el tablero deben pasar por
`MonitorTablero`. Completa aqui las condiciones de exclusion mutua, espera y
notificacion que adoptes, y explica como evitas mantener el monitor durante
`Thread.sleep()`.

## Uso de asistencia

Se consulto GitHub Copilot con la solicitud «generame un codigo base para
partir» para crear este esqueleto de clases, nombres requeridos y lista de
tareas pendientes. No se genero aqui la logica de concurrencia; debe ser
diseñada, implementada y probada por el equipo.
