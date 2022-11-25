import fasttools.jtools.common.FtException;
import fasttools.jtools.gin.api.DistributedItemId;
import fasttools.jtools.gin.api.Gin;
import fasttools.jtools.gin.msg.GinMsgDefs;
import fasttools.processfast.Attribute;
import fasttools.processfast.ProcessClass;
import fasttools.processfast.Signal;
import fasttools.processfast.Timer;

import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.joining;

/**
 * UDP_TEST
 * Ryan Jameson
 * For Hatena http://akemi2.jp.ykgw.net/1669255498
 */
public class UDP_TEST extends ProcessClass {
  private static final Logger cLog = Logger.getLogger(UDP_TEST.class.getName());
  private static final Charset MESSAGE_ENCODING = StandardCharsets.ISO_8859_1; // or StandardCharsets.US_ASCII
  private static final double UPDATE_RATE = 5; // in seconds
  private static final String SOURCE_IP = "192.168.1.80"; // use specific source address
  //private static final String SOURCE_IP = "*"; // system will pick a local source address
  private static final int SOURCE_PORT = 8091;
  private static final String DEST_IP = "192.168.1.80"; // only the same as SOURCE_IP for running the UdpServer on the same machine
  private static final int DEST_PORT = 8090;
  public static final String MESSAGE_PREFIX = "SendBegin:";
  public static final String MESSAGE_SUFFIX = "End;";

  public Attribute aEnabled = defineStringAttributeForPrompt("Enable item (optional)");
  public Attribute aLog = defineIntegerAttributeForPrompt("Log to UMH: 0=no, 1=yes");
  public Signal iEnabled = defineIntegerSignal(Signal.Trigger.VALUE);
  public Timer timer = defineTimer();

  private List<Binding> bindings;

  @Override
  public void create() {
    super.create();
    String mEnabledItem = aEnabled.getValueAsString();
    if (!mEnabledItem.isEmpty()) {
      attachItemToSignal(iEnabled, mEnabledItem);
    }
  }

  @Override
  public void start() {
    super.start();

    //SendBegin:Time,0;Speed,0;Torque,0.000;PowerM,0.000;Freq,0.0;Voltage1,0.00;Voltage2,0.00;Voltage3,0.00;VoltageZ,0.00;Current1,0.000;Current2,0.000;Current3,0.000;CurrentZ,0.000;PowerZ,0.000;End;
    bindings = new ArrayList<>();
    bindings.add(Binding.createSupplier("Time", 0, this::getTimeInSeconds));
    bindings.add(Binding.createFt("Speed", 0, "SYSTEM.TEST.Tag1"));
    bindings.add(Binding.createFt("Torque", 3, "SYSTEM.TEST.Tag2"));
    bindings.add(Binding.createFt("PowerM", 3, "SYSTEM.TEST.Tag3"));
    bindings.add(Binding.createFt("Freq", 2, "SYSTEM.TEST.Tag4"));
    //... add more bindings as required

    if (isEnabled()) {
      enable();
    }
  }

  @Override
  public void stop() {
    try {
      disable();
    } finally {
      super.stop();
    }
  }

  public boolean isEnabled() {
    return !iEnabled.isAttached() || (iEnabled.getValueAsInteger() != 0);
  }

  public boolean isLog() {
    return aLog.getValueAsInteger() != 0;
  }

  public void enable() {
    cLog.log(Level.INFO, "Starting scanning");
    if (isLog()) systemMessage("Starting scanning");

    // start scanning timer
    timer.setTime(UPDATE_RATE);
    timer.setRepeat(true);
    timer.start();
  }

  public void disable() {
    cLog.log(Level.INFO, "Stopping scanning");
    if (isLog()) systemMessage("Stopping scanning");
    // stop scanning timer
    timer.cancel();


  }

  @Override
  public void signalEvent(Signal signal) {
    super.signalEvent(signal);
    if (signal == iEnabled) {
      boolean mEnabled = (iEnabled.getValueAsInteger() != 0);
      if (mEnabled) {
        enable();
      } else {
        disable();
      }
    }
  }

  @Override
  public void timerEvent(Timer timer) {
    // just in case
    if (!isEnabled()) {
      return;
    }

    // don't stall the timer thread, run async
    CompletableFuture.runAsync(this::scan);
  }

  public void scan() {
    try {
      // update the values of the bindings
      bindings.forEach(Binding::refresh);

      // create a message string (convert each pair to string and join together)
      String message = bindings.stream()
       .map(i -> i.pair.toString())
       .collect(joining("", MESSAGE_PREFIX, MESSAGE_SUFFIX));

      // send string to UDP server
      sendUdpString(message);

    } catch (Throwable t) {
      cLog.log(Level.WARNING, "Error while scanning", t);
    }
  }

  public static void sendUdpString(String message) {
    try {

      // build data packet
      byte[] payload = message.getBytes(MESSAGE_ENCODING);
      DatagramPacket packet = new DatagramPacket(payload, 0, payload.length, new InetSocketAddress(DEST_IP, DEST_PORT));

      // send to destination
      try (DatagramSocket socket = SOURCE_IP.equals("*")
       ? new DatagramSocket()
       : new DatagramSocket(new InetSocketAddress(SOURCE_IP, SOURCE_PORT))) {
        socket.send(packet);
      }

    } catch (Throwable t) {
      cLog.log(Level.WARNING, "Error while sending UDP packet", t);
    }
  }

  private double getTimeInSeconds() {
    return (double) (System.currentTimeMillis() / 1000);
  }

  public abstract static class Binding {
    public final TagValuePair pair;

    protected Binding(TagValuePair pair) {
      this.pair = Objects.requireNonNull(pair, "pair");
    }

    public abstract double getValue();

    public void refresh() {
      pair.value = getValue();
    }

    public static Binding createFt(String messageTag, int decimalPlaces, String ftItemName) {
      return new FtBinding(new TagValuePair(messageTag, decimalPlaces), ftItemName);
    }

    public static Binding createSupplier(String messageTag, int decimalPlaces, DoubleSupplier supplier) {
      return new SupplierBinding(new TagValuePair(messageTag, decimalPlaces), supplier);
    }
  }

  public final static class SupplierBinding extends Binding {
    public final DoubleSupplier supplier;

    protected SupplierBinding(TagValuePair pair, DoubleSupplier supplier) {
      super(pair);
      this.supplier = Objects.requireNonNull(supplier, "supplier");

    }

    @Override
    public double getValue() {
      return supplier.getAsDouble();
    }
  }

  public final static class FtBinding extends Binding {
    public final String ftItemName;
    public final DistributedItemId id;

    public FtBinding(TagValuePair pair, String ftItemName) {
      super(pair);
      Objects.requireNonNull(ftItemName);
      if (ftItemName.isEmpty()) throw new IllegalArgumentException("ftItemName must not be empty");
      this.ftItemName = ftItemName;
      try {
        this.id = DistributedItemId.getItemId(ftItemName);
      } catch (FtException e) {
        throw new RuntimeException(String.format("Could not find FAST/TOOLS item [%s] for binding [%s]", ftItemName, pair.tagName), e);
      }
    }

    @Override
    public double getValue() {
      try {
        return Gin.getAttribute(id, GinMsgDefs.GIN_DiiValue | Gin.REP_DOUBLE).getValueAsDouble();
      } catch (FtException e) {
        cLog.log(Level.WARNING, String.format("Error while getting value of item [%s]", ftItemName), e);
        return 0;
      }
    }
  }

  public final static class TagValuePair {
    public final String tagName;
    public final int decimalPlaces;
    public double value;

    public TagValuePair(String tagName) {
      this(tagName, 3);
    }

    public TagValuePair(String tagName, int decimalPlaces) {
      this(tagName, decimalPlaces, 0d);
    }

    public TagValuePair(String tagName, int decimalPlaces, double value) {
      Objects.requireNonNull(tagName, "tagName");
      if (tagName.isEmpty()) throw new IllegalArgumentException("tagName must not be empty");
      this.tagName = tagName;
      this.decimalPlaces = decimalPlaces;
      this.value = value;
    }

    @Override
    public String toString() {
      return String.format("%s,%." + decimalPlaces + "f;", tagName, value);
    }
  }

}



