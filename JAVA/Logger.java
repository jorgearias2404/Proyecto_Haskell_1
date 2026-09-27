/**
 * Logger centralizado y thread-safe para la traza de ejecución.
 *
 * Recurso crítico: el contador de "tick" (número de evento global) y la
 * consola (System.out). Ambos se protegen con un único método `synchronized`
 * estático, de modo que:
 *   1) el incremento del contador y la impresión de la línea ocurren como
 *      una operación atómica (sección crítica), evitando que dos hilos
 *      entrelacen caracteres o repitan/salten números de tick, y
 *   2) el orden de impresión coincide siempre con el orden real en que
 *      ocurrieron los eventos, porque cada hilo debe adquirir el mismo
 *      candado (el objeto de clase Logger.class) para poder loguear.
 */
public class Logger {

    private static int tick = 0;

    /**
     * Registra un evento en la traza de ejecución.
     *
     * @param idHilo  identificador del hilo, ej. "Robot-1", "Productor-2"
     * @param accion  descripción de la acción ya formateada, ej.
     *                "MOVER (0,0) -> (0,1) | Batería: 14"
     */
    public static synchronized void log(String idHilo, String accion) {
        tick++;
        System.out.printf("[Tick-%02d] [%s] %s%n", tick, idHilo, accion);
    }
}
