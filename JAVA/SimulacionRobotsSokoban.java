public final class SimulacionRobotsSokoban {
    private SimulacionRobotsSokoban() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java SimulacionRobotsSokoban <archivo.txt>");
            return;
        }

        System.out.println("Esqueleto inicial. Falta implementar la simulacion.");
        System.out.println("Archivo de configuracion: " + args[0]);
        throw new UnsupportedOperationException(
                "TODO: leer y validar entrada, iniciar hilos, hacer join y emitir reporte final");
    }
}