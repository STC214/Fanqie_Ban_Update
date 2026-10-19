package local.usbcontrol;
import java.io.IOException;
import java.util.regex.*;
/** JVM test double only; never in production src/. */
final class Root {
    static String density="reset";
    static boolean failWrite,failRead,failApply;
    static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    static String run(String command)throws Exception {
        if(command.equals("wm density")){
            if(failRead)throw new IOException("injected read failure");
            return "Physical density: 420"+(density.equals("reset")?"":"\nOverride density: "+density);
        }
        if(command.contains("nohup")&&failApply)throw new IOException("injected watchdog launch failure");
        Matcher m=Pattern.compile("wm density (reset|[0-9]+)").matcher(command);
        while(m.find()){if(failWrite)throw new IOException("injected write failure");density=m.group(1);}
        return "";
    }
}
