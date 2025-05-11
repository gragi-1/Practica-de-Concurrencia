// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {
  // Estado de la carretera: matriz de ocupación (null si libre, id si ocupado)
  private final String[][] ocupacion;
  // Ticks restantes para cada coche
  private final Map<String, Integer> ticksRestantes;
  // Posición actual de cada coche
  private final Map<String, Pos> posiciones;
  // Monitor y condición
  private final Monitor mutex;
  private final Monitor.Cond puedeMover;
  private final Monitor.Cond puedeCircular;
  // Dimensiones
  private final int segmentos;
  private final int carriles;

  /**
   * Crea una carretera con un número de segmentos y carriles.
   *
   * @param segmentos número de segmentos
   * @param carriles número de carriles
   */
  public CarreteraMonitor(int segmentos, int carriles) {
    this.segmentos = segmentos;
    this.carriles = carriles;
    this.ocupacion = new String[segmentos][carriles];
    this.ticksRestantes = new HashMap<>();
    this.posiciones = new HashMap<>();
    this.mutex = new Monitor();
    this.puedeMover = mutex.newCond();
    this.puedeCircular = mutex.newCond();
    // Inicialmente todo libre
    for (int i = 0; i < segmentos; i++)
      for (int j = 0; j < carriles; j++)
        ocupacion[i][j] = null;
  }

  /**
   * Busca un carril libre en el primer segmento.
   *
   * @return el índice del carril libre o -1 si no hay ninguno
   */
  private int buscarCarrilLibre() {
    for (int c = 0; c < carriles; c++) {
      if (ocupacion[0][c] == null) return c;
    }
    return -1;
  }

  /**
   * Busca un carril libre en el segmento indicado.
   *
   * @param seg el segmento a comprobar
   * @return el índice del carril libre o -1 si no hay ninguno
   */
  public Pos entrar(String id, int tks) {
    mutex.enter();
    try {
      // Esperar hasta que haya hueco en el primer segmento
      while (buscarCarrilLibre() == -1) {
        puedeMover.await();
      }
      int carril = buscarCarrilLibre();
      ocupacion[0][carril] = id;
      posiciones.put(id, new Pos(1, carril + 1));
      ticksRestantes.put(id, tks);
      puedeCircular.signal(); // Por si el coche espera para circular
      return new Pos(1, carril + 1);
    } finally {
      mutex.leave();
    }
  }

  /**
   * Avanza un coche al siguiente segmento.
   *
   * @param id el identificador del coche
   * @param tks el número de ticks restantes
   * @return la nueva posición del coche
   */
  public Pos avanzar(String id, int tks) {
    mutex.enter();
    try {
      Pos pos = posiciones.get(id);
      int seg = pos.getSegmento() - 1;
      int car = pos.getCarril() - 1;
      if (seg + 1 >= segmentos) {
        return new Pos(seg + 1, car + 1);
      }
      // Esperar hasta que el siguiente segmento esté libre
      while (ticksRestantes.get(id) > 0 || ocupacion[seg + 1][car] != null) {
        puedeMover.await();
      }
      ocupacion[seg][car] = null;
      ocupacion[seg + 1][car] = id;
      posiciones.put(id, new Pos(seg + 2, car + 1));
      ticksRestantes.put(id, tks);
      // Solo un signal por evento
      if (puedeMover.waiting() > 0) {
        puedeMover.signal();
      } else if (puedeCircular.waiting() > 0) {
        puedeCircular.signal();
      }
      return new Pos(seg + 2, car + 1);
    } finally {
      mutex.leave();
    }
  }

  /**
   * Un coche "circula" a lo largo del segmento en el que está. La
   * operación termina cuando el coche ha llegado al final del segmento.
   *
   * @param id identificador del coche
   */
  public void circulando(String id) {
    mutex.enter();
    try {
      while (ticksRestantes.get(id) > 0) {
        puedeCircular.await();
      }
    } finally {
      mutex.leave();
    }
  }

  /**
   * Un coche abandona el último segmento.
   *
   * @param id identificador del coche
   */
  public void salir(String id) {
    mutex.enter();
    try {
      Pos pos = posiciones.get(id);
      int seg = pos.getSegmento() - 1;
      int car = pos.getCarril() - 1;
      ocupacion[seg][car] = null;
      posiciones.remove(id);
      ticksRestantes.remove(id);
      // Solo un signal por evento
      if (puedeMover.waiting() > 0) {
        puedeMover.signal();
      } else if (puedeCircular.waiting() > 0) {
        puedeCircular.signal();
      }
    } finally {
      mutex.leave();
    }
  }

  /**
   * Hace avanzar el tiempo de forma que a cada coche en la carretera
   * le queda un tick menos para llegar al final de su segmento.
   */
  public void tick() {
    mutex.enter();
    try {
      boolean hayCambio = false;
      for (String id : ticksRestantes.keySet().toArray(new String[0])) {
        int t = ticksRestantes.get(id);
        if (t > 0) {
          ticksRestantes.put(id, t - 1);
          if (t - 1 == 0) hayCambio = true;
        }
      }
      if (hayCambio) {
        // broadcast circulando
        int nC = puedeCircular.waiting();
        for (int i = 0; i < nC; i++) {
          puedeCircular.signal();
          // cede monitor para que el hilo consumido procese el signal
          mutex.leave();
          mutex.enter();
        }
        // broadcast mover
        int nM = puedeMover.waiting();
        for (int i = 0; i < nM; i++) {
          puedeMover.signal();
          mutex.leave();
          mutex.enter();
        }
      }
    } finally {
      mutex.leave();
    }
  }
}
