// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementación del recurso compartido {@link Carretera} usando Monitores.
 * Gestiona la ocupación de segmentos y carriles, el avance de los coches
 * y el control de ticks (tiempo de circulación restante) de forma segura
 * para múltiples hilos.
 */
public class CarreteraMonitor implements Carretera {
  /* 
   * Matriz de ocupación [segmento][carril], almacena el id del coche o null si está libre
   * La matriz empieza en (0,0) y las posiciones empiezan en (1,1), luego a la hora imprimir por 
   * pantalla se le suma 1 a cada posición ( segmento, carril > 0 else illegalArgumentException)
   * ocupacion[0][0] -> segmento 1, carril 1
   */
  private final String[][] ocupacion;

  // Ticks restantes para cada coche
  private final Map<String, Integer> ticksRestantes; // ticks de circulación pendientes
  private final Map<String, Pos> posiciones; // posición actual (segmento, carril)

  // Mecanismo de sincronización
  private final Monitor mutex;  // monitor que protege toda la estructura
  private final Monitor.Cond puedeMover;  // condición para avanzar a siguiente segmento
  private final Monitor.Cond puedeCircular;  // condición para consumir ticks en el mismo segmento
  
  // Tamaño de la carretera
  private final int segmentos;  // número de segmentos en la carretera
  private final int carriles;  // número de carriles por segmento

  /**
   * Constructor.
   * Inicializa la matriz de ocupación a null (libre), así como los mapas y condiciones.
   * Crea una carretera con un número de segmentos y carriles.
   *
   * @param segmentos número de segmentos
   * @param carriles número de carriles en cada segmento
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

    // Marcamos todos los espacios como libres y nos aseguramos que son null todos los elementos 
    for (int i = 0; i < segmentos; i++)
      for (int j = 0; j < carriles; j++)
        ocupacion[i][j] = null;
  }

  /**
   * Busca un carril libre en el segmento indicado en la matriz ocupacion.
   *
   * @param seg índice de segmento
   * @return índice de carril libre, o -1 si todos están ocupados
   */
  private int buscarCarrilLibre(int seg) {
    for (int c = 0; c < carriles; c++) { 
      if (ocupacion[seg][c] == null) return c;
    }
    return -1;
  }

  /**
   * Permite a un coche entrar en el primer segmento -> seg=0
   * Si no hay hueco, espera hasta que otro coche avance o salga.
   *
   * @param id identificador único del coche
   * @param tks ticks iniciales para este segmento
   * @return posición asignada (segmento=1, carril)
   */
  public Pos entrar(String id, int tks) {
    mutex.enter();
    try {
      // si no hay hueco -> esperar
      while (buscarCarrilLibre(0) == -1) {
        puedeMover.await();
      }
      // Asigna el coche a un carril libre en el primer segmento del carril disponible
      // y lo marca como ocupado
      int carril = buscarCarrilLibre(0);
      ocupacion[0][carril] = id;
      posiciones.put(id, new Pos(1, carril + 1));
      ticksRestantes.put(id, tks);

      // Despierta a cualquier hilo que estuviera esperando para circular en su segmento
      puedeCircular.signal();
      return new Pos(1, carril + 1);
    } finally {
      mutex.leave();
    }
  }


  /**
   * Los coches avanzan al siguiente segmento -> seg+1 si tks = 0.
   * Si el coche ya está en el último segmento, no se mueve.
   *
   * @param id identificador del coche
   * @param tks ticks necesarios para el siguiente segmento
   * @return nueva posición (segmento, carril)
   */

  @Override
  public Pos avanzar(String id, int tks) {
    mutex.enter();
    try {
      Pos pos = posiciones.get(id);
      int seg = pos.getSegmento() - 1;
      int car = pos.getCarril() - 1;

      // Si ya está en el último segmento, no mueve
      if (seg + 1 >= segmentos) {
        return new Pos(seg + 1, car + 1);     // en matriz ocupacion [seg][carril]-> pos(seg+1, carril+1)
      }

      // Espera mientras queden ticks o no haya hueco en el siguiente segmento
      while (ticksRestantes.get(id) > 0
             || buscarCarrilLibre(seg + 1) == -1) {
        puedeMover.await();
      }

      // Elige un carril libre en el siguiente segmento (posible cambio de carril)
      int nuevoCarril = buscarCarrilLibre(seg + 1); // seg + 1 es el siguiente segmento ya que lo vemos en la matriz
      ocupacion[seg][car] = null;
      ocupacion[seg + 1][nuevoCarril] = id;
      posiciones.put(id, new Pos(seg + 2, nuevoCarril + 1));

      ticksRestantes.put(id, tks);

      // Despierta a otro hilo que pueda avanzar o circular
      if (puedeMover.waiting() > 0) {
        puedeMover.signal();
      } else if (puedeCircular.waiting() > 0) {
        puedeCircular.signal();
      }
      return new Pos(seg + 2, nuevoCarril + 1);

    } finally {
      mutex.leave();
    }
  }

  /**
   * Fase de circulación dentro de un mismo segmento.
   * El coche espera hasta consumir todos sus ticks antes de avanzar.
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
   * El coche abandona la carretera (último segmento).
   * Libera su carril y notifica a otros hilos en espera.
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
      
      // Notifica a un hilo que esté esperando avanzar o entrar
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
