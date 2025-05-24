// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;
import java.util.*;

/**
 * Implementación basada en Monitor de la interfaz {@link Carretera}.
 * Coordina de forma concurrente las operaciones de vehículos que
 * entran, avanzan, circulan, cambian de carril y salen en una carretera
 * compuesta por varios segmentos y carriles.
 *
 * @author Liu Yang
 * @author Alejandro Gragera
 * @see Carretera
 */
public class CarreteraMonitor implements Carretera {
  // Matriz de ocupación: ocupacion[segmento][carril] = identificador del coche o null
  private final String[][] ocupacion;
  // Número de ticks pendientes para cada coche
  private final Map<String,Integer> ticksRestantes;
  // Posición actual de cada coche: segmento y carril
  private final Map<String,Pos> posiciones;
  // Monitor y listas de peticiones aplazadas
  private final Monitor mutex;
  private final List<EntradaReq> pendingEntrar;       // peticiones de entrar
  private final List<AvanzarReq> pendingAvanzar;     // peticiones de avanzar
  private final List<CirculandoReq> pendingCirc;     // peticiones de circular
  private final List<CambiarCarrilReq> pendingCambiar;// peticiones de cambio de carril
  private final int segmentos, carriles;              // dimensiones de la carretera

  /**
   * Inicializa el Monitor para una carretera con los segmentos y carriles dados.
   *
   * @param segmentos número de segmentos consecutivos
   * @param carriles  número de carriles por segmento
   */
  public CarreteraMonitor(int segmentos, int carriles) {
    this.segmentos      = segmentos;
    this.carriles       = carriles;
    this.ocupacion      = new String[segmentos][carriles];
    this.ticksRestantes = new HashMap<>();
    this.posiciones     = new HashMap<>();
    this.mutex          = new Monitor();
    this.pendingEntrar  = new LinkedList<>();
    this.pendingAvanzar = new LinkedList<>();
    this.pendingCirc    = new LinkedList<>();
    this.pendingCambiar = new LinkedList<>();
    // Librar todos los carriles al inicio
    for (int i = 0; i < segmentos; i++) {
      Arrays.fill(this.ocupacion[i], null);
    }
  }

  /**
   * Busca un carril libre en un segmento dado.
   *
   * @param seg índice del segmento (0-based)
   * @return índice del carril libre, o -1 si ninguno está disponible
   */
  private int buscarCarrilLibre(int seg) {
    for (int c = 0; c < carriles; c++) {
      if (ocupacion[seg][c] == null) return c;
    }
    return -1;
  }

  /**
   * Busca otro carril distinto al actual que esté libre en el mismo segmento.
   *
   * @param seg    índice del segmento (0-based)
   * @param actual índice del carril actual (0-based)
   * @return índice de otro carril libre, o -1 si no hay disponible
   */
  private int buscarOtroCarrilLibre(int seg, int actual) {
    for (int c = 0; c < carriles; c++) {
      if (c != actual && ocupacion[seg][c] == null) return c;
    }
    return -1;
  }

  // Clases internas para indexación de clientes (peticiones aplazadas)

  /** Petición de entrada al primer segmento (almacena id, ticks y su condición). */
  private class EntradaReq {
    final String id; final int tks; final Monitor.Cond cond;
    EntradaReq(String id, int tks) {
      this.id   = id;
      this.tks  = tks;
      this.cond = mutex.newCond();
    }
  }

  /** Petición de avance de segmento (almacena id, ticks, segmento y carril). */
  private class AvanzarReq {
    final String id; final int tks; final int seg, car; final Monitor.Cond cond;
    AvanzarReq(String id, int tks, int seg, int car) {
      this.id   = id;
      this.tks  = tks;
      this.seg  = seg;
      this.car  = car;
      this.cond = mutex.newCond();
    }
  }

  /** Petición de circulación (espera a que los ticks lleguen a cero). */
  private class CirculandoReq {
    final String id; final Monitor.Cond cond;
    CirculandoReq(String id) {
      this.id   = id;
      this.cond = mutex.newCond();
    }
  }

  /** Petición de cambio de carril (espera ticks a cero y disponibilidad de otro carril). */
  private class CambiarCarrilReq {
    final String id; final Monitor.Cond cond;
    CambiarCarrilReq(String id) {
      this.id   = id;
      this.cond = mutex.newCond();
    }
  }

  // Métodos de señalización para despertar peticiones pendientes

  /** Despierta la primera petición de entrar si hay carril libre en el segmento 0. */
  private void señalizarEntrar() {
    Iterator<EntradaReq> it = pendingEntrar.iterator();
    while (it.hasNext()) {
      EntradaReq r = it.next();
      if (buscarCarrilLibre(0) >= 0) {
        r.cond.signal();
        it.remove();
        return;
      }
    }
  }

  /** Despierta la primera petición de avanzar si cumple ticks==0 y hay hueco. */
  private void señalizarAvanzar() {
    Iterator<AvanzarReq> it = pendingAvanzar.iterator();
    while (it.hasNext()) {
      AvanzarReq r = it.next();
      if (ticksRestantes.get(r.id) == 0 &&
          buscarCarrilLibre(r.seg + 1) >= 0) {
        r.cond.signal();
        it.remove();
        return;
      }
    }
  }

  /** Despierta la primera petición de circular cuando ticks==0. */
  private void señalizarCirculando() {
    Iterator<CirculandoReq> it = pendingCirc.iterator();
    while (it.hasNext()) {
      CirculandoReq r = it.next();
      if (ticksRestantes.get(r.id) == 0) {
        r.cond.signal();
        it.remove();
        return;
      }
    }
  }

  /** Despierta la primera petición de cambio de carril que pueda ejecutarse. */
  private void señalizarCambiar() {
    Iterator<CambiarCarrilReq> it = pendingCambiar.iterator();
    while (it.hasNext()) {
      CambiarCarrilReq r = it.next();
      Pos p = posiciones.get(r.id);
      int seg = p.getSegmento() - 1, car = p.getCarril() - 1;
      if (ticksRestantes.get(r.id) == 0 &&
          buscarOtroCarrilLibre(seg, car) >= 0) {
        r.cond.signal();
        it.remove();
        return;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public Pos entrar(String id, int tks) {
    mutex.enter();
    try {
      int c = buscarCarrilLibre(0);
      if (c < 0) {
        EntradaReq req = new EntradaReq(id, tks);
        pendingEntrar.add(req);
        req.cond.await();  // se bloquea hasta que haya hueco
        c = buscarCarrilLibre(0);
      }
      ocupacion[0][c] = id;
      posiciones.put(id, new Pos(1, c + 1));
      ticksRestantes.put(id, tks);
      señalizarEntrar();
      señalizarAvanzar();
      señalizarCirculando();
      señalizarCambiar();
      return new Pos(1, c + 1);
    } finally {
      mutex.leave();
    }
  }

  /** {@inheritDoc} */
  @Override
  public Pos avanzar(String id, int tks) {
    mutex.enter();
    try {
      Pos p = posiciones.get(id);
      int seg = p.getSegmento() - 1, car = p.getCarril() - 1;
      // Si está en el último segmento, sale de la carretera
      if (seg + 1 >= segmentos) {
        ocupacion[seg][car] = null;
        posiciones.remove(id);
        ticksRestantes.remove(id);
        señalizarEntrar();
        señalizarAvanzar();
        señalizarCirculando();
        señalizarCambiar();
        return p;
      }
      // Si aún tiene ticks o no hay hueco, se bloquea en pendingAvanzar
      if (ticksRestantes.get(id) > 0 ||
          buscarCarrilLibre(seg + 1) < 0) {
        AvanzarReq req = new AvanzarReq(id, tks, seg, car);
        pendingAvanzar.add(req);
        req.cond.await();
      }
      // Avanza de segmento y reinicia ticks
      int nc = buscarCarrilLibre(seg + 1);
      ocupacion[seg][car] = null;
      ocupacion[seg + 1][nc] = id;
      posiciones.put(id, new Pos(seg + 2, nc + 1));
      ticksRestantes.put(id, tks);
      señalizarEntrar();
      señalizarAvanzar();
      señalizarCirculando();
      señalizarCambiar();
      return new Pos(seg + 2, nc + 1);
    } finally {
      mutex.leave();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void circulando(String id) {
    mutex.enter();
    try {
      // Si aún quedan ticks, se bloquea hasta consumirse
      if (ticksRestantes.get(id) > 0) {
        CirculandoReq req = new CirculandoReq(id);
        pendingCirc.add(req);
        req.cond.await();
      }
      señalizarEntrar();
      señalizarAvanzar();
      señalizarCirculando();
      señalizarCambiar();
    } finally {
      mutex.leave();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void salir(String id) {
    mutex.enter();
    try {
      Pos p = posiciones.remove(id);
      int seg = p.getSegmento() - 1, car = p.getCarril() - 1;
      ocupacion[seg][car] = null;
      ticksRestantes.remove(id);
      señalizarEntrar();
      señalizarAvanzar();
      señalizarCirculando();
      señalizarCambiar();
    } finally {
      mutex.leave();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void tick() {
    mutex.enter();
    try {
      boolean huboCambio = false;
      for (String id : ticksRestantes.keySet().toArray(new String[0])) {
        int t = ticksRestantes.get(id);
        if (t > 0) {
          ticksRestantes.put(id, t - 1);
          if (t - 1 == 0) huboCambio = true;
        }
      }
      if (huboCambio) {
        señalizarCirculando();
        señalizarEntrar();
        señalizarAvanzar();
        señalizarCambiar();
      }
    } finally {
      mutex.leave();
    }
  }

  /**
   * Cambia de carril dentro del mismo segmento cuando
   * haya 0 ticks rest
   * antes y exista otro carril libre.
   *
   * @param id identificador del coche
   * @return posición tras el cambio (mismo segmento, nuevo carril)
   */
  public Pos cambiarCarril(String id) {
    mutex.enter();
    try {
      Pos p = posiciones.get(id);
      int seg = p.getSegmento() - 1, car = p.getCarril() - 1;
      if (ticksRestantes.get(id) > 0 ||
          buscarOtroCarrilLibre(seg, car) < 0) {
        CambiarCarrilReq req = new CambiarCarrilReq(id);
        pendingCambiar.add(req);
        req.cond.await();
        // Recalcular posición tras despertar
        p = posiciones.get(id);
        seg = p.getSegmento() - 1; car = p.getCarril() - 1;
      }
      // Ejecutar el cambio de carril
      int nc = buscarOtroCarrilLibre(seg, car);
      ocupacion[seg][car] = null;
      ocupacion[seg][nc]  = id;
      posiciones.put(id, new Pos(seg + 1, nc + 1));
      señalizarEntrar();
      señalizarAvanzar();
      señalizarCirculando();
      señalizarCambiar();
      return new Pos(seg + 1, nc + 1);
    } finally {
      mutex.leave();
    }
  }
}
