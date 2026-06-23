# Proyecto: Almacén Robótico 

Este programa ayuda a un robot a mover una caja importante hasta la zona de salida de un almacén.

## ¿Qué hace cada parte del código?

1. **Preparar el almacén:** Antes de empezar, el programa revisa que el robot y las cajas estén bien puestos en el mapa (6x6) y que no haya errores. Si algo está mal, no deja empezar.
2. **Revisar si un movimiento se puede hacer:** Antes de que el robot se mueva, el programa se asegura de que no choque con paredes, que no se salga del almacén y que no intente empujar dos cajas a la vez. 
3. **Mover al robot:** Cuando confirmamos que el movimiento es correcto, esta parte actualiza dónde está el robot y, si empujó una caja, también mueve la caja de lugar.
4. **Encontrar la salida:** El programa prueba todos los caminos posibles paso a paso para encontrar el camino más corto posible y llevar la caja a la meta. Así, el robot nunca da vueltas de más.


## ¿Cómo está hecho?
- Usamos **reglas de lógica** para definir qué puede y qué no puede hacer el robot.
- Usamos un **algoritmo de búsqueda** que se encarga de explorar el mapa de forma ordenada para no fallar y encontrar siempre la mejor solución.