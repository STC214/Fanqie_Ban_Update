package local.usbcontrol;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

final class SafeFiles {
    static void write(File file,byte[] data)throws IOException {
        File temp=File.createTempFile("write-", ".tmp",file.getParentFile());
        try {
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);out.getFD().sync();}
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{temp.delete();}
    }
    static void properties(File file,Properties p)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();p.store(out,"USB Control transaction");write(file,out.toByteArray());
    }
    static Properties read(File file)throws IOException {
        Properties p=new Properties();try(InputStream in=new FileInputStream(file)){p.load(in);}return p;
    }
}
