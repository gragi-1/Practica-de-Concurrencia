/*
 * Simulates a carretera in a GUI window.
 *
 */
package cc.carretera;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTextArea;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.border.LineBorder;

import java.util.Map;
import java.util.HashMap;
import java.awt.Color;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JTextField;
import javax.swing.Box;
import javax.swing.JLabel;
import java.awt.FlowLayout;
import javax.swing.JScrollPane;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.SwingWorker;
import java.awt.Component;
import javax.swing.JCheckBox;
import java.util.function.Supplier;


public class CarreteraSim {

  // Dimensions
  int segmentos;
  int carriles;

  // Random number generation
  Random rnd;

  // GUI state
  private JFrame frmCarreterasim;
  JTextArea callsTextArea;
  JLabel[][] carretera;
  int[][] tks;
  String[][] cars;
  
  // JLabel timeLab;

  // Current time
  // int time = 0;

  // Simulation
  Sim sim;

  // For sending messages to simulation from GUI
  BlockingQueue<Integer> tickQueue;

  // Manually step ticks or not
  boolean stepTicks = false;

  // Current generation -- we keep a count of the number of times
  // the simulation was started to keep from displaying spurious messages
  int generation = 0;


  /**
   * Launch the application.
   */
  public static void main(String[] args) {
    EventQueue.invokeLater(new Runnable() {
        public void run() {
          try {
            CarreteraSim window = new CarreteraSim();
            window.frmCarreterasim.setVisible(true);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      });
  }

  /**
   * Create the application.
   */
  public CarreteraSim() {
    initialize();
  }

  /**
   * Setup the GUI.
   */
  private void initialize() {
    rnd = new Random();
    segmentos = 2+rnd.nextInt(4);
    carriles = 2+rnd.nextInt(2);

    frmCarreterasim = new JFrame();
    frmCarreterasim.setTitle("CarreteraSim");
    frmCarreterasim.setBounds(100, 100, Math.max(500,200+100*segmentos), Math.max(500,300+carriles*100));
    frmCarreterasim.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // JLabel lblTime = new JLabel("Time:");
    // JLabel timeLab = new JLabel("0"); this.timeLab = timeLab;
    JCheckBox stepTicksBox = new JCheckBox("Step ticks",true);

    JButton btnDoTimeTick = new JButton("Tick");
    btnDoTimeTick.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            tickQueue.put(1);
          } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
          }
        }
      });
    btnDoTimeTick.setEnabled(false);
    carretera = new JLabel[segmentos][carriles];
    tks = new int[segmentos][carriles];
    cars = new String[segmentos][carriles];
    for (int segmento=0; segmento<segmentos; segmento++)
      for (int carril=0; carril<carriles; carril++) {
        carretera[segmento][carril] = new JLabel("--------");
        tks[segmento][carril] = 0;
        cars[segmento][carril] = null;
      }

    JPanel panel_options = new JPanel();
    JPanel panel_carretera = new JPanel();
    panel_carretera.setBorder(new LineBorder(new Color(0, 0, 0)));

    JPanel panel_actions = new JPanel();
    JPanel panel_calls = new JPanel();

    JTextField txtCalls = new JTextField();
    txtCalls.setEditable(false);
    txtCalls.setText("Calls:");
    txtCalls.setColumns(10);

    callsTextArea = new JTextArea();
    callsTextArea.setColumns(20);
    callsTextArea.setRows(20);
    JScrollPane callsTextAreaSP = new JScrollPane(callsTextArea);

    JButton btnQuit = new JButton("Quit");
    btnQuit.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          frmCarreterasim.dispose();
          System.exit(0);
        }
      });

    JButton btnPauseSim = new JButton("Pause simulation");
    btnPauseSim.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          try {
            tickQueue.put(-1);
            if (btnPauseSim.getText().equals("Pause simulation"))
              btnPauseSim.setText("Restart simulation");
            else
              btnPauseSim.setText("Pause simulation");
          } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
          }
        }
      });
    btnPauseSim.setEnabled(false);

    JButton btnStartSim = new JButton("Start simulation");
    final CarreteraSim win = this;
    btnStartSim.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {

          ++generation;

          if (sim != null && tickQueue != null) {
            try {
              tickQueue.put(-10);
            } catch (InterruptedException e1) {
              // TODO Auto-generated catch block
              e1.printStackTrace();
            }
          }

          tickQueue = new LinkedBlockingQueue<Integer>();
          // time = 0;
          // timeLab.setText(Integer.valueOf(time).toString());
          sim = new Sim(win,rnd,generation,tickQueue,segmentos,carriles);
          stepTicks = stepTicksBox.isSelected();
          btnDoTimeTick.setEnabled(stepTicks);
          btnPauseSim.setEnabled(!stepTicks);
          btnPauseSim.setText("Pause simulation");

          for (JLabel[] lblRow : carretera)
            for (JLabel lbl : lblRow)
              lbl.setText("--------");
          for (int segmento=0; segmento<segmentos; segmento++)
            for (int carril=0; carril<carriles; carril++) {
              tks[segmento][carril] = 0;
              cars[segmento][carril] = null;
            }
          callsTextArea.setText("");

          sim.execute();
        }
      });

    // Top panel layout
    GroupLayout gl_top = new GroupLayout(frmCarreterasim.getContentPane());
    gl_top.setAutoCreateGaps(true);
    gl_top.setAutoCreateContainerGaps(true);
    gl_top.setHorizontalGroup
      (
       gl_top.createParallelGroup(Alignment.CENTER)
       .addComponent(panel_calls)
       .addComponent(panel_actions)
       .addComponent(panel_options)
       .addComponent(panel_carretera)
       );

    gl_top.setVerticalGroup
      (
       gl_top.createSequentialGroup()
       .addComponent(panel_options)
       .addComponent(panel_carretera)
       .addComponent(panel_calls)
       .addComponent(panel_actions)
       );


    // Panel 1: carretera

    GroupLayout gl_panel_carretera = new GroupLayout(panel_carretera);
    GroupLayout.ParallelGroup[] horizontalGroups = new GroupLayout.ParallelGroup[segmentos];
    GroupLayout.ParallelGroup[] verticalGroups = new GroupLayout.ParallelGroup[carriles];

    for (int segmento=0; segmento<segmentos; segmento++) {
      GroupLayout.ParallelGroup g =
        gl_panel_carretera.createParallelGroup(Alignment.LEADING);
      horizontalGroups[segmento] =
        g;
      for (int carril=0; carril<carriles; carril++)
        g.addComponent(carretera[segmento][carril], GroupLayout.DEFAULT_SIZE, 100, Short.MAX_VALUE);
    }

    for (int carril=carriles-1; carril>=0; carril--) {
      GroupLayout.ParallelGroup g =
        gl_panel_carretera.createParallelGroup(Alignment.BASELINE);
      verticalGroups[carril] =
        g;
      for (int segmento=0; segmento<segmentos; segmento++)
        g.addComponent(carretera[segmento][carril], GroupLayout.DEFAULT_SIZE, 30, Short.MAX_VALUE);
    }

    gl_panel_carretera.setAutoCreateGaps(true);
    gl_panel_carretera.setAutoCreateContainerGaps(true);
    GroupLayout.SequentialGroup horizontalGroup =
      gl_panel_carretera.createSequentialGroup();
    for (int segmento=0; segmento<segmentos; segmento++)
      horizontalGroup.addGroup(horizontalGroups[segmento]);
    gl_panel_carretera.setHorizontalGroup(horizontalGroup);

    GroupLayout.SequentialGroup verticalGroup =
      gl_panel_carretera.createSequentialGroup();
    for (int carril=carriles-1; carril>=0; carril--)
      verticalGroup.addGroup(verticalGroups[carril]);
    gl_panel_carretera.setVerticalGroup(verticalGroup);
    panel_carretera.setLayout(gl_panel_carretera);


    // Panel time_options: time and time tick option

    GroupLayout gl_panel_options = new GroupLayout(panel_options);
    gl_panel_options.setAutoCreateGaps(true);
    gl_panel_options.setAutoCreateContainerGaps(true);
    gl_panel_options.setHorizontalGroup
      (
       gl_panel_options.createSequentialGroup()
       //.addComponent(lblTime)
       //.addComponent(timeLab)
       //.addPreferredGap(ComponentPlacement.UNRELATED)
       .addGap(150)
       .addComponent(stepTicksBox)
       );
    gl_panel_options.setVerticalGroup
      (
       gl_panel_options.createParallelGroup(Alignment.BASELINE)
       //.addComponent(lblTime)
       //.addComponent(timeLab)
       .addComponent(stepTicksBox)
       );
    panel_options.setLayout(gl_panel_options);



    // Panel actions: tick button, start simulation and quit

    GroupLayout gl_panel_actions = new GroupLayout(panel_actions);
    gl_panel_actions.setAutoCreateGaps(true);
    gl_panel_actions.setAutoCreateContainerGaps(true);
    gl_panel_actions.setHorizontalGroup
      (
       gl_panel_actions.createSequentialGroup()
       .addComponent(btnDoTimeTick)
       .addPreferredGap(ComponentPlacement.UNRELATED)
       .addComponent(btnPauseSim)
       .addComponent(btnStartSim)
       .addPreferredGap(ComponentPlacement.UNRELATED)
       .addComponent(btnQuit)
       );
    gl_panel_actions.setVerticalGroup
      (
       gl_panel_actions.createParallelGroup(Alignment.BASELINE)
       .addComponent(btnQuit)
       .addComponent(btnPauseSim)
       .addComponent(btnStartSim)
       .addComponent(btnDoTimeTick)
       );
    panel_actions.setLayout(gl_panel_actions);


    // Panel calls: calls text window and label

    GroupLayout gl_panel_calls = new GroupLayout(panel_calls);
    gl_panel_calls.setAutoCreateGaps(true);
    gl_panel_calls.setAutoCreateContainerGaps(true);

    gl_panel_calls.setHorizontalGroup
      (
       gl_panel_calls.createParallelGroup(Alignment.LEADING)
       .addComponent(txtCalls)
       .addComponent(callsTextAreaSP)
       );
    gl_panel_calls.setVerticalGroup
      (
       gl_panel_calls.createSequentialGroup()
       .addComponent(txtCalls, GroupLayout.PREFERRED_SIZE, 20, GroupLayout.PREFERRED_SIZE)
       .addComponent(callsTextAreaSP, GroupLayout.PREFERRED_SIZE, 200, GroupLayout.PREFERRED_SIZE)
       );
    panel_calls.setLayout(gl_panel_calls);


    // Set layout on top content pane

    frmCarreterasim.getContentPane().setLayout(gl_top);
  }
}


/*
 * Run the simulation. Since we can change the GUI only in a single
 * thread the simulation is run as a "SwingWorker",
 * and we send simulation events
 * back to the GUI theread.
 */


class Sim extends SwingWorker<Void,Object> {

  // Simulation cars
  String[] cars = {"vw", "seat", "volvo", "toyota", "fiat", "ford", "citroen", "porsche"};

  // Car velocicities (lower is faster!)
  Map<String,Integer> velocidades;

  // Main application state (including GUI)
  CarreteraSim cs;

  // Current generation
  int generation;

  // Messages from GUI
  BlockingQueue<Integer> tickQueue;

  // Random state
  Random rnd;

  // Dimensions of carretera
  int segmentos;
  int carriles;

  int time = 0;


  Sim(CarreteraSim cs, Random rnd, int generation, BlockingQueue<Integer> tickQueue, int segmentos, int carriles) {
    this.cs = cs;
    this.generation = generation;
    this.tickQueue = tickQueue;
    this.segmentos = segmentos;
    this.carriles = carriles;
    this.rnd = rnd;

    // Set car velocities (vw is punished for "dieselgate"...)
    this.velocidades = new HashMap<>();
    velocidades.put("vw",4); velocidades.put("seat",3); velocidades.put("volvo",1);
    velocidades.put("toyota",1); velocidades.put("fiat",2); velocidades.put("ford",1);
    velocidades.put("citroen",2); velocidades.put("porsche",3);
  }

  // Remove a car from the GUI
  static void removeCar(CarreteraSim cs, String car) {
    for (int i=0; i<cs.carretera.length; i++)
      for (int j=0; j<cs.carretera[0].length; j++)
        if (cs.carretera[i][j].getText().startsWith(car)) {
          cs.carretera[i][j].setText("--------");
          cs.cars[i][j] = null;
          cs.tks[i][j] = 0;
        }
  }

  // Handles the GUI updates resulting from simulation events
  @Override
  protected void process(List<Object> messages) {
    for (Object preMsg : messages) {

      // A message sent?
      if (preMsg instanceof String) {
        String str = (String) preMsg;
        cs.callsTextArea.append(str+"\n");
        System.out.println(str);
      }

      // Else we must have been sent a call and a generation
      else if (preMsg instanceof CallAndGeneration) {
        CallAndGeneration msg = (CallAndGeneration) preMsg;
        if (msg.generation == cs.generation) {
          SimCall call = msg.call;

          // Call raised an exception?
          if (call.raisedException) {
            String str = "\n*** Error: exception thrown:\n"+call.exception;
            System.out.println(str);
            call.exception.printStackTrace();
            cs.callsTextArea.append(str+"\n");
            for (StackTraceElement e : call.exception.getStackTrace()) {
              cs.callsTextArea.append(e+"\n");
            }
          }

          // Call failed?
          else if (call.failed) {
            String str = "\n*** Error: "+call.failMessage;
            cs.callsTextArea.append(str+"\n");
            System.out.println(str);
          }

          // Call returned normally
          else {
            String str = "";
            if (call.returnTime != -1 && (call.returnTime != call.time)) {
              str = "time "+call.returnTime+":  "+call.toString() + " [started at time "+call.time+"]";
            } else {
              str = "time "+call.time+":  "+call.toString();
            }
            cs.callsTextArea.append(str+"\n");
            System.out.println(str);

            if (call.name.equals("entrar") && call.returned) {
              Pos pos = call.result;
              int segmento = pos.getSegmento()-1;
              int carril = pos.getCarril()-1;
              cs.cars[segmento][carril] = call.car;
              cs.tks[segmento][carril] = call.velocidad;
              JLabel lbl = cs.carretera[segmento][carril];
              lbl.setText(call.car+"@"+Integer.valueOf(call.velocidad));
            } else if (call.name.equals("avanzar") && call.returned) {
              Pos pos = call.result;
              int segmento = pos.getSegmento()-1;
              int carril = pos.getCarril()-1;
              cs.cars[segmento][carril] = call.car;
              cs.tks[segmento][carril] = call.velocidad;
              removeCar(cs,call.car);
              JLabel lbl = cs.carretera[segmento][carril];
              lbl.setText(call.car+"@"+Integer.valueOf(call.velocidad));
            } else if (call.name.equals("salir") && call.returned) {
              removeCar(cs,call.car);
            } else if (call.name.equals("tick") && call.returned) {
              for (int segmento=0; segmento<segmentos; segmento++)
                for (int carril=0; carril<carriles; carril++) {
                  if (cs.tks[segmento][carril] > 0) {
                    --cs.tks[segmento][carril];
                    JLabel lbl = cs.carretera[segmento][carril];
                    lbl.setText(cs.cars[segmento][carril]+"@"+Integer.valueOf(cs.tks[segmento][carril]));
                  }
                }
            }
          }
        }

      } else {
        String str = "\n*** Internal error: unknown message "+preMsg+" received";
        cs.callsTextArea.append(str+"\n");
        System.out.println(str);
      }
    }
  }

  // Main simulation thread
  @Override
  protected Void doInBackground() throws Exception {
    boolean stepTicks = cs.stepTicks;
    AtomicBoolean terminated = new AtomicBoolean(false);

    // Shuffle cars
    for (int i=0; i<cars.length*5; i++) {
      int one = rnd.nextInt(cars.length);
      int two = rnd.nextInt(cars.length);
      String carOne = cars[one];
      cars[one] = cars[two];
      cars[two] = carOne;
    }

    // Invoke the monitor
    Carretera crPre = null;

    try {
      // crPre = new CarreteraCSP(segmentos,carriles);
      crPre = new CarreteraMonitor(segmentos,carriles);
    } catch (Throwable exc) {
      String str =
        "\n*** Error: calling CarreteraMonitor("+segmentos+","+carriles+") raised the exception "+exc;
      for (StackTraceElement e : exc.getStackTrace())
        str += e.toString()+"\n";
      publish(str);
      return null;
    }

    // This strange looking code is to pass a Java check that variables used in
    // lambda expressions must be final or effectively final. Since crPre is set
    // in the try it does not pass the test (even if we assign crPre also in the catch part)
    Carretera cr = crPre;

    // Number of cars to simulate
    int numCars = rnd.nextInt(cars.length-1)+1;
    AtomicInteger carsToExit = new AtomicInteger(numCars);

    if (segmentos < 1 || carriles < 1) {
      System.out.println
        ("\n*** Error: segmentos and carriles cannot be smaller than 1");
      System.exit(1);
    }

    System.out.println
      ("Simulation of "+numCars+" cars moving in a carretera of segmentos "
       +segmentos+" with "+carriles+" lanes");

    for (int i=0; i<numCars; i++) {
      String car = cars[i];
      int velocidad = velocidades.get(car);

      // One thread per car executes the car protocol (entrar, circulando, [avanzar, circulando]*, salir)
      Thread carTh = new Thread(car) {
          public void run() {
            Pos result = null;
            int currX = 1;

            // Do the car process
            if (!terminated.get()) {
              terminated.compareAndSet
                (false,!doResultCall(() -> { return cr.entrar(car,velocidad); }, SimCall.entrar(time,car,velocidad), currX, carriles));
            }

            if (!terminated.get()) {
              terminated.compareAndSet
                (false,!doCall(() -> { cr.circulando(car); }, SimCall.circulando(time,car)));
            }

            while (!terminated.get() && currX < segmentos) {

              if (!terminated.get()) {
                terminated.compareAndSet
                  (false,!doResultCall(() -> { return cr.avanzar(car,velocidad); }, SimCall.avanzar(time,car,velocidad), ++currX, carriles));
              }

              if (!terminated.get()) {
                terminated.compareAndSet
                  (false,!doCall(() -> { cr.circulando(car); }, SimCall.circulando(time,car)));
              }
            }

            if (!terminated.get()) {
              terminated.compareAndSet
                (false,!doCall(() -> { cr.salir(car); }, SimCall.salir(time,car)));
            }

            carsToExit.decrementAndGet();
          }
        };
      carTh.start();
    }


    // Avance time -- either manualy (step ticks) or automatically.
    // Listens to orders from the GUI to pause or quit the current simulation.
    Thread timeThread = new Thread("tick") {
        public void run() {
          do {
            Integer cmd = null;
            if (stepTicks) {
              try {
                cmd = tickQueue.take();
                terminated.compareAndSet(false,cmd != null && cmd == -10);
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            } else {
              try {
                Thread.sleep(5000);

                cmd = tickQueue.poll();
                boolean stopped = (cmd != null && cmd == -1);
                terminated.compareAndSet(false,cmd != null && cmd == -10);

                while (stopped && !terminated.get()) {
                  cmd = tickQueue.take();
                  terminated.compareAndSet(false,cmd != null && cmd == -10);
                  stopped = !(cmd != null && cmd == -1);
                }
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }

            if (!terminated.get()) {
              terminated.compareAndSet(false,!doCall(() -> { cr.tick(); ++time; }, SimCall.tick(time)));
            }
          } while (!terminated.get() && carsToExit.get() > 0);
        }
      };
    timeThread.start();
    return null;
  }

  // Send a message from the simulation to the GUI
  SimCall sendCallToGUI(SimCall call) {
    publish(new CallAndGeneration(call,generation));
    return call;
  }

  boolean doCall(Runnable callCode, SimCall oldCall) {
    sendCallToGUI(oldCall);
    SimCall call = new SimCall(oldCall);

    boolean callResult = true;

    try {
      callCode.run();
    } catch (Throwable exc) {
      call.raisedException = true;
      call.exception = exc;
      callResult = false;
    };

    if (callResult) {
      call.returnTime = time;
      call.returned();
    }

    sendCallToGUI(call);
    return callResult;
  }

  boolean doResultCall(Supplier<Pos> callCode, SimCall oldCall, int expectedSegmento, int carriles) {
    sendCallToGUI(oldCall);
    SimCall call = new SimCall(oldCall);
    boolean callResult = true;
    Pos pos = null;

    try {
      pos = callCode.get();
    } catch (Throwable exc) {
      call.raisedException = true;
      call.exception = exc;
      callResult = false;
    };

    if (callResult) {
      call.returned(pos);
      call.returnTime = time;
      callResult = checkCall(call, expectedSegmento, carriles);
    }

    sendCallToGUI(call);
    return callResult;
  }

  private boolean checkCall(SimCall call, int expectedSegmento, int carriles) {
    Pos result = call.result;

    if (result == null) {
      call.failed = true;
      call.failMessage =
        "The call to "+call.getCallString()+" returned a NULL value";
      return false;
    } else if (result.getSegmento() != expectedSegmento) {
      call.failed = true;
      call.failMessage =
        "The call to "+call.getCallString()+" returned a segmento "+
        result.getSegmento()+" != expected value "+expectedSegmento;
      return false;
    } else  if (result.getCarril() < 1 || result.getCarril() > carriles) {
      call.failed = true;
      call.failMessage =
        "The call to "+call.getCallString()+" returned a carril "+
        result.getCarril()+" < 1 or > the number of carriles = "+carriles;
      return false;
    } else return true;
  }
}

// A simulation event sent to the GUI which includes the generation --
// to discard "old" events.
class CallAndGeneration {
  SimCall call;
  Integer generation;

  CallAndGeneration(SimCall call, int generation) {
    this.call = call;
    this.generation = generation;
  }
}


// A simulation event sent to the GUI
class SimCall {
  int time;
  int returnTime = -1;
  String name;
  String car=null;
  Integer velocidad=null;
  boolean returned;
  Pos result=null;
  boolean failed=false;
  String failMessage=null;
  boolean raisedException=false;
  Throwable exception;

  SimCall(int time, String name) { this.time = time; this.name = name; this.returned = false; }

  SimCall(SimCall call) {
    this.time = call.time;
    this.returnTime = call.returnTime;
    this.name = call.name;
    this.car = call.car;
    this.velocidad = call.velocidad;
    this.returned = call.returned;
    this.result = call.result;
    this.failed = call.failed;
    this.failMessage = call.failMessage;
    this.raisedException = call.raisedException;
    this.exception = call.exception;
  }

  static SimCall entrar(int time, String car, int velocidad) {
    SimCall call = new SimCall(time,"entrar"); call.car = car; call.velocidad = velocidad; return call;
  }

  static SimCall avanzar(int time, String car, int velocidad) {
    SimCall call = new SimCall(time,"avanzar"); call.car = car; call.velocidad = velocidad; return call;
  }

  static SimCall salir(int time,String car) {
    SimCall call = new SimCall(time,"salir"); call.car = car; return call;
  }

  static SimCall circulando(int time,String car) {
    SimCall call = new SimCall(time,"circulando"); call.car = car; return call;
  }

  static SimCall tick(int time) {
    SimCall call = new SimCall(time,"tick"); return call;
  }

  public void returned() {
    this.returned = true;
  }

  public void returned(Pos result) {
    this.returned = true;
    this.result = result;
  }

  public String getCallString() {
    String str = name+"(";
    if (car != null) str +=car;
    if (velocidad != null) str +=","+velocidad;
    str += ")";
    return str;
  }

  public String toString() {
    String str = getCallString();
    if (returned) {
      str += " returned";
      if (result != null) str += " "+result;
    }
    return str;
  }
}
