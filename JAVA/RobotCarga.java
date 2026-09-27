/**
 * RobotCarga (Consumidor)
 * ------------------------
 * Cada instancia es un hilo independiente que representa un robot de
 * carga. Su ciclo de vida:
 *
 *   mientras (batería > 0):
 *       pedirle al monitor un turno (mover, empujar, extraer o esperar)
 *       dormir un poco (ritmo de simulación, fuera del monitor)
 *   retirarse del tablero (batería agotada)
 *
 * La batería solo se decrementa dentro de MonitorTablero.turno(), y
 * únicamente cuando el turno resultó en un movimiento o empuje real
 * (una espera por falta de Cajas Objetivo NO consume batería, tal como
 * exige el enunciado).
 *
 * Nota de concurrencia: el campo `bateria` solo es leído y escrito por
 * el propio hilo del robot (incluso cuando la escritura ocurre "dentro"
 * de un método synchronized del monitor, quien ejecuta ese código sigue
 * siendo este mismo hilo), por lo que no requiere sincronización
 * adicional ni `volatile`.
 */
public class RobotCarga implements Runnable {

    private final int id;
    private int bateria;
    private final MonitorTablero monitor;
    private final int velocidadMs;

    public RobotCarga(int id, int bateriaInicial, MonitorTablero monitor, int velocidadMs) {
        this.id = id;
        this.bateria = bateriaInicial;
        this.monitor = monitor;
        this.velocidadMs = velocidadMs;
    }

    public int getId() {
        return id;
    }

    public int getBateria() {
        return bateria;
    }

    public void consumirBateria() {
        bateria--;
    }

    public String getNombre() {
        return "Robot-" + id;
    }

    @Override
    public void run() {
        try {
            while (bateria > 0) {
                MonitorTablero.ResultadoTurno resultado = monitor.turno(this);
                if (resultado == MonitorTablero.ResultadoTurno.FINALIZAR_SIN_TRABAJO) {
                    // Condición de parada adicional (ver MonitorTablero):
                    // ya no hay ni puede haber más Cajas Objetivo.
                    break;
                }
                // Pequeña pausa para dar ritmo a la simulación y hacer la
                // traza legible; si no hubo acción (esperando turno u
                // obstáculo momentáneo) se reintenta un poco más rápido.
                boolean actuo = (resultado == MonitorTablero.ResultadoTurno.ACTUO);
                Thread.sleep(actuo ? velocidadMs : Math.max(20, velocidadMs / 4));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            monitor.retirarRobot(this);
        }
    }
}
