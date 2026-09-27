public final class RobotCarga extends Thread {
    private final MonitorTablero monitor;
    private int bateria;

    public RobotCarga(int id, int bateriaInicial, MonitorTablero monitor) {
        super("Robot-" + id);
        this.monitor = monitor;
        this.bateria = bateriaInicial;
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException(
                "TODO: buscar objetivos, moverse/empujar, esperar y consumir bateria");
    }

    private void recalibrarGiroscopioXYZ() {
    }
}