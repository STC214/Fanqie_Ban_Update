package local.usbcontrol;

public class LogicTest {
    private static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(Logic.installNamespace("", "", "")==null,"unsupported ROM disabled");
        check("global".equals(Logic.installNamespace("adb_install_enabled=0\n", "", "")),"existing global key");
        check("secure".equals(Logic.installNamespace("", "adb_install_enabled=1", "")),"existing secure key");
        check("system".equals(Logic.installNamespace("", "", "adb_install_enabled=1")),"existing system key");
        check(Logic.installNamespace("adb_install_enabled=null", "", "")==null,"invalid not supported");
        check(Logic.installNamespace("package_verifier_enable=0", "", "")==null,"never confuse installation verification");
        check(Logic.installNamespace("adb_install_enabled=1", "adb_install_enabled=0", "")==null,"ambiguity disabled");
        check(Logic.installNamespace("other_adb_install_enabled=1", "", "")==null,"no fuzzy matching");
        check(Logic.lastNumber("Physical density: 420\nOverride density: 360","(?:Physical|Override) density",0)==360,"override density");
        check(Logic.lastNumber("Physical density: 420","Override density",0)==0,"default reset");
        int[] size=Logic.size("Physical size: 1080x2400\nOverride size: 900x2000");
        check(size[0]==900&&size[1]==2000,"overridden resolution");
        check(Logic.densityForWidth(1080,360)==480,"dp to density");
        check(Logic.densityForWidth(1080,480)==360,"higher dp smaller UI");
        boolean rejected=false;try{Logic.densityForWidth(1080,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject zero");
        rejected=false;try{Logic.densityForWidth(1080,900);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject extreme dp");
        float s=Logic.coverScale(1600,900,400,800,1);
        check(s==800f/900,"uniform portrait cover scale");
        check(Math.abs((1600*s)/(900*s)-1600f/900)<.0001f,"aspect ratio preserved");
        check(Logic.coverScale(900,1600,400,800,4)==2f,"zoom uniformly");
        check(Logic.clamp(-.3f,0,1)==0&&Logic.clamp(2,0,1)==1,"crop edges clamped");
        System.out.println("PASS: "+checks+" capability, density, crop and aspect-ratio assertions");
    }
}
