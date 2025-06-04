package cc.carretera;

import org.jcsp.lang.*;

import java.nio.channels.Channel;
import java.security.Guard;
import java.util.*;

public class CarreteraCSP implements Carretera, CSProcess {

    // Canales para recibir peticiones de los coches (entrar, avanzar, etc.)
    private final Any2OneChannel entrarCh = Channel.any2one();      // Peticiones de entrada a la carretera
    private final Any2OneChannel avanzarCh = Channel.any2one();     // Peticiones para avanzar al siguiente segmento
    private final Any2OneChannel circulandoCh = Channel.any2one();  // Peticiones para informar que siguen circulando
    private final Any2OneChannel salirCh = Channel.any2one();       // Peticiones para salir de la carretera
    private final Any2OneChannel tickCh = Channel.any2one();        // Peticiones para avanzar el tiempo (ticks)

    // Estado interno de la carretera
    private final int segmentos, carriles; // Numero de segmentos y carriles de la carretera
    private final String[][] ocupacion; // Matriz que indica que coche ocupa cada posicion
    private final Map<String, Pos> posiciones = new HashMap<>(); // Posicion actual de cada coche
    private final Map<String, Integer> ticksRem = new HashMap<>(); // Ticks restantes para cada coche

    // Colas de peticiones aplazadas, las que no se pueden atender inmediatamente
    // Si una peticion no se puede atender (eg: no hay hueco), se guarda aqui para reintentarse mas tarde.
    private final Queue<EntrarMsg> pendingEntrar = new LinkedList<>();
    private final Queue<AvanzarMsg> pendingAvanzar = new LinkedList<>();
    private final Queue<CirculandoMsg> pendingCirc = new LinkedList<>();

    public CarreteraCSP(int segmentos, int carriles) {
        this.segmentos = segmentos;
        this.carriles = carriles;
        this.ocupacion = new String[segmentos][carriles];
        new ProcessManager(this).start(); // Lanza el proceso que gestiona la carretera
    }

    // Vamos a definir los mensajes que se envian por los csnales
    // Cada mensaje representa una peticion de un coche y puede incluir datos y un canal de respuesta.

    // Mensajes de peticion
    private static class EntrarMsg {
        final String id;
        final int tks;
        final ChannelOutput resp;
        EntrarMsg(String id, int tks, ChannelOutput resp) {
            this.id = id;
            this.tks = tks;
            this.resp = resp;
        }
    }
    // Mensaje para pedir avanzar al siguiente segmento   
    private static class AvanzarMsg {
        final String id;
        final int tks;
        final ChannelOutput resp;
        AvanzarMsg(String id, int tks, ChannelOutput resp) {
            this.id = id;
            this.tks = tks;
            this.resp = resp;
        }
    }
    // Mensaje para informar que el coche sigue circulando y preguntar si ya puede avanzar
    private static class CirculandoMsg {
        final String id;
        final ChannelOutput resp;
        CirculandoMsg(String id, ChannelOutput resp) {
            this.id = id;
            this.resp = resp;
        }
    }


    // Mensajes para solicitar salir de la carretera
    private static class SalirMsg { final String id; SalirMsg(String id) { this.id = id; } }
    private static class TickMsg { }


    // Aqui vamosa definir los metodos de cliente

    /**
     * Solicita entrar en la carretera. El coche espera hasta recibir la posicion asignada.
     * @param id  Identificador del coche
     * @param tks Ticks que debe esperar antes de poder avanzar
     * @return    Posicion inicial asignada al coche
     */
    public Pos entrar(String id, int tks) {
        // Solicita entrar y espera la posicion asignada
        One2OneChannel resp = Channel.one2one();
        entrarCh.out().write(new EntrarMsg(id, tks, resp.out()));
        return (Pos) resp.in().read();
    }    
    
    
    /**
     * Solicita avanzar al siguiente segmento. El coche espera hasta recibir la nueva posicion.
     * @param id  Identificador del coche
     * @param tks Ticks que debe esperar antes de poder volver a avanzar
     * @return    Nueva posicion asignada al coche
     */
    public Pos avanzar(String id, int tks) {
        // Solicita avanzar y espera la nueva posicion asignada
        One2OneChannel resp = Channel.one2one();
        avanzarCh.out().write(new AvanzarMsg(id, tks, resp.out()));
        return (Pos) resp.in().read();
    }


    /**
     * Informa que el coche sigue circulando y espera confirmacion para poder avanzar.
     * @param id Identificador del coche
     */
    public void circulando(String id) {
        // Informa que sigue circulando y espera confirmacion
        One2OneChannel resp = Channel.one2one();
        circulandoCh.out().write(new CirculandoMsg(id, resp.out()));
        resp.in().read();
    }

     /**
     * Solicita salir de la carretera.
     * @param id Identificador del coche
     */
    public void salir(String id) {
        // Solicita salir de la carretera
        salirCh.out().write(new SalirMsg(id));
    }

    /**
     * Solicita avanzar el tiempo (tick), lo que reduce los ticks restantes de todos los coches.
     */
    public void tick() {
        // Avanza el tiempo, reduce ticks restantes de cada coche
        tickCh.out().write(new TickMsg());
    }


    /**
     * buscar un carril libre en un segmento concreto
     * @param id Identificador del coche
     * @return El indice del primer carril libre en el segmento dado, o -1 si no hay ninguno.
     */
    private int buscarCarrilLibre(int seg) {
        for (int c = 0; c < carriles; c++) {
            if (ocupacion[seg][c] == null) return c;
        }
        return -1; // No hay carril libre en el segmento dado
    }

    
    /**
     * Atiende todas las peticiones de los coches y gestiona el estado.
     */
    public void run() {
        Guard[] guards = new Guard[]{
            // Array de guards para esperar peticiones en los canales
            entrarCh.in(), avanzarCh.in(), salirCh.in(), circulandoCh.in(), tickCh.in()
        };
        Alternative alt = new Alternative(guards); // Permite seleccionar entre varias peticiones
        
        while (true) {
            int sel = alt.fairSelect(); // Espera una peticion y selecciona cuál atender
            switch (sel) {
                case 0: { // Peticion de entrar
                    EntrarMsg m = (EntrarMsg) entrarCh.in().read();
                    if (!procesarEntrar(m)) pendingEntrar.add(m);  // Si no se puede, se aplaza
                    break;
                }
                case 1: { // Peticion de avanzar
                    AvanzarMsg m = (AvanzarMsg) avanzarCh.in().read();
                    if (!procesarAvanzar(m)) pendingAvanzar.add(m);
                    break;
                }
                case 2: { // Peticion de salir
                    SalirMsg m = (SalirMsg) salirCh.in().read();
                    procesarSalir(m.id);
                    break;
                }
                case 3: { // Peticion de circulando
                    CirculandoMsg m = (CirculandoMsg) circulandoCh.in().read();
                    if (!procesarCirculando(m)) pendingCirc.add(m);
                    break;
                }
                case 4: { // Tick: avanza el tiempo para todos los coches
                    tickCh.in().read();
                    // Reduce en 1 los ticks restantes de todos los coches (sin bajar de 0)
                    ticksRem.keySet().forEach(id ->
                        ticksRem.put(id, Math.max(0, ticksRem.get(id) - 1))
                    );
                    break;
                }
            }
            // Tras cualquier evento, intentar despachar las peticiones aplazadas
            // Esto permite que peticiones que antes no se podian atender (por ejemplo, por falta de hueco)
            // se vuelvan a intentar despues de cada cambio de estado.
            boolean progreso;
            do {
                progreso = false;
                // Intentamos despachar peticiones pendientes de entrar
                Iterator<EntrarMsg> itE = pendingEntrar.iterator();
                while (itE.hasNext()) {
                    if (procesarEntrar(itE.next())) { itE.remove(); progreso = true; break; }
                }
                // Intentamos despachar peticiones pendientes de avanzar
                Iterator<AvanzarMsg> itA = pendingAvanzar.iterator();
                while (itA.hasNext()) {
                    if (procesarAvanzar(itA.next())) { itA.remove(); progreso = true; break; }
                }
                // Intentamos despachar peticiones pendientes de circulando
                Iterator<CirculandoMsg> itC = pendingCirc.iterator();
                while (itC.hasNext()) {
                    if (procesarCirculando(itC.next())) { itC.remove(); progreso = true; break; }
                }
            } while (progreso); // Repite mientras se pueda avanzar
        }
    }

    // Aqui estan funciones para procesar cada tipo de peticion

    /**
     * Procesamiento de cada operacion
     * Intenta procesar una peticion de entrar en la carretera.
     * Si hay hueco en el primer segmento, asigna un carril al coche, guarda su posicion y ticks,
     * y responde al coche con la posicion asignada.
     * @param m Mensaje de entrada que contiene el id del coche, ticks y canal de respuesta
     * @return Devuelve true si la peticion fue atendida, false si no hay hueco y debe esperar.
     */    
    private boolean procesarEntrar(EntrarMsg m) {
        int carril = buscarCarrilLibre(0);
        if (carril < 0) return false;  // Si no hay hueco, no se puede atender ahora
        ocupacion[0][carril] = m.id;
        posiciones.put(m.id, new Pos(1, carril+1));
        ticksRem.put(m.id, m.tks);
        m.resp.write(new Pos(1, carril+1));
        return true;
    }

    /**
     * Intenta procesar una peticion de avanzar
     * Si el coche puede avanzar (ha esperado los ticks necesarios y hay hueco delante),
     * lo mueve al siguiente segmento o lo saca de la carretera si ya está al final.
     * @param m Mensaje de avance que contiene el id del coche, ticks y canal de respuesta
     * @return  devuelve true si la peticion fue atendida, false si no hay hueco o debe esperar.
     */
    private boolean procesarAvanzar(AvanzarMsg m) {
        Pos p = posiciones.get(m.id);               // Obtenemos la posicion actual del coche
        if (p == null) return false; 
        if (ticksRem.get(m.id) > 0) return false;   // Debe esperar más ticks

        int seg = p.getSegmento() - 1;
        int next = seg + 1;

        // Si llega al final, sale de la carretera
        if (next >= segmentos) {
            ocupacion[seg][p.getCarril()-1] = null;         // Liberamos la posicion actual
            posiciones.remove(m.id);
            ticksRem.remove(m.id);
            m.resp.write(new Pos(next+1, p.getCarril()));   // Respondemos con la nueva posicion (fuera de la carretera)
            return true;
        }

        // Si hay hueco en el siguiente segmento, movemos el coche
        int carril = buscarCarrilLibre(next);
        if (carril < 0) return false;                       // No hay hueco en el siguiente segmento

        ocupacion[seg][p.getCarril()-1] = null;             // Liberamos la posicion actual
        ocupacion[next][carril] = m.id;                     // Ocupamos la nueva posicion
        posiciones.put(m.id, new Pos(next+1, carril+1));
        ticksRem.put(m.id, m.tks);                          // Reiniciamos los ticks de espera para el siguiente avance
        m.resp.write(new Pos(next+1, carril+1));
        return true;
    }

    /**
     * Procesa una peticion de "circulando", es decir, el coche pregunta si ya puede avanzar.
     * Si ya ha esperado los ticks necesarios, se le responde para que pueda avanzar.
     * @param m Mensaje de circulando que contiene el id del coche y canal de respuesta
     * @return  Devuelve true si la peticion fue atendida, false si debe seguir esperando.
     */
    private boolean procesarCirculando(CirculandoMsg m) {
        Integer t = ticksRem.get(m.id);
        if (t != null && t <= 0) {
            m.resp.write(null);  // Ya puede avanzar
            return true;
        }
        return false;
    }

    /**
     * Procesa la salida de un coche: libera su posicion en la carretera y elimina su informacion.
     * @param id Identificador del coche que sale
     */
    private void procesarSalir(String id) {
        Pos p = posiciones.remove(id);                              // Quitamos la posicion del coche
        if (p != null) {
            ocupacion[p.getSegmento()-1][p.getCarril()-1] = null;   // Liberamos la posicion en la matriz
            ticksRem.remove(id);                                    // Eliminamos los ticks pendientes
        }
    }
}
