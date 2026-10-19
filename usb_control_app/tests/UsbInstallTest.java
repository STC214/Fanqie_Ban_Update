package local.usbcontrol;
public final class UsbInstallTest {
 static int checks;
 static void check(boolean yes,String name){checks++;if(!yes)throw new AssertionError(name);}
 public static void main(String[] args){
  String standard="__GLOBAL__\nadb_install_enabled=1\n__SECURE__\nadb_install_enabled=null\n__SYSTEM__\nadb_install_enabled=null";
  check("global".equals(UsbInstall.backend(standard)),"standard backend retained");
  check("1".equals(UsbInstall.state(standard,"global")),"standard on state");
  check("0".equals(UsbInstall.state(standard.replace("enabled=1","enabled=0"),"global")),"standard off state");
  check(UsbInstall.backend(standard.replace("enabled=1","enabled=null"))==null,"unsupported disabled");
  String miui="\n__MIUI__\nResult: Bundle[{CONTAIN_KEY=true}]\nResult: Bundle[{security_adb_install_enable=false}]\ncom.miui.securitycenter/com.miui.permcenter.install.AdbInstallVerifyActivity";
  check("miui".equals(UsbInstall.backend(standard+miui)),"vendor API preferred over stale standard key");
  check("0".equals(UsbInstall.state(standard+miui,"miui")),"vendor actual off");
  check("1".equals(UsbInstall.state(standard+miui.replace("enable=false","enable=true"),"miui")),"vendor actual on");
  check(UsbInstall.state(standard,"miui")==null,"no stale vendor state");
  check(UsbInstall.state(standard,"invalid")==null,"invalid backend");
  check(UsbInstall.writeCommand("global",true).equals("settings put global adb_install_enabled 1"),"standard enable");
  check(UsbInstall.writeCommand("secure",false).equals("settings put secure adb_install_enabled 0"),"standard disable");
  String off=UsbInstall.writeCommand("miui",false),on=UsbInstall.writeCommand("miui",true);
  check(off.contains("--arg SET")&&off.contains("--extra value:b:false"),"vendor uses supported disable API");
  check(on.contains("am start")&&on.contains("AdbInstallVerifyActivity"),"enable routes to official activity");
  check(UsbInstall.toggleCommand("miui",false).contains("CONTAIN_KEY"),"fast toggle checks key before writing");
  check(UsbInstall.toggleCommand("global",true).contains("case \"$old\" in 0|1)"),"fast standard path rejects missing keys");
  check(UsbInstall.toggleCommand("miui",true).contains("AdbInstallVerifyActivity")&&!UsbInstall.toggleCommand("miui",true).contains("value:b:true"),"fast enable preserves official validation");
  check(!on.contains("--arg SET")&&!on.contains("setprop")&&!on.contains("sed "),"no forced enable or XML edit");
  check(UsbInstall.restoreCommand("miui","1").contains("AdbInstallVerifyActivity"),"enabled baseline keeps official confirmation");
  check(UsbInstall.restoreCommand("miui","0").contains("--extra value:b:false"),"disabled baseline restore");
  check(UsbInstall.verifyCommand("miui","1").contains("security_adb_install_enable=true"),"vendor actual readback");
  boolean rejected=false;try{UsbInstall.writeCommand("global; bad",true);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"backend command injection rejected");
  rejected=false;try{UsbInstall.restoreCommand("miui","true");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"baseline malformed value rejected");
  String script=RecoveryScript.make("true","420","1","1","miui","1");
  check(script.contains("security_adb_install_enable=true")&&!script.contains("settings get miui"),"recovery uses provider verification");
  System.out.println("PASS: "+checks+" USB-install adapter and recovery assertions");
 }
}
