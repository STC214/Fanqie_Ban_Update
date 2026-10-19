package local.usbcontrol;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TransactionTest {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static File home()throws Exception{return Files.createTempDirectory("usb-density-model-").toFile();}
    static void reset(){Root.density="reset";Root.failWrite=false;Root.failRead=false;Root.failApply=false;}
    static void remove(File home)throws Exception{try(java.util.stream.Stream<Path> s=Files.walk(home.toPath())){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}}
    public static void main(String[] args)throws Exception {
        for(int pass=0;pass<20;pass++){
            reset();File h=home();
            try{
                DensitySession a=DensitySession.begin(h,"480",100);
                check(Root.density.equals("480"),"preview applied");check(new File(h,"density-active").exists(),"journal persisted");
                check(a.confirm(),"atomic confirmation wins");check(!new File(h,"density-active").exists(),"confirmed journal cleared");
                DensitySession b=DensitySession.begin(h,"360",200);check(!a.dir.equals(b.dir),"per-session paths never reused");
                b.rollback();check(Root.density.equals("480"),"rollback restores earlier override, not physical default");
                DensitySession c=DensitySession.begin(h,"400",300);c.file("pending").renameTo(c.file("expired"));Root.density="480";
                check(!c.confirm(),"watchdog wins confirmation race");DensitySession.recover(h);check(Root.density.equals("480"),"expiry recovered");
                DensitySession d=DensitySession.begin(h,"360",400);Root.failWrite=true;
                boolean failed=false;try{d.rollback();}catch(IOException e){failed=true;}
                check(failed,"injected restore failure surfaced");check(new File(h,"density-active").exists(),"failed recovery retains journal");check(d.file("pending").exists(),"failed foreground rollback leaves watchdog armed");
                Root.failWrite=false;DensitySession.recover(h);check(Root.density.equals("480"),"retry recovers original override");
                Root.failApply=true;failed=false;try{DensitySession.begin(h,"360",500);}catch(IOException e){failed=true;}
                check(failed,"watchdog launch failure surfaces");check(Root.density.equals("480"),"watchdog launch failure never leaves preview applied");check(!new File(h,"density-active").exists(),"failed begin verified rollback clears journal");Root.failApply=false;
                DensitySession e=DensitySession.begin(h,"reset",600);check(Root.density.equals("reset"),"system default preview");e.rollback();check(Root.density.equals("480"),"default preview rollback preserves prior override");
                check(!DensitySession.validDensity("480;reboot"),"density injection rejected");check(!DensitySession.validDensity("0"),"zero rejected");
                check(Logic.clamp(Float.NaN,0,1)==0,"NaN crop preference bounded");
                Properties bad=new Properties();bad.setProperty("token","../../bad");bad.setProperty("previous","480");bad.setProperty("target","360");SafeFiles.properties(new File(h,"density-active"),bad);
                failed=false;try{DensitySession.recover(h);}catch(IOException x){failed=true;}check(failed,"invalid journal path rejected");
            }finally{reset();remove(h);}
        }
        System.out.println("PASS: "+checks+" transaction assertions across 20 repeated model cycles");
    }
}
