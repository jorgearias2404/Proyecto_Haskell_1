import java.util.Objects;

public class Posicion {

    private final int fila;
    private final int columna;

    public Posicion(int fila, int columna) {
        this.fila = fila;
        this.columna = columna;
    }

    public int getFila() {
        return fila;
    }

    public int getColumna() {
        return columna;
    }

    public Posicion arriba() {
        return new Posicion(fila - 1, columna);
    }

    public Posicion abajo() {
        return new Posicion(fila + 1, columna);
    }

    public Posicion izquierda() {
        return new Posicion(fila, columna - 1);
    }

    public Posicion derecha() {
        return new Posicion(fila, columna + 1);
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Posicion)) {
            return false;
        }

        Posicion otra = (Posicion) obj;

        return fila == otra.fila &&
               columna == otra.columna;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fila, columna);
    }

    @Override
    public String toString() {
        return "(" + fila + "," + columna + ")";
    }
}