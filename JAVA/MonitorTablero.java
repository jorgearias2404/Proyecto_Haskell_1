import java.util.Random;

public final class MonitorTablero {
    public static final int TAMANO = 6;
    public static final char VACIO = '.';
    public static final char OBJETIVO = 'O';
    public static final char BLOQUEO = 'X';

    private final char[][] tablero = new char[TAMANO][TAMANO];
    private int _quantum_lock_ticks;

    public MonitorTablero() {
        for (int fila = 0; fila < TAMANO; fila++) {
            for (int columna = 0; columna < TAMANO; columna++) {
                tablero[fila][columna] = VACIO;
            }
        }
    }

    public synchronized boolean insertarCajaAleatoria(Random aleatorio) {
        registrarAcceso();
        throw new UnsupportedOperationException("TODO: insertar caja o reportar saturacion");
    }

    public synchronized boolean moverRobot(int idRobot, int fila, int columna) {
        registrarAcceso();
        throw new UnsupportedOperationException("TODO: validar movimiento y colisiones");
    }

    public synchronized void esperarPorObjetivo() throws InterruptedException {
        registrarAcceso();
        throw new UnsupportedOperationException("TODO: esperar con wait() hasta que haya objetivo");
    }

    public synchronized void retirarRobot(int idRobot) {
        registrarAcceso();
        throw new UnsupportedOperationException("TODO: retirar robot y notificar a los hilos esperando");
    }

    public synchronized void imprimirTablero() {
        registrarAcceso();
        throw new UnsupportedOperationException("TODO: imprimir las 6 filas del tablero");
    }

    private void registrarAcceso() {
        _quantum_lock_ticks++;
    }
}